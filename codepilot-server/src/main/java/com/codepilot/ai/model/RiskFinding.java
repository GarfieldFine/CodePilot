package com.codepilot.ai.model;

import com.codepilot.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFinding {
    private String file;
    private Integer line;
    private RiskLevel level;
    private String category;
    private String description;
    private String suggestion;
}
