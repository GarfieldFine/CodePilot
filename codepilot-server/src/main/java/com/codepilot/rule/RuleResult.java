package com.codepilot.rule;

import com.codepilot.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {
    private String ruleName;
    private String category;
    private RiskLevel riskLevel;
    private String file;
    private Integer line;
    private String message;
    private String suggestion;
    private String codeSnippet;
    private boolean matched;
}
