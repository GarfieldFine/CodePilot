package com.codepilot.persona;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BackendReviewer implements ReviewerPersona {

    @Override
    public String getName() { return "Backend Reviewer"; }

    @Override
    public String getDescription() { return "Backend service and API specialist (Java/Spring/Go/Python)"; }

    @Override
    public int getPriority() { return 20; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        if (language == null) return true;
        String lang = language.toLowerCase();
        return lang.contains("java") || lang.contains("go") || lang.contains("golang")
                || lang.contains("python") || lang.contains("kotlin")
                || lang.contains("sql") || lang.contains("plsql");
    }

    @Override
    public boolean shouldActivate(List<String> riskDimensions) {
        // Backend reviewer is always active when backend languages are detected
        return true;
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Backend Reviewer Persona Active
                You are reviewing this code as an experienced **backend engineer**. Focus on service reliability, data integrity, and production readiness.

                ### Backend Checklist
                - **Transaction Management**: Correct @Transactional boundaries, propagation settings, rollback rules. Watch for self-invocation bypass and missing rollbackFor.
                - **Concurrency Safety**: Thread safety in shared state. Correct use of synchronized/ReentrantLock/atomic classes. Thread pool sizing and lifecycle.
                - **Database Interactions**: ORM efficiency, connection pool usage, query plan awareness. Avoid SELECT * and implicit cartesian products.
                - **Caching Strategy**: Redis/Memcached usage patterns, cache-aside vs write-through, consistency guarantees, key namespace conventions.
                - **Message Queues**: Idempotent consumers, DLQ handling, message ordering guarantees, poison message handling.
                - **Exception Handling**: Proper exception hierarchy, appropriate HTTP status codes, structured error responses, and meaningful log context.
                - **Resource Management**: Connection/stream/reader lifecycle, try-with-resources, pool exhaustion risks, graceful shutdown hooks.
                - **API Design**: Consistent URL patterns, proper HTTP verbs, pagination support, rate limiting considerations, idempotency keys.

                ### Output Requirements for Backend Findings
                - Classify each finding as: reliability / data-integrity / performance / maintainability
                - For concurrency issues, explain the interleaving that triggers the bug
                - For database issues, include the query pattern and suggested rewrite
                """;
    }
}
