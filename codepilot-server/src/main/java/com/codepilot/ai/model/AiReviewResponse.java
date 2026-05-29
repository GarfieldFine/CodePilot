package com.codepilot.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewResponse {
    private String summary;
    private List<RiskFinding> riskFindings;
    private List<ReviewSuggestion> suggestions;
    private String overallAssessment;
    private Map<String, String> metadata;
}
