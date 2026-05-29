package com.codepilot.scorer;

import com.codepilot.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScore {
    private int totalScore;
    private int maxScore;
    private RiskLevel riskLevel;
    private int fileCountScore;
    private int ruleFindingsScore;
    private int moduleScore;
    private int volumeScore;
    private int aiScore;
    private Map<RiskLevel, Long> ruleBreakdown;
    private int fileCount;
    private int totalAdditions;
    private int totalDeletions;
}
