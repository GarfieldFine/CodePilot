package com.codepilot.persona;

import java.util.List;

/**
 * Defines a reviewer persona with a distinct expertise focus, communication style,
 * and review checklist. Multiple personas can be active for a single PR.
 */
public interface ReviewerPersona {

    /** Unique persona identifier */
    String getName();

    /** Brief description of this persona's expertise */
    String getDescription();

    /** System prompt extension appended to the base prompt when this persona is active */
    String getSystemPromptExtension();

    /** Whether this persona is relevant for the given language and frameworks */
    boolean supports(String language, List<String> frameworks);

    /** Whether this persona should be activated based on risk dimensions */
    boolean shouldActivate(List<String> riskDimensions);

    /** Lower number = higher priority. Determines ordering in the combined prompt. */
    default int getPriority() { return 100; }
}
