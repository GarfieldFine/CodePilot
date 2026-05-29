package com.codepilot.strategy;

import java.util.List;

/**
 * Dynamic review strategy interface. Each implementation provides language/framework-specific
 * focus areas, prompt extensions, and review guidance.
 */
public interface ReviewStrategy {

    String getName();

    boolean supports(String language, List<String> frameworks);

    List<String> getFocusAreas();

    String getSystemPromptExtension();

    /** Lower = higher priority when multiple strategies match. */
    default int getPriority() { return 100; }
}
