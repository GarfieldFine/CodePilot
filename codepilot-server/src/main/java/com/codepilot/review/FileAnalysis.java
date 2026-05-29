package com.codepilot.review;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.RuleResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAnalysis {
    private String filename;
    private String language;
    private String status;
    private Integer additions;
    private Integer deletions;
    private RiskLevel riskLevel;
    private List<RuleResult> findings;
}
