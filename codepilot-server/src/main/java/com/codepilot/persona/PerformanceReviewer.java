package com.codepilot.persona;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PerformanceReviewer implements ReviewerPersona {

    @Override
    public String getName() { return "Performance Reviewer"; }

    @Override
    public String getDescription() { return "Performance optimization specialist"; }

    @Override
    public int getPriority() { return 10; }

    @Override
    public boolean supports(String language, List<String> frameworks) { return true; }

    @Override
    public boolean shouldActivate(List<String> riskDimensions) {
        if (riskDimensions == null || riskDimensions.isEmpty()) return false;
        return riskDimensions.stream().anyMatch(d ->
                d.contains("Database") || d.contains("SQL") || d.contains("Cache")
                        || d.contains("loop") || d.contains("algorithm") || d.contains("Large"));
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Performance Reviewer Persona Active
                You are reviewing this code through a **performance-first** lens. Identify performance bottlenecks and optimization opportunities.

                ### Performance Checklist
                - **Database Queries**: N+1 queries, missing indexes, full table scans, inefficient JOINs, missing batch operations
                - **Caching**: Missing or misconfigured cache, cache stampede, inappropriate TTL, cache invalidation bugs
                - **Memory Management**: Memory leaks (unclosed resources, growing collections), excessive object allocation, large retained heaps
                - **Algorithm Complexity**: O(n²) or worse in hot paths, unnecessary iterations, redundant computations
                - **Network IO**: Chatty API calls, missing connection pooling, synchronous blocking IO on event loops, missing timeouts
                - **Concurrency**: Lock contention, thread pool sizing, blocking operations in async contexts, false sharing
                - **Serialization**: Inefficient serialization formats, over-fetching data, large payload sizes

                ### Output Requirements for Performance Findings
                - Estimate the performance impact: negligible / noticeable / significant / critical
                - Identify the bottleneck type: CPU-bound / IO-bound / memory-bound / lock-contention
                - Provide a concrete optimization with expected improvement (e.g., "batch these 3 queries into 1, reducing DB round-trips by 66%")
                """;
    }
}
