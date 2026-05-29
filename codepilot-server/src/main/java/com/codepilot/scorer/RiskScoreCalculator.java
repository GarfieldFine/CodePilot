package com.codepilot.scorer;

import com.codepilot.github.model.PrInfo;
import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RiskScoreCalculator {

    public RiskScore calculate(PrInfo prInfo, List<RuleResult> ruleResults, String aiFindings) {
        int score = 0;
        int maxScore = 100;

        // 1. File count factor (max 20 pts)
        score += scoreFileCount(prInfo.getChangedFiles());

        // 2. Rule engine findings (max 35 pts)
        score += scoreRuleResults(ruleResults);

        // 3. Module sensitivity (max 15 pts)
        score += scoreModuleSensitivity(prInfo);

        // 4. Change volume (max 15 pts)
        score += scoreChangeVolume(prInfo.getAdditions(), prInfo.getDeletions());

        // 5. AI findings severity (max 15 pts)
        score += scoreAiFindings(aiFindings);

        RiskLevel level = mapScoreToLevel(score);
        Map<RiskLevel, Long> ruleBreakdown = ruleResults.stream()
                .collect(Collectors.groupingBy(RuleResult::getRiskLevel, Collectors.counting()));

        return RiskScore.builder()
                .totalScore(score)
                .maxScore(maxScore)
                .riskLevel(level)
                .fileCountScore(scoreFileCount(prInfo.getChangedFiles()))
                .ruleFindingsScore(scoreRuleResults(ruleResults))
                .moduleScore(scoreModuleSensitivity(prInfo))
                .volumeScore(scoreChangeVolume(prInfo.getAdditions(), prInfo.getDeletions()))
                .aiScore(scoreAiFindings(aiFindings))
                .ruleBreakdown(ruleBreakdown)
                .fileCount(prInfo.getChangedFiles())
                .totalAdditions(prInfo.getAdditions())
                .totalDeletions(prInfo.getDeletions())
                .build();
    }

    private int scoreFileCount(int fileCount) {
        if (fileCount <= 3) return 3;
        if (fileCount <= 10) return 8;
        if (fileCount <= 20) return 13;
        if (fileCount <= 30) return 17;
        return 20;
    }

    private int scoreRuleResults(List<RuleResult> results) {
        if (results.isEmpty()) return 0;
        int score = 0;
        for (RuleResult r : results) {
            switch (r.getRiskLevel()) {
                case CRITICAL -> score += 8;
                case HIGH -> score += 5;
                case MEDIUM -> score += 2;
                case LOW -> score += 1;
            }
        }
        return Math.min(score, 35);
    }

    private int scoreModuleSensitivity(PrInfo prInfo) {
        int score = 0;
        String title = prInfo.getTitle() != null ? prInfo.getTitle().toLowerCase() : "";
        String desc = prInfo.getDescription() != null ? prInfo.getDescription().toLowerCase() : "";

        if (title.contains("transaction") || desc.contains("transaction")
                || title.contains("payment") || desc.contains("payment")) score += 5;
        if (title.contains("security") || desc.contains("security")
                || title.contains("auth") || desc.contains("login")) score += 5;
        if (title.contains("db") || title.contains("database")
                || title.contains("migration") || title.contains("ddl")) score += 3;
        if (title.contains("cache") || title.contains("redis")
                || title.contains("concurrent") || title.contains("lock")) score += 2;
        return Math.min(score, 15);
    }

    private int scoreChangeVolume(int additions, int deletions) {
        int total = additions + deletions;
        if (total <= 50) return 2;
        if (total <= 200) return 5;
        if (total <= 500) return 10;
        if (total <= 1000) return 13;
        return 15;
    }

    private int scoreAiFindings(String aiFindings) {
        if (aiFindings == null || aiFindings.isEmpty()) return 0;
        String lower = aiFindings.toLowerCase();
        int score = 0;
        if (lower.contains("critical") || lower.contains("严重")) score += 5;
        if (lower.contains("high") || lower.contains("高风险")) score += 4;
        if (lower.contains("medium") || lower.contains("中风险")) score += 3;
        if (lower.contains("sql injection") || lower.contains("注入")) score += 3;
        return Math.min(score, 15);
    }

    private RiskLevel mapScoreToLevel(int score) {
        if (score >= 60) return RiskLevel.CRITICAL;
        if (score >= 35) return RiskLevel.HIGH;
        if (score >= 15) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}
