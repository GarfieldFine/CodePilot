package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.AiProviderFactory;
import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import com.codepilot.model.enums.AnalysisStatus;
import com.codepilot.model.enums.RiskLevel;
import com.codepilot.review.AnalysisResult;
import com.codepilot.review.FileAnalysis;
import com.codepilot.rule.RuleResult;
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
    private final AiProviderFactory aiProviderFactory;

    public SummaryMergeAgent(RiskScoreCalculator scoreCalculator,
                              AiProviderFactory aiProviderFactory) {
        this.scoreCalculator = scoreCalculator;
        this.aiProviderFactory = aiProviderFactory;
    }

    @Override
    public String getName() { return "SummaryMergeAgent"; }

    @Override
    public String getDescription() { return "Merging all analyses and computing final assessment"; }

    @Override
    public int priority() { return 99; }

    @Override
    public AgentResult execute(AgentContext context) {
        // 1. Merge chunk reviews
        List<String> chunkReviews = context.getChunkReviews();
        String combinedAiOutput = chunkReviews.isEmpty() ? "" : String.join("\n\n---\n\n", chunkReviews);

        // 2. Compute risk score
        RiskScore riskScore = scoreCalculator.calculate(
                context.getPrInfo(),
                context.getRuleResults(),
                combinedAiOutput);
        context.setRiskScore(riskScore);

        // 3. Calculate AI confidence
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
                .aiRawOutput(combinedAiOutput)
                .status(AnalysisStatus.COMPLETED)
                .fileAnalysis(fileAnalysis)
                .repositoryProfile(repoProfile)
                .confidenceScores(confidence)
                .agentTimeline(List.of(
                        "RepositoryAnalyzeAgent: " + context.getProjectType() + " project detected",
                        "DiffAnalyzeAgent: " + fileAnalysis.size() + " files analyzed",
                        "ContextBuildAgent: Context built for " + String.join(", ", context.getLanguages()),
                        "RiskDetectAgent: " + context.getRuleResults().size() + " findings",
                        "ReviewGenerateAgent: " + context.getChunkReviews().size() + " chunks reviewed",
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

        String summary = String.format("Analysis complete: Risk=%s (Score=%d), %d files reviewed, Confidence=%.0f%%",
                riskScore.getRiskLevel().name(),
                riskScore.getTotalScore(),
                fileAnalysis.size(),
                (double) confidence.getOrDefault("overallConfidence", 0.0));

        log.info("{}: {}", getName(), summary);
        return AgentResult.success(getName(), summary, output);
    }

    private Map<String, Object> calculateConfidence(AgentContext context, RiskScore riskScore) {
        Map<String, Object> conf = new LinkedHashMap<>();

        // Factor 1: Rule engine participation (how many rules fired)
        long matchedRules = context.getRuleResults().stream().filter(RuleResult::isMatched).count();
        long totalRules = context.getRuleResults().size();
        double ruleCoverage = totalRules > 0 ? (double) matchedRules / Math.max(totalRules, 1) : 0.5;

        // Factor 2: AI review completeness (did all chunks produce output?)
        int totalChunks = context.getChunkReviews().size();
        long validChunks = context.getChunkReviews().stream()
                .filter(r -> r != null && !r.startsWith("[AI Review failed"))
                .count();
        double chunkCompleteness = totalChunks > 0 ? (double) validChunks / totalChunks : 1.0;

        // Factor 3: Change complexity factor (higher complexity = lower confidence)
        Map<String, String> diffAnalysis = context.getDiffAnalysis();
        int changeScore = diffAnalysis != null ? Integer.parseInt(diffAnalysis.getOrDefault("changeScore", "0")) : 0;
        double complexityFactor = Math.max(0.5, 1.0 - (changeScore / 200.0));

        // Factor 4: Repository knowledge factor (more context = higher confidence)
        int languagesKnown = context.getLanguages().size();
        int frameworksKnown = context.getFrameworks().size();
        double repoKnowledge = Math.min(1.0, (languagesKnown * 0.2 + frameworksKnown * 0.15 + 0.3));

        // Combined confidence score (weighted)
        double overallConfidence = (ruleCoverage * 0.25 +
                chunkCompleteness * 0.30 +
                complexityFactor * 0.25 +
                repoKnowledge * 0.20) * 100;

        overallConfidence = Math.min(98, Math.max(30, overallConfidence));

        conf.put("overallConfidence", Math.round(overallConfidence * 10.0) / 10.0);
        conf.put("ruleCoverage", Math.round(ruleCoverage * 100.0) / 100.0);
        conf.put("chunkCompleteness", Math.round(chunkCompleteness * 100.0) / 100.0);
        conf.put("complexityFactor", Math.round(complexityFactor * 100.0) / 100.0);
        conf.put("repoKnowledge", Math.round(repoKnowledge * 100.0) / 100.0);

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
