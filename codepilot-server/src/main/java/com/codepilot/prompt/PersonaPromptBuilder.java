package com.codepilot.prompt;

import com.codepilot.persona.ReviewerPersona;
import com.codepilot.strategy.ReviewStrategy;
import com.codepilot.strategy.ReviewStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds persona-enriched system prompts by combining:
 * 1. Base system prompt template (externalized)
 * 2. Active persona extensions (context-aware selection)
 * 3. Language-specific strategy extensions
 *
 * Persona selection is driven by detected languages, frameworks, and risk dimensions.
 */
@Slf4j
@Component
public class PersonaPromptBuilder {

    private final PromptManager promptManager;
    private final List<ReviewerPersona> personas;
    private final ReviewStrategyFactory strategyFactory;

    public PersonaPromptBuilder(PromptManager promptManager,
                                List<ReviewerPersona> personas,
                                ReviewStrategyFactory strategyFactory) {
        this.promptManager = promptManager;
        // Sort by priority for consistent ordering
        this.personas = personas.stream()
                .sorted(Comparator.comparingInt(ReviewerPersona::getPriority))
                .toList();
        this.strategyFactory = strategyFactory;
    }

    /**
     * Build the complete system prompt for a PR review.
     *
     * @param primaryLanguage the primary language detected
     * @param languages       all detected languages
     * @param frameworks      detected frameworks
     * @param projectType     project type from repository analysis
     * @param riskDimensions  risk dimensions from diff analysis (e.g., "SQL changes", "Config changes")
     * @return complete system prompt string
     */
    public String buildSystemPrompt(String primaryLanguage,
                                    List<String> languages,
                                    List<String> frameworks,
                                    String projectType,
                                    List<String> riskDimensions) {
        StringBuilder sb = new StringBuilder();

        // 1. Base system prompt from externalized template
        String basePrompt = promptManager.getTemplate("system-base");
        if (!basePrompt.isEmpty()) {
            sb.append(basePrompt).append("\n\n");
        } else {
            sb.append(getBuiltInBasePrompt()).append("\n\n");
        }

        // 2. Add project context
        sb.append("## Current Review Context\n");
        sb.append("- **Primary Language:** ").append(primaryLanguage).append("\n");
        sb.append("- **All Languages:** ").append(String.join(", ", languages)).append("\n");
        sb.append("- **Frameworks:** ").append(String.join(", ", frameworks)).append("\n");
        sb.append("- **Project Type:** ").append(projectType != null ? projectType : "Unknown").append("\n");

        if (riskDimensions != null && !riskDimensions.isEmpty()) {
            sb.append("- **Risk Signals:** ").append(String.join("; ", riskDimensions)).append("\n");
        }
        sb.append("\n");

        // 3. Select and append active personas
        List<ReviewerPersona> activePersonas = selectActivePersonas(primaryLanguage, languages, frameworks, riskDimensions);
        if (!activePersonas.isEmpty()) {
            sb.append("## Active Reviewer Expertises\n\n");
            for (ReviewerPersona persona : activePersonas) {
                sb.append(persona.getSystemPromptExtension()).append("\n\n");
            }
        }

        // 4. Add language-specific strategy extension
        ReviewStrategy strategy = strategyFactory.findStrategy(primaryLanguage, frameworks);
        if (strategy != null && !"Default".equals(strategy.getName())) {
            sb.append(strategy.getSystemPromptExtension()).append("\n\n");
        }

        // 5. Explainability requirement
        sb.append("## Explainability Requirement\n");
        sb.append("For every issue you flag, you MUST include:\n");
        sb.append("- **Why:** Explain why this is a problem with root cause reasoning\n");
        sb.append("- **Evidence:** Cite specific code lines or patterns as evidence\n");
        sb.append("- **Impact:** Describe the potential impact if this issue reaches production\n");
        sb.append("- **Suggestion:** Provide a concrete, actionable fix with code example where appropriate\n\n");
        sb.append("Do NOT use vague phrases like 'code can be improved' or 'consider refactoring' without specifics.\n");

        log.info("Built system prompt with {} active personas: {}", activePersonas.size(),
                activePersonas.stream().map(ReviewerPersona::getName).collect(Collectors.joining(", ")));

        return sb.toString();
    }

    /**
     * Build a user prompt for a chunk review using the externalized template.
     */
    public String buildUserPrompt(Map<String, String> variables) {
        String template = promptManager.getTemplate("review-template");
        if (!template.isEmpty()) {
            return promptManager.render("review-template", variables);
        }
        return buildBuiltInUserPrompt(variables);
    }

    /**
     * Select which personas should be active for this PR.
     */
    private List<ReviewerPersona> selectActivePersonas(String primaryLanguage,
                                                        List<String> languages,
                                                        List<String> frameworks,
                                                        List<String> riskDimensions) {
        List<ReviewerPersona> active = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        for (ReviewerPersona persona : personas) {
            if (addedNames.contains(persona.getName())) continue;

            boolean applies = false;
            // Check language support
            for (String lang : languages) {
                if (persona.supports(lang, frameworks)) {
                    applies = true;
                    break;
                }
            }
            // Check risk dimension activation
            if (!applies && persona.shouldActivate(
                    riskDimensions != null ? riskDimensions : List.of())) {
                applies = true;
            }

            // Limit to most relevant personas (max 3)
            if (applies && active.size() < 3) {
                active.add(persona);
                addedNames.add(persona.getName());
            }
        }

        return active;
    }

    /**
     * Fallback base prompt when external template is unavailable.
     */
    private String getBuiltInBasePrompt() {
        return """
                You are a Senior Tech Lead and Principal Engineer conducting a professional code review.

                Your expertise includes:
                - 10+ years of enterprise development across frontend and backend
                - Deep knowledge of distributed systems, concurrency, and databases
                - Expert in security best practices and vulnerability detection
                - Experienced in code maintainability, readability, and architectural design

                ## Review Principles
                1. **Be Precise**: Only flag issues you are confident about. Avoid false positives.
                2. **Be Constructive**: Each issue must include a clear, actionable suggestion.
                3. **Be Thorough**: Consider performance, security, concurrency, error handling, and maintainability.
                4. **Be Contextual**: Consider the language and framework best practices.
                5. **Be Severity-Aware**: Clearly distinguish CRITICAL bugs from style suggestions.

                ## Output Format
                Respond in the following structured format:

                ### PR Summary
                [Brief bullet-point summary of what this PR changes]

                ### Risk Analysis
                [Each risk with level: CRITICAL / HIGH / MEDIUM / LOW]
                - **[LEVEL]**: Description → Impact → Suggestion

                ### Review Suggestions
                [Numbered list of specific, actionable suggestions with code examples]

                ### Overall Assessment
                [Final verdict and overall risk level, 2-3 sentences]
                """;
    }

    private String buildBuiltInUserPrompt(Map<String, String> variables) {
        return """
                ## Pull Request Review

                ### Files
                {{fileSummary}}

                ### Context
                {{projectContext}}

                ### Code Diff
                ```diff
                {{diff}}
                ```

                ### Related Code
                {{relatedCode}}

                ### Static Analysis Findings
                {{ruleFindings}}

                ### Commits
                {{commits}}

                Provide a thorough code review following the structured format.
                """;
    }
}
