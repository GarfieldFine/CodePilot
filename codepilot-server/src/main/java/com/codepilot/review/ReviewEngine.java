package com.codepilot.review;

import com.codepilot.ai.AiProvider;
import com.codepilot.ai.AiProviderFactory;
import com.codepilot.ai.model.AiReviewRequest;
import com.codepilot.ai.model.ReviewSuggestion;
import com.codepilot.ai.model.RiskFinding;
import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import com.codepilot.model.enums.AnalysisStatus;
import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.RuleEngine;
import com.codepilot.rule.RuleResult;
import com.codepilot.scorer.RiskScore;
import com.codepilot.scorer.RiskScoreCalculator;
import com.codepilot.sse.SseEmitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class ReviewEngine {

    private final AiProviderFactory aiProviderFactory;
    private final RuleEngine ruleEngine;
    private final RiskScoreCalculator scoreCalculator;
    private final DiffContextBuilder contextBuilder;
    private final SseEmitterService sseService;
    private final PrSplitter prSplitter;

    public ReviewEngine(AiProviderFactory aiProviderFactory,
                        RuleEngine ruleEngine,
                        RiskScoreCalculator scoreCalculator,
                        DiffContextBuilder contextBuilder,
                        SseEmitterService sseService,
                        PrSplitter prSplitter) {
        this.aiProviderFactory = aiProviderFactory;
        this.ruleEngine = ruleEngine;
        this.scoreCalculator = scoreCalculator;
        this.contextBuilder = contextBuilder;
        this.sseService = sseService;
        this.prSplitter = prSplitter;
    }

    public Mono<AnalysisResult> analyze(PrInfo prInfo, String providerName) {
        String analysisId = UUID.randomUUID().toString();
        sseService.createEmitter(analysisId);

        AiProvider aiProvider = aiProviderFactory.getProvider(providerName);
        log.info("Starting analysis {} with provider: {}", analysisId, aiProvider.getProviderName());

        return Mono.fromCallable(() -> {
            // Step 1: Rule Engine (static analysis)
            sseService.emit(analysisId, "ANALYZING_DIFF", "正在运行静态规则引擎...");
            List<RuleResult> ruleResults = ruleEngine.analyze(prInfo);
            sseService.emit(analysisId, "RUNNING_RULES_COMPLETE",
                    String.format("规则引擎完成，发现 %d 个问题", ruleResults.size()));

            // Step 2: AI Review per file chunk
            sseService.emit(analysisId, "AI_REVIEWING", "AI 正在分析代码变更...");
            List<PRAnalysisChunk> chunks = prSplitter.split(prInfo);
            List<String> aiSummaries = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                int idx = i + 1;
                sseService.emit(analysisId, "AI_REVIEWING",
                        String.format("AI 正在分析第 %d/%d 部分...", idx, chunks.size()));

                PRAnalysisChunk chunk = chunks.get(i);
                Map<String, String> fileContexts = contextBuilder.buildContext(
                        chunk.files(), Collections.emptyMap());

                AiReviewRequest request = AiReviewRequest.builder()
                        .diff(chunk.diff())
                        .contextCode(String.join("\n\n", fileContexts.values()))
                        .commitMessages(formatCommits(prInfo))
                        .fileLanguage(chunk.files().isEmpty() ? "Java" : chunk.files().get(0).getLanguage())
                        .riskRules(ruleResults.stream().map(r -> r.getRuleName() + ": " + r.getMessage()).toList())
                        .build();

                try {
                    String aiResponse = aiProvider.chat(
                            contextBuilder.buildSystemPrompt(),
                            buildUserPrompt(request, prInfo));
                    aiSummaries.add(aiResponse);
                } catch (Exception e) {
                    log.error("AI review failed for chunk {}: {}", idx, e.getMessage());
                    aiSummaries.add("[AI Analysis failed for this chunk: " + e.getMessage() + "]");
                }
            }

            // Step 3: Compute risk score
            sseService.emit(analysisId, "CALCULATING_SCORE", "正在计算综合风险评分...");
            String combinedAiOutput = String.join("\n", aiSummaries);
            RiskScore riskScore = scoreCalculator.calculate(prInfo, ruleResults, combinedAiOutput);

            // Step 4: Build analysis result
            AnalysisResult result = AnalysisResult.builder()
                    .analysisId(analysisId)
                    .prTitle(prInfo.getTitle())
                    .prUrl(prInfo.getHtmlUrl())
                    .prNumber(prInfo.getNumber())
                    .owner(prInfo.getOwner())
                    .repo(prInfo.getRepo())
                    .author(prInfo.getAuthor())
                    .changedFiles(prInfo.getChangedFiles())
                    .additions(prInfo.getAdditions())
                    .deletions(prInfo.getDeletions())
                    .ruleResults(ruleResults)
                    .riskScore(riskScore)
                    .aiRawOutput(combinedAiOutput)
                    .status(AnalysisStatus.COMPLETED)
                    .fileAnalysis(buildFileAnalysis(prInfo, ruleResults))
                    .build();

            sseService.complete(analysisId, result);
            return result;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public Flux<String> analyzeStream(PrInfo prInfo, String providerName) {
        String analysisId = UUID.randomUUID().toString();
        sseService.createEmitter(analysisId);

        AiProvider aiProvider = aiProviderFactory.getProvider(providerName);
        log.info("Starting streaming analysis {} with {}", analysisId, aiProvider.getProviderName());

        return Flux.concat(
                Flux.just("{\"type\":\"status\",\"status\":\"ANALYZING_DIFF\",\"message\":\"正在运行静态规则引擎...\"}"),
                Flux.defer(() -> {
                    List<RuleResult> ruleResults = ruleEngine.analyze(prInfo);
                    return Flux.just("{\"type\":\"status\",\"status\":\"RULES_COMPLETE\",\"message\":\"规则引擎完成，发现 " +
                            ruleResults.size() + " 个问题\",\"count\":" + ruleResults.size() + "}");
                }),
                Flux.just("{\"type\":\"status\",\"status\":\"AI_REVIEWING\",\"message\":\"AI 正在分析代码变更...\"}"),
                Flux.defer(() -> {
                    List<PRAnalysisChunk> chunks = prSplitter.split(prInfo);
                    Map<String, String> fileContexts = contextBuilder.buildContext(
                            prInfo.getFiles(), Collections.emptyMap());

                    AiReviewRequest request = AiReviewRequest.builder()
                            .diff(prInfo.getDiffContent())
                            .contextCode(String.join("\n\n", fileContexts.values()))
                            .commitMessages(formatCommits(prInfo))
                            .fileLanguage("Java")
                            .build();

                    return aiProvider.reviewStream(request)
                            .map(token -> "{\"type\":\"ai_token\",\"content\":\"" + escapeJson(token) + "\"}");
                }),
                Flux.just("{\"type\":\"status\",\"status\":\"COMPLETED\",\"message\":\"分析完成\"}")
        ).onErrorResume(e -> {
            log.error("Stream analysis error: {}", e.getMessage());
            return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        });
    }

    private String buildUserPrompt(AiReviewRequest request, PrInfo prInfo) {
        return String.format("""
                        ## Pull Request Review

                        **PR Title:** %s
                        **Changed Files:** %d (+%d -%d)

                        ### Code Diff
                        ```diff
                        %s
                        ```

                        ### Related Code Context
                        ```
                        %s
                        ```

                        ### Commit Messages
                        %s

                        Provide a thorough code review. Structure your response exactly as:
                        1. ### PR Summary
                        2. ### Risk Analysis
                        3. ### Review Suggestions
                        4. ### Overall Assessment
                        """,
                prInfo.getTitle(),
                prInfo.getChangedFiles(), prInfo.getAdditions(), prInfo.getDeletions(),
                truncate(request.getDiff(), 6000),
                truncate(request.getContextCode(), 4000),
                truncate(formatCommits(prInfo), 1500));
    }

    private String formatCommits(PrInfo prInfo) {
        if (prInfo.getCommits() == null || prInfo.getCommits().isEmpty()) return "N/A";
        StringBuilder sb = new StringBuilder();
        for (var c : prInfo.getCommits()) {
            sb.append("- ").append(c.getMessage()).append("\n");
        }
        return sb.toString();
    }

    private List<FileAnalysis> buildFileAnalysis(PrInfo prInfo, List<RuleResult> allRules) {
        List<FileAnalysis> analysis = new ArrayList<>();
        for (PrFile file : prInfo.getFiles()) {
            List<RuleResult> fileRules = allRules.stream()
                    .filter(r -> file.getFilename().equals(r.getFile()))
                    .toList();

            RiskLevel highestRisk = fileRules.stream()
                    .map(RuleResult::getRiskLevel)
                    .max(Comparator.comparingInt(RiskLevel::getLevel))
                    .orElse(RiskLevel.LOW);

            analysis.add(FileAnalysis.builder()
                    .filename(file.getFilename())
                    .language(file.getLanguage())
                    .status(file.getStatus())
                    .additions(file.getAdditions())
                    .deletions(file.getDeletions())
                    .riskLevel(highestRisk)
                    .findings(fileRules)
                    .build());
        }
        return analysis;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
