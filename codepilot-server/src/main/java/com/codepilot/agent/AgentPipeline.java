package com.codepilot.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Consumer;

/**
 * Orchestrates agent execution in a multi-stage pipeline.
 *
 * Pipeline stages:
 *   Stage 1 (SEQUENTIAL): RepositoryAnalyze → DiffAnalyze → ContextBuild
 *   Stage 2 (PARALLEL):    RiskDetect(Rules) + RiskDetect(AI)
 *   Stage 3 (SEQUENTIAL):  ReviewGenerate
 *   Stage 4 (FINAL):       SummaryMerge
 */
@Slf4j
@Component
public class AgentPipeline {

    private final AgentExecutor agentExecutor;

    public AgentPipeline(AgentExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
    }

    /**
     * A single stage in the pipeline. Contains a list of agents and execution mode.
     */
    public record PipelineStage(String name, List<Agent> agents, ExecutionMode mode) {
        public enum ExecutionMode { SEQUENTIAL, PARALLEL }
    }

    /**
     * Execute the full pipeline synchronously (for blocking analyze flow).
     */
    public AgentContext execute(List<PipelineStage> stages, AgentContext context,
                                Consumer<AgentExecutor.SseAgentEvent> eventSink) {
        log.info("Starting agent pipeline for analysis {}", context.getAnalysisId());

        for (PipelineStage stage : stages) {
            eventSink.accept(AgentExecutor.SseAgentEvent.pipelineStage(stage.name()));
            log.info("Pipeline stage [{}] starting with {} agents (mode={})",
                    stage.name(), stage.agents().size(), stage.mode());

            List<AgentResult> results;
            if (stage.mode() == PipelineStage.ExecutionMode.PARALLEL) {
                results = agentExecutor.runParallel(stage.agents(), context, eventSink);
            } else {
                results = agentExecutor.runSequential(stage.agents(), context, eventSink);
            }

            logStageResults(stage.name(), results);
        }

        log.info("Agent pipeline completed for analysis {}", context.getAnalysisId());
        return context;
    }

    /**
     * Execute the full pipeline as a Flux of SSE events (for streaming flow).
     * Emits properly formatted SSE events with "data:" prefix.
     */
    public Flux<String> executeStream(List<PipelineStage> stages, AgentContext context) {
        return Flux.create(sink -> {
            try {
                execute(stages, context, event -> {
                    sink.next("data:" + event.toJson() + "\n\n");
                });
                sink.complete();
            } catch (Exception e) {
                log.error("Pipeline stream error: {}", e.getMessage());
                sink.error(e);
            }
        });
    }

    private void logStageResults(String stageName, List<AgentResult> results) {
        long succeeded = results.stream().filter(r -> r.getStatus() == AgentStatus.COMPLETED).count();
        long failed = results.stream().filter(r -> r.getStatus() == AgentStatus.FAILED).count();
        long skipped = results.stream().filter(r -> r.getStatus() == AgentStatus.SKIPPED).count();
        log.info("Stage [{}] complete: {} succeeded, {} failed, {} skipped",
                stageName, succeeded, failed, skipped);
    }
}
