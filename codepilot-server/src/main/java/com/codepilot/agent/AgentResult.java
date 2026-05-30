package com.codepilot.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {
    private String agentName;
    private AgentStatus status;
    private String summary;
    private Map<String, Object> output;
    private String errorMessage;
    private long durationMs;

    public static AgentResult success(String agentName, String summary, Map<String, Object> output) {
        return AgentResult.builder()
                .agentName(agentName)
                .status(AgentStatus.COMPLETED)
                .summary(summary)
                .output(output)
                .build();
    }

    public static AgentResult failed(String agentName, String errorMessage) {
        return AgentResult.builder()
                .agentName(agentName)
                .status(AgentStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    public static AgentResult skipped(String agentName, String reason) {
        return AgentResult.builder()
                .agentName(agentName)
                .status(AgentStatus.SKIPPED)
                .summary(reason)
                .build();
    }
}
