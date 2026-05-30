package com.codepilot.review;

import com.codepilot.agent.AgentPipeline;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentExecutor;
import com.codepilot.agent.AgentPipeline.PipelineStage;
import com.codepilot.agent.AgentPipeline.PipelineStage.ExecutionMode;
import com.codepilot.agent.agents.*;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.AiProviderFactory;
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

import java.util.*;

/**
 * Review engine orchestrating the multi-agent pipeline.
 *
 * Stage 1 (SEQUENTIAL): RepositoryAnalyze → DiffAnalyze → ContextBuild
 * Stage 2 (PARALLEL):    RiskDetect
 * Stage 3 (SEQUENTIAL):  ReviewGenerate
 * Stage 4 (FINAL):       SummaryMerge
 */
@Slf4j
@Component
public class ReviewEngine {

    private final AiProviderFactory aiProviderFactory;
    private final RuleEngine ruleEngine;
    private final RiskScoreCalculator scoreCalculator;
    private final DiffContextBuilder contextBuilder;
    private final SseEmitterService sseService;
    private final PrSplitter prSplitter;

    // Agent layer
    private final AgentPipeline agentPipeline;
    private final RepositoryAnalyzeAgent repositoryAnalyzeAgent;
    private final DiffAnalyzeAgent diffAnalyzeAgent;
    private final ContextBuildAgent contextBuildAgent;
    private final RiskDetectAgent riskDetectAgent;
    private final ReviewGenerateAgent reviewGenerateAgent;
    private final SummaryMergeAgent summaryMergeAgent;

    public ReviewEngine(AiProviderFactory aiProviderFactory,
                        RuleEngine ruleEngine,
                        RiskScoreCalculator scoreCalculator,
                        DiffContextBuilder contextBuilder,
                        SseEmitterService sseService,
                        PrSplitter prSplitter,
                        AgentPipeline agentPipeline,
                        RepositoryAnalyzeAgent repositoryAnalyzeAgent,
                        DiffAnalyzeAgent diffAnalyzeAgent,
                        ContextBuildAgent contextBuildAgent,
                        RiskDetectAgent riskDetectAgent,
                        ReviewGenerateAgent reviewGenerateAgent,
                        SummaryMergeAgent summaryMergeAgent) {
        this.aiProviderFactory = aiProviderFactory;
        this.ruleEngine = ruleEngine;
        this.scoreCalculator = scoreCalculator;
        this.contextBuilder = contextBuilder;
        this.sseService = sseService;
        this.prSplitter = prSplitter;
        this.agentPipeline = agentPipeline;
        this.repositoryAnalyzeAgent = repositoryAnalyzeAgent;
        this.diffAnalyzeAgent = diffAnalyzeAgent;
        this.contextBuildAgent = contextBuildAgent;
        this.riskDetectAgent = riskDetectAgent;
        this.reviewGenerateAgent = reviewGenerateAgent;
        this.summaryMergeAgent = summaryMergeAgent;
    }

