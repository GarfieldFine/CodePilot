package com.codepilot.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultReviewStrategy implements ReviewStrategy {

    @Override
    public String getName() { return "Default"; }

    @Override
    public int getPriority() { return 1000; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        return true; // fallback for everything
    }

    @Override
    public List<String> getFocusAreas() {
        return List.of(
                "Code Quality (naming, structure, duplication, readability)",
                "Error Handling (exception propagation, logging, error recovery)",
                "Testing (test coverage, edge cases, mocking strategy)",
                "Security (input validation, authentication, authorization)",
                "Performance (algorithm complexity, resource usage, caching)"
        );
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## General Code Review Expertise Required
                - Review code quality: clear naming, single responsibility, DRY principle
                - Check error handling: errors should be handled explicitly, not silently ignored
                - Examine testing: verify adequate test coverage for changed paths
                - Validate security: input validation, proper authentication/authorization checks
                - Review performance: watch for unnecessary allocations, inefficient algorithms
                """;
    }
}
