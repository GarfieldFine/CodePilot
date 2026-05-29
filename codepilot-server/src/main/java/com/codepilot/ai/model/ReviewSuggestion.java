package com.codepilot.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSuggestion {
    private String category;
    private String title;
    private String description;
    private String file;
    private Integer line;
    private String codeSnippet;
    private String suggestedFix;
    private String priority;
}
