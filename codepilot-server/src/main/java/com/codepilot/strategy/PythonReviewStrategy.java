package com.codepilot.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PythonReviewStrategy implements ReviewStrategy {

    @Override
    public String getName() { return "Python"; }

    @Override
    public int getPriority() { return 20; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        if (language == null) return false;
        String lang = language.toLowerCase();
        if (lang.contains("python") || lang.contains("py")) return true;
        String fwStr = String.join(",", frameworks).toLowerCase();
        return fwStr.contains("django") || fwStr.contains("flask") || fwStr.contains("fastapi");
    }

    @Override
    public List<String> getFocusAreas() {
        return List.of(
                "Type Safety (type hints, Optional/None handling, mypy compliance)",
                "Exception Handling (try-except granularity, bare except avoidance)",
                "Async/Await Correctness (asyncio patterns, blocking calls in async context)",
                "Dependency Management (requirements.txt, version pinning, transitive deps)",
                "Resource Management (context managers, file/connection closing)",
                "Performance (list comprehensions vs loops, generator usage, GIL awareness)",
                "Security (SQL injection, pickle safety, secrets management)"
        );
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Python Expertise Required
                - Check type annotations: all public functions should have type hints
                - Review exception handling: avoid bare `except:`, catch specific exceptions
                - Verify async correctness: no blocking calls (time.sleep, requests) inside async functions
                - Examine resource management: use `with` statements for files, connections, locks
                - Watch for mutable default arguments: `def foo(x=[])` is a classic bug
                - Validate dependency changes: check version pinning and compatibility
                - Check for SQL injection: use parameterized queries, never string formatting
                """;
    }
}
