package com.codepilot.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FrontendReviewStrategy implements ReviewStrategy {

    @Override
    public String getName() { return "Frontend"; }

    @Override
    public int getPriority() { return 15; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        if (language == null) return false;
        String lang = language.toLowerCase();
        if (lang.contains("typescript") || lang.contains("javascript") || lang.contains("tsx")
                || lang.contains("jsx") || lang.contains("vue") || lang.contains("css")
                || lang.contains("scss") || lang.contains("html")) return true;
        String fwStr = String.join(",", frameworks).toLowerCase();
        return fwStr.contains("react") || fwStr.contains("vue") || fwStr.contains("angular")
                || fwStr.contains("next") || fwStr.contains("nuxt");
    }

    @Override
    public List<String> getFocusAreas() {
        return List.of(
                "Component Architecture (props drilling, coupling, composition vs inheritance)",
                "State Management (hooks dependencies, render loops, stale closures)",
                "Performance (useMemo/useCallback necessity, bundle size, code splitting)",
                "Reactivity (Vue reactive/watch/computed correctness, React state immutability)",
                "Side Effects (useEffect cleanup, event listener removal, subscription management)",
                "Accessibility (ARIA labels, keyboard navigation, semantic HTML)",
                "Security (XSS prevention, CSP compliance, input sanitization)",
                "Bundle Impact (large dependencies, tree-shaking, dynamic imports)"
        );
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Frontend Expertise Required
                - Check component structure: single responsibility, avoid excessive props drilling
                - Review hooks/effects: verify useEffect dependency arrays, watch for infinite render loops
                - Validate state updates: ensure immutability, proper batching, no direct mutation
                - Examine performance: unnecessary re-renders, missing useMemo/useCallback on expensive computations
                - Check for memory leaks: missing useEffect cleanup, unresolved subscriptions/timers
                - Review accessibility: missing alt text, non-semantic HTML, keyboard navigation gaps
                - Validate security: avoid dangerouslySetInnerHTML/v-html with user input, sanitize data
                - Check CSS/SCSS: avoid deep nesting, watch specificity wars, use CSS modules/scoped styles
                """;
    }
}
