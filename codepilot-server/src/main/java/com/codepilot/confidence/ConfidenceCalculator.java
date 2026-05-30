package com.codepilot.confidence;

import com.codepilot.rule.RuleResult;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Calculates AI review confidence using multi-factor analysis.
 *
 * Factors (weighted):
 * 1. Rule coverage    — what fraction of static rules fired (0.20)
 * 2. Chunk success    — what fraction of chunks produced valid output (0.25)
 * 3. Cross-chunk consistency — inverse of review length variance (0.15)
 * 4. Complexity penalty — change score reduces confidence (0.20)
 * 5. Repository knowledge — language/framework breadth boosts confidence (0.10)
 * 6. Token coverage   — adequate context for AI reasoning (0.10)
 *
 * Output is clamped to [30, 98] and includes a qualitative level.
 */
@Slf4j
@Component
public class ConfidenceCalculator {

    /**
     * All inputs needed for confidence calculation.
     */
    @Data
    @Builder
    public static class ConfidenceInput {
        private List<RuleResult> ruleResults;
        private List<String> chunkReviews;
        private Map<String, Object> chunkMeta;
        private int changeScore;
        private List<String> languages;
        private List<String> frameworks;
    }

    /**
     * Structured confidence result with factor breakdown.
     */
    @Data
    @Builder
    public static class ConfidenceResult {
        private double overallConfidence;
        private String confidenceLevel;
        private Map<String, Double> factors;
        private String summary;
    }

    /**
     * Calculate confidence for a completed PR analysis.
     */
    public ConfidenceResult calculate(ConfidenceInput input) {
        // Factor 1: Rule coverage
        long matchedRules = input.ruleResults.stream().filter(RuleResult::isMatched).count();
        long totalRules = Math.max(input.ruleResults.size(), 1);
        double ruleCoverage = Math.min(1.0, (double) matchedRules / totalRules);

        // Factor 2: Chunk success rate
        int totalChunks = 0;
        long successfulChunks = 0;
        if (input.chunkMeta != null) {
            totalChunks = ((Number) input.chunkMeta.getOrDefault("totalChunks", 0)).intValue();
            successfulChunks = ((Number) input.chunkMeta.getOrDefault("successfulChunks", 0)).longValue();
        }
        if (totalChunks == 0) totalChunks = input.chunkReviews.size();
        if (successfulChunks == 0) successfulChunks = countValidChunks(input.chunkReviews);
        double chunkSuccess = totalChunks > 0 ? (double) successfulChunks / totalChunks : 1.0;

        // Factor 3: Cross-chunk consistency
        double consistency = calculateCrossChunkConsistency(input.chunkReviews);

        // Factor 4: Complexity penalty
        double complexity = Math.max(0.3, 1.0 - (input.changeScore / 150.0));

        // Factor 5: Repository knowledge
        double repoKnowledge = calculateRepoKnowledge(input.languages, input.frameworks);

        // Factor 6: Token coverage
        int totalTokens = 0;
        if (input.chunkMeta != null) {
            totalTokens = ((Number) input.chunkMeta.getOrDefault("totalTokens", 0)).intValue();
        }
        double tokenCoverage = totalTokens > 500 ? Math.min(1.0, totalTokens / 6000.0) : 0.6;

        // Weighted combination
        double overall = ruleCoverage * 0.20
                + chunkSuccess * 0.25
                + consistency * 0.15
                + complexity * 0.20
                + repoKnowledge * 0.10
                + tokenCoverage * 0.10;

        overall = overall * 100;
        overall = Math.min(98, Math.max(30, overall));

        // Build factor map
        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("ruleCoverage", round(ruleCoverage));
        factors.put("chunkSuccess", round(chunkSuccess));
        factors.put("crossChunkConsistency", round(consistency));
        factors.put("complexityFactor", round(complexity));
        factors.put("repoKnowledge", round(repoKnowledge));
        factors.put("tokenCoverage", round(tokenCoverage));

        String level;
        if (overall >= 85) level = "HIGH";
        else if (overall >= 65) level = "MEDIUM";
        else level = "LOW";

        String summary = String.format(
                "Confidence: %.0f%% (%s) — rules=%.0f%%, chunks=%d/%d, consistency=%.0f%%, complexity=%.0f%%",
                overall, level, ruleCoverage * 100, successfulChunks, totalChunks, consistency * 100, complexity * 100);

        log.info(summary);

        return ConfidenceResult.builder()
                .overallConfidence(round(overall))
                .confidenceLevel(level)
                .factors(factors)
                .summary(summary)
                .build();
    }

    /**
     * Calculate cross-chunk consistency by analyzing review output lengths.
     * High variance in review length suggests inconsistent quality.
     */
    private double calculateCrossChunkConsistency(List<String> chunkReviews) {
        if (chunkReviews == null || chunkReviews.size() <= 1) return 1.0;

        List<Integer> lengths = chunkReviews.stream()
                .filter(r -> r != null && !r.startsWith("[AI Review failed"))
                .map(String::length)
                .filter(l -> l > 0)
                .toList();

        if (lengths.size() <= 1) return 1.0;

        double mean = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        if (mean == 0) return 0.5;

        double variance = lengths.stream()
                .mapToDouble(l -> Math.pow(l - mean, 2))
                .average()
                .orElse(0);

        double cv = Math.sqrt(variance) / mean; // coefficient of variation
        // Low CV = high consistency. Map: CV=0 → 1.0, CV=1 → 0.5, CV=2+ → 0.2
        return Math.max(0.2, 1.0 / (1.0 + cv));
    }

    private double calculateRepoKnowledge(List<String> languages, List<String> frameworks) {
        int langCount = languages != null ? languages.size() : 0;
        int fwCount = frameworks != null ? frameworks.size() : 0;
        // More detected context = the system has more knowledge about this repo
        return Math.min(1.0, (langCount * 0.15 + fwCount * 0.10 + 0.4));
    }

    private long countValidChunks(List<String> chunkReviews) {
        if (chunkReviews == null) return 0;
        return chunkReviews.stream()
                .filter(r -> r != null && !r.startsWith("[AI Review failed"))
                .count();
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