    /**
     * Execute full PR analysis using the multi-agent pipeline.
     * Maintains backward compatibility with existing PrAnalysisService.
     */
    public Mono<AnalysisResult> analyze(PrInfo prInfo, String providerName) {
        AiProvider aiProvider = aiProviderFactory.getProvider(providerName);
        AgentContext agentContext = AgentContext.from(prInfo, providerName);
        sseService.createEmitter(agentContext.getAnalysisId());

        log.info("Starting agent-pipeline analysis {} with provider: {}",
                agentContext.getAnalysisId(), aiProvider.getProviderName());

        return Mono.fromCallable(() -> {
            // Build pipeline stages
            List<PipelineStage> stages = List.of(
                    new PipelineStage("Understanding Repository",
                            List.of(repositoryAnalyzeAgent, diffAnalyzeAgent, contextBuildAgent),
                            ExecutionMode.SEQUENTIAL),
                    new PipelineStage("Detecting Risks",
                            List.of(riskDetectAgent),
                            ExecutionMode.SEQUENTIAL),
                    new PipelineStage("Generating AI Review",
                            List.of(reviewGenerateAgent),
                            ExecutionMode.SEQUENTIAL),
                    new PipelineStage("Merging Analysis",
                            List.of(summaryMergeAgent),
                            ExecutionMode.SEQUENTIAL)
            );

            // Execute pipeline with SSE event relay
            agentPipeline.execute(stages, agentContext, event -> {
                // Map agent events to legacy SSE format for backward compatibility
                relayToSse(agentContext.getAnalysisId(), event);
            });

            AnalysisResult result = agentContext.getFinalResult();
            if (result != null) {
                sseService.complete(agentContext.getAnalysisId(), result);
                return result;
            }

            // Fallback: build result manually (should not normally happen)
            return buildResultLegacy(prInfo, agentContext);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Execute streaming PR analysis using the multi-agent pipeline.
     * Emits agent lifecycle events + AI tokens as SSE stream.
     */
    public Flux<String> analyzeStream(PrInfo prInfo, String providerName) {
        AgentContext agentContext = AgentContext.from(prInfo, providerName);
        sseService.createEmitter(agentContext.getAnalysisId());

        AiProvider aiProvider = aiProviderFactory.getProvider(providerName);
        log.info("Starting agent-pipeline stream analysis {} with {}",
                agentContext.getAnalysisId(), aiProvider.getProviderName());

        List<PipelineStage> stages = List.of(
                new PipelineStage("Understanding Repository",
                        List.of(repositoryAnalyzeAgent, diffAnalyzeAgent, contextBuildAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Detecting Risks",
                        List.of(riskDetectAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Generating AI Review",
                        List.of(reviewGenerateAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Merging Analysis",
                        List.of(summaryMergeAgent),
                        ExecutionMode.SEQUENTIAL)
        );

        // Stream agent pipeline events
        Flux<String> agentEvents = agentPipeline.executeStream(stages, agentContext);

        // After agents complete, build final result and emit complete event
        Flux<String> completionEvents = Flux.defer(() -> {
            AnalysisResult result = agentContext.getFinalResult();
            if (result != null) {
                return Flux.just(jsonComplete(result));
            }
            // Fallback
            AnalysisResult fallback = buildResultLegacy(prInfo, agentContext);
            return Flux.just(jsonComplete(fallback));
        });

        return Flux.concat(agentEvents, completionEvents)
                .onErrorResume(e -> {
                    log.error("Agent pipeline stream error: {}", e.getMessage());
                    return Flux.just(jsonError(e.getMessage()));
                });
    }

    /**
     * Relays agent SSE events to the legacy SseEmitterService for backward compatibility.
     */
    private void relayToSse(String analysisId, AgentExecutor.SseAgentEvent event) {
        switch (event.type()) {
            case "agent_start" -> sseService.emit(analysisId, "AI_REVIEWING",
                    "[" + event.agentName() + "] " + event.message());
            case "agent_complete" -> sseService.emit(analysisId, "AI_REVIEWING",
                    "[OK] " + event.agentName() + " — " + event.message());
            case "agent_error" -> sseService.emit(analysisId, "AI_REVIEWING",
                    "[ERROR] " + event.agentName() + " — " + event.message());
            case "pipeline_stage" -> sseService.emit(analysisId, "BUILDING_CONTEXT",
                    event.agentName() + (event.message() != null ? ": " + event.message() : ""));
        }
    }

    /**
     * Legacy fallback for building AnalysisResult when agent pipeline doesn't produce one.
     */
    private AnalysisResult buildResultLegacy(PrInfo prInfo, AgentContext agentContext) {
        List<RuleResult> ruleResults = agentContext.getRuleResults() != null
                ? agentContext.getRuleResults() : ruleEngine.analyze(prInfo);

        String aiOutput = agentContext.getChunkReviews() != null
                ? String.join("\n", agentContext.getChunkReviews()) : "";

        RiskScore riskScore = agentContext.getRiskScore() != null
                ? agentContext.getRiskScore()
                : scoreCalculator.calculate(prInfo, ruleResults, aiOutput);

        return AnalysisResult.builder()
                .analysisId(agentContext.getAnalysisId())
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
                .aiRawOutput(aiOutput)
                .status(AnalysisStatus.COMPLETED)
                .fileAnalysis(buildFileAnalysis(prInfo, ruleResults))
                .build();
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

    // --- JSON SSE helpers ---

    private String jsonComplete(AnalysisResult result) {
        String dataJson = cn.hutool.json.JSONUtil.toJsonStr(result);
        return "data:{\"type\":\"complete\",\"data\":" + dataJson + "}\n\n";
    }

    private String jsonError(String message) {
        return "data:{\"type\":\"error\",\"message\":\"" + escapeJson(message) + "\"}\n\n";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
