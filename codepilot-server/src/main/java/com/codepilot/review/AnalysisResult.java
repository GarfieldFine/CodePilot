package com.codepilot.review;

import com.codepilot.model.enums.AnalysisStatus;
import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.RuleResult;
import com.codepilot.scorer.RiskScore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {
    private String analysisId;
    private String prTitle;
    private String prUrl;
    private Integer prNumber;
    private String owner;
    private String repo;
    private String author;
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
    private List<RuleResult> ruleResults;
    private RiskScore riskScore;
    private String aiRawOutput;
    private AnalysisStatus status;
    private List<FileAnalysis> fileAnalysis;
}
