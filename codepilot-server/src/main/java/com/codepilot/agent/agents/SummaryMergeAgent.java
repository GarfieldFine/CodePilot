package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import com.codepilot.model.enums.AnalysisStatus;
import com.codepilot.model.enums.RiskLevel;
import com.codepilot.review.AnalysisResult;
import com.codepilot.review.FileAnalysis;
import com.codepilot.rule.RuleResult;
import com.codepilot.confidence.ConfidenceCalculator;
import com.codepilot.confidence.ConfidenceCalculator.ConfidenceInput;
import com.codepilot.confidence.ConfidenceCalculator.ConfidenceResult;
import com.codepilot.scorer.RiskScore;
import com.codepilot.scorer.RiskScoreCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Merges all agent outputs into the final AnalysisResult.
 *
 * Responsibilities:
 * - Merge chunk reviews into a cohesive report
 * - Compute final risk score combining rule + semantic + AI findings
 * - Calculate AI confidence score
 * - Build per-file analysis summaries
 */
@Slf4j
@Component
public class SummaryMergeAgent implements Agent {

    private final RiskScoreCalculator scoreCalculator;
    private final ConfidenceCalculator confidenceCalculator;

    public SummaryMergeAgent(RiskScoreCalculator scoreCalculator,
                             ConfidenceCalculator confidenceCalculator) {
        this.scoreCalculator = scoreCalculator;
        this.confidenceCalculator = confidenceCalculator;
    }

    @Override
    public String getName() { return "SummaryMergeAgent"; }

    @Override
    public String getDescription() { return "Merging all analyses and computing final assessment"; }

    @Override
    public int priority() { return 99; }

    @Override
    public AgentResult execute(AgentContext context) {
        // 1. Merge chunk reviews with dedup
        List<String> chunkReviews = context.getChunkReviews();
        MergeResult merged = mergeChunkReviews(chunkReviews);

        // 2. Compute risk score
        RiskScore riskScore = scoreCalculator.calculate(
                context.getPrInfo(),
                context.getRuleResults(),
                merged.combined());
        context.setRiskScore(riskScore);

        // 3. Calculate AI confidence (enhanced with chunk metadata)
        Map<String, Object> confidence = calculateConfidence(context, riskScore);

        // 4. Build per-file analysis
        List<FileAnalysis> fileAnalysis = buildFileAnalysis(
                context.getPrInfo(), context.getRuleResults());

        // 5. Build final AnalysisResult
        Map<String, Object> repoProfile = Map.of(
                "projectType", context.getProjectType() != null ? context.getProjectType() : "Unknown",
                "languages", context.getLanguages(),
                "frameworks", context.getFrameworks()
        );

        AnalysisResult result = AnalysisResult.builder()
                .analysisId(context.getAnalysisId())
                .prTitle(context.getPrInfo().getTitle())
                .prUrl(context.getPrInfo().getHtmlUrl())
                .prNumber(context.getPrInfo().getNumber())
                .owner(context.getPrInfo().getOwner())
                .repo(context.getPrInfo().getRepo())
                .author(context.getPrInfo().getAuthor())
                .changedFiles(context.getPrInfo().getChangedFiles())
                .additions(context.getPrInfo().getAdditions())
                .deletions(context.getPrInfo().getDeletions())
                .ruleResults(context.getRuleResults())
                .riskScore(riskScore)
                .aiRawOutput(merged.combined())
                .status(AnalysisStatus.COMPLETED)
                .fileAnalysis(fileAnalysis)
                .repositoryProfile(repoProfile)
                .confidenceScores(confidence)
                .agentTimeline(List.of(
                        "RepositoryAnalyzeAgent: " + context.getProjectType() + " project detected",
                        "DiffAnalyzeAgent: " + fileAnalysis.size() + " files analyzed, "
                                + merged.chunkCount() + " chunks created",
                        "ContextBuildAgent: Context built for " + String.join(", ", context.getLanguages()),
                        "RiskDetectAgent: " + context.getRuleResults().size() + " findings",
                        "ReviewGenerateAgent: " + merged.chunkCount() + " chunks reviewed ("
                                + merged.dedupCount() + " duplicates removed)",
                        "SummaryMergeAgent: Final assessment merged"
                ))
                .build();

        context.setFinalResult(result);

        // 6. Build summary
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("totalFindings", context.getRuleResults().size());
        output.put("criticalFindings", context.getRuleResults().stream()
                .filter(r -> r.getRiskLevel() == RiskLevel.CRITICAL).count());
        output.put("highFindings", context.getRuleResults().stream()
                .filter(r -> r.getRiskLevel() == RiskLevel.HIGH).count());
        output.put("riskScore", riskScore.getTotalScore());
        output.put("riskLevel", riskScore.getRiskLevel().name());
        output.put("confidence", confidence);
        output.put("projectType", context.getProjectType());
        output.put("languages", context.getLanguages());
        output.put("frameworks", context.getFrameworks());

        String summary = String.format("Analysis complete: Risk=%s (Score=%d), %d files, %d chunks merged, Confidence=%.0f%%",
                riskScore.getRiskLevel().name(),
                riskScore.getTotalScore(),
                fileAnalysis.size(),
                merged.chunkCount(),
                (double) confidence.getOrDefault("overallConfidence", 0.0));

        log.info("{}: {}", getName(), summary);
        return AgentResult.success(getName(), summary, output);
    }

