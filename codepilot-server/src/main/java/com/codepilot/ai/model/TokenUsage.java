package com.codepilot.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {
    private int totalTokens;
    private int promptTokens;
    private int completionTokens;
    private int totalChunks;
    private int successfulChunks;
}
