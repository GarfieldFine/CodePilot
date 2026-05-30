package com.codepilot.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Executes individual agents and manages the SSE event emission for agent lifecycle.
 */
@Slf4j
@Component
public class AgentExecutor {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Run a single agent, emitting lifecycle events via callback.
     */
    public AgentResult runAgent(Agent agent, AgentContext context, Consumer<SseAgentEvent> eventSink) {
        if (!agent.shouldRun(context)) {
            AgentResult skipped = AgentResult.skipped(agent.getName(), "Precondition not met");
            eventSink.accept(SseAgentEvent.skipped(agent.getName(), skipped.getSummary()));
            return skipped;
        }

        long start = System.currentTimeMillis();
        eventSink.accept(SseAgentEvent.started(agent.getName(), agent.getDescription()));

        try {
            AgentResult result = agent.execute(context);
            result.setDurationMs(System.currentTimeMillis() - start);
            eventSink.accept(SseAgentEvent.completed(agent.getName(), result.getSummary()));
            log.info("Agent [{}] completed in {}ms: {}", agent.getName(), result.getDurationMs(), result.getSummary());
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Agent [{}] failed after {}ms: {}", agent.getName(), duration, e.getMessage());
            eventSink.accept(SseAgentEvent.failed(agent.getName(), e.getMessage()));
            return AgentResult.failed(agent.getName(), e.getMessage());
        }
    }

    /**
     * Run multiple agents sequentially, one after another.
     */
    public List<AgentResult> runSequential(List<Agent> agents, AgentContext context,
                                           Consumer<SseAgentEvent> eventSink) {
        List<AgentResult> results = new ArrayList<>();
        for (Agent agent : agents) {
            results.add(runAgent(agent, context, eventSink));
        }
        return results;
    }

    /**
     * Run multiple agents in parallel.
     */
    public List<AgentResult> runParallel(List<Agent> agents, AgentContext context,
                                         Consumer<SseAgentEvent> eventSink) {
        List<CompletableFuture<AgentResult>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(
                        () -> runAgent(agent, context, eventSink), executor))
                .toList();

        return futures.stream()
                .map(f -> {
                    try { return f.get(120, TimeUnit.SECONDS); }
                    catch (Exception e) { return AgentResult.failed("parallel-agent", e.getMessage()); }
                })
                .toList();
    }

    /**
     * SSE event for agent lifecycle. Separate from main SSE stream to keep event types clear.
     */
    public record SseAgentEvent(String type, String agentName, String message) {

        public static SseAgentEvent started(String agentName, String description) {
            return new SseAgentEvent("agent_start", agentName, description);
        }

        public static SseAgentEvent completed(String agentName, String summary) {
            return new SseAgentEvent("agent_complete", agentName, summary);
        }

        public static SseAgentEvent failed(String agentName, String error) {
            return new SseAgentEvent("agent_error", agentName, error);
        }

        public static SseAgentEvent skipped(String agentName, String reason) {
            return new SseAgentEvent("agent_skip", agentName, reason);
        }

        public static SseAgentEvent pipelineStage(String stage) {
            return new SseAgentEvent("pipeline_stage", stage, "");
        }

        public String toJson() {
            return String.format("{\"type\":\"%s\",\"agentName\":\"%s\",\"message\":\"%s\"}",
                    type.replace("\"", "\\\""),
                    agentName != null ? agentName.replace("\"", "\\\"") : "",
                    message != null ? message.replace("\"", "\\\"") : "");
        }
    }
}