    /**
     * Merge multiple chunk reviews into a single cohesive report.
     * Deduplicates findings that appear across multiple chunks.
     */
    private MergeResult mergeChunkReviews(List<String> chunkReviews) {
        if (chunkReviews.isEmpty()) return new MergeResult("", 0, 0);

        // For single chunk, no merging needed
        if (chunkReviews.size() == 1) {
            return new MergeResult(chunkReviews.get(0), 1, 0);
        }

        // Extract and dedup findings across chunks
        Set<String> seenFindings = new HashSet<>();
        StringBuilder merged = new StringBuilder();
        int dedupCount = 0;

        for (int i = 0; i < chunkReviews.size(); i++) {
            String review = chunkReviews.get(i);
            if (review == null || review.startsWith("[AI Review failed")) continue;

            if (i > 0) {
                merged.append("\n\n---\n\n");
            }

            // Dedup: check for repeated finding patterns (lines with ** or [CRITICAL] etc.)
            String[] lines = review.split("\n");
            StringBuilder dedupedReview = new StringBuilder();
            for (String line : lines) {
                String normalized = normalizeForDedup(line);
                if (normalized.length() > 20 && !seenFindings.add(normalized)) {
                    dedupCount++;
                    continue;
                }
                dedupedReview.append(line).append("\n");
            }
            merged.append(dedupedReview.toString().trim());
        }

        return new MergeResult(merged.toString(), chunkReviews.size(), dedupCount);
    }

    private String normalizeForDedup(String line) {
        // Normalize finding lines for dedup comparison
        return line.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[*\\-#>]", "")
                .trim();
    }

    private record MergeResult(String combined, int chunkCount, int dedupCount) {}

    private Map<String, Object> calculateConfidence(AgentContext context, RiskScore riskScore) {
        @SuppressWarnings("unchecked")
        Map<String, Object> chunkMeta = (Map<String, Object>) context.getMetadata().get("chunkMeta");

        int changeScore = 0;
        Map<String, String> diffAnalysis = context.getDiffAnalysis();
        if (diffAnalysis != null) {
            changeScore = Integer.parseInt(diffAnalysis.getOrDefault("changeScore", "0"));
        }

        ConfidenceInput input = ConfidenceInput.builder()
                .ruleResults(context.getRuleResults())
                .chunkReviews(context.getChunkReviews())
                .chunkMeta(chunkMeta)
                .changeScore(changeScore)
                .languages(context.getLanguages())
                .frameworks(context.getFrameworks())
                .build();

        ConfidenceResult result = confidenceCalculator.calculate(input);

        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("overallConfidence", result.getOverallConfidence());
        conf.put("confidenceLevel", result.getConfidenceLevel());
        conf.put("factors", result.getFactors());
        conf.put("summary", result.getSummary());

        return conf;
    }

    private List<FileAnalysis> buildFileAnalysis(PrInfo prInfo, List<RuleResult> allRules) {
        List<FileAnalysis> analysis = new ArrayList<>();
        for (PrFile file : prInfo.getFiles()) {
            List<RuleResult> fileRules = allRules.stream()
                    .filter(r -> file.getFilename().equals(r.getFile()))
                    .collect(Collectors.toList());

            RiskLevel highestRisk = fileRules.stream()
                    .filter(RuleResult::isMatched)
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
}
