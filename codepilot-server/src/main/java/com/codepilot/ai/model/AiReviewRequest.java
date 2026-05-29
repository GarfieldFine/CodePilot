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
public class AiReviewRequest {
    private String diff;
    private String contextCode;
    private String commitMessages;
    private String fileLanguage;
    private List<String> riskRules;
    private Map<String, Object> extraContext;
}
