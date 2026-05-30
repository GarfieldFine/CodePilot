package com.codepilot.agent;

import reactor.core.publisher.Mono;

/**
 * Core Agent interface. Each agent has a single responsibility and operates on AgentContext.
 */
public interface Agent {

    String getName();

    String getDescription();

    /**
     * Execute agent logic synchronously (blocking).
     * Called by AgentExecutor within boundedElastic scheduler.
     */
    AgentResult execute(AgentContext context);

    /**
     * Execute agent logic asynchronously.
     * Default wraps execute() in Mono for Reactor pipeline compatibility.
     */
    default Mono<AgentResult> executeAsync(AgentContext context) {
        return Mono.fromCallable(() -> execute(context))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Whether this agent should run given the current context.
     */
    default boolean shouldRun(AgentContext context) {
        return true;
    }

    /**
     * Priority within a stage. Higher priority runs first.
     */
    default int priority() {
        return 100;
    }
}
