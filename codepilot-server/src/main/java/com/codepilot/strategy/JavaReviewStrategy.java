package com.codepilot.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JavaReviewStrategy implements ReviewStrategy {

    @Override
    public String getName() { return "Java"; }

    @Override
    public int getPriority() { return 10; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        if (language == null) return false;
        String lang = language.toLowerCase();
        if (lang.contains("java") || lang.contains("kotlin")) return true;
        String fwStr = String.join(",", frameworks).toLowerCase();
        return fwStr.contains("spring") || fwStr.contains("mybatis") || fwStr.contains("maven")
                || fwStr.contains("gradle") || fwStr.contains("jpa") || fwStr.contains("hibernate");
    }

    @Override
    public List<String> getFocusAreas() {
        return List.of(
                "Concurrency Safety (thread pools, synchronized, volatile, locks, atomic)",
                "Transaction Management (@Transactional scope, isolation levels, propagation)",
                "Spring Context (bean scopes, circular dependencies, lifecycle hooks)",
                "SQL/ORM (N+1 queries, indexing, batch operations, lazy loading)",
                "Redis/Cache (key design, consistency, eviction, distributed locks)",
                "Exception Handling (exception propagation, logging, error codes)",
                "Memory Management (object allocation, GC pressure, memory leaks)",
                "API Design (REST conventions, versioning, backward compatibility)"
        );
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Java/Spring Expertise Required
                - Check for proper @Transactional usage: ensure write operations are transactional, read-only where appropriate
                - Verify thread safety: shared mutable state must use synchronization or concurrent collections
                - Review Spring bean scopes: singleton beans must be stateless, prototype for stateful beans
                - Check MyBatis/JPA usage: watch for N+1 queries, missing indexes, inefficient batch operations
                - Validate Redis operations: key naming conventions, TTL settings, cache invalidation strategy
                - Examine exception handling: never swallow exceptions silently, use appropriate HTTP status codes
                - Look for resource leaks: unclosed streams, connections, or native resources
                """;
    }
}
