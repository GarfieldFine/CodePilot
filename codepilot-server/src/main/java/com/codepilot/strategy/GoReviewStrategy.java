package com.codepilot.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoReviewStrategy implements ReviewStrategy {

    @Override
    public String getName() { return "Go"; }

    @Override
    public int getPriority() { return 30; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        if (language == null) return false;
        String lang = language.toLowerCase();
        if (lang.contains("go") || lang.equals("golang")) return true;
        String fwStr = String.join(",", frameworks).toLowerCase();
        return fwStr.contains("go modules") || fwStr.contains("gin") || fwStr.contains("echo");
    }

    @Override
    public List<String> getFocusAreas() {
        return List.of(
                "Goroutine Lifecycle (leaks, proper cancellation, context propagation)",
                "Channel Usage (deadlocks, unbuffered vs buffered, select patterns)",
                "Error Handling (error wrapping with %w, sentinel errors, error inspection)",
                "Defer Safety (defer in loops, defer after os.Exit)",
                "Concurrency (mutex usage, race conditions, sync.Once)",
                "Memory (slice capacity pre-allocation, pointer vs value receivers)",
                "Context Usage (context.Background vs TODO, timeout handling)"
        );
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Go Expertise Required
                - Check goroutine lifecycle: every goroutine must have a clear exit path, use context cancellation
                - Review channel operations: ensure send/receive symmetry, watch for blocking on unbuffered channels
                - Validate error handling: errors must be checked or explicitly ignored with `_`, use `fmt.Errorf("%w", ...)` for wrapping
                - Examine defer usage: defer statements in loops can cause resource buildup
                - Check for race conditions: shared state without mutex protection, check with `go test -race`
                - Review context propagation: contexts should flow through the call chain, never stored in structs
                - Validate slice operations: pre-allocate capacity when size is known to avoid repeated growth
                """;
    }
}
