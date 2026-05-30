package com.codepilot.persona;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FrontendReviewer implements ReviewerPersona {

    @Override
    public String getName() { return "Frontend Reviewer"; }

    @Override
    public String getDescription() { return "Frontend UI/UX and component architecture specialist (Vue/React/TypeScript)"; }

    @Override
    public int getPriority() { return 25; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        if (language == null) return false;
        String lang = language.toLowerCase();
        return lang.contains("typescript") || lang.contains("javascript")
                || lang.contains("tsx") || lang.contains("jsx")
                || lang.contains("vue") || lang.contains("css")
                || lang.contains("scss") || lang.contains("html");
    }

    @Override
    public boolean shouldActivate(List<String> riskDimensions) {
        // Frontend reviewer activates when frontend files detected
        return true;
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Frontend Reviewer Persona Active
                You are reviewing this code as an experienced **frontend engineer**. Focus on component architecture, rendering performance, and user experience quality.

                ### Frontend Checklist
                - **Component Architecture**: Single responsibility, proper composition over inheritance, appropriate component size. Watch for god components with too many responsibilities.
                - **State Management**: Appropriate state scope (local vs shared vs global), proper immutable updates, selector efficiency. Watch for stale closures and race conditions in async state.
                - **Reactivity (Vue)**: Proper ref/reactive usage, correct watch/computed selection, avoiding mutation outside reactive system. Watch for lost reactivity when destructuring props.
                - **Hooks (React)**: Correct dependency arrays, hook ordering rules, no hooks in conditionals. Watch for missing cleanup in useEffect and useLayoutEffect timing issues.
                - **Rendering Performance**: Unnecessary re-renders, missing memo/useMemo/React.memo, large component trees re-rendering on every keystroke. Watch for derived state recalculations.
                - **Side Effects**: Proper cleanup of subscriptions, event listeners, timers, and async operations. Watch for memory leaks from unmounted component state updates.
                - **Accessibility**: Semantic HTML, ARIA labels, keyboard navigation, focus management, screen reader compatibility, color contrast.
                - **Security**: XSS via v-html/dangerouslySetInnerHTML, CSRF token handling, secure cookie attributes, Content Security Policy compatibility.
                - **Bundle Impact**: Large dependencies, missing tree shaking, unoptimized images, missing code splitting, inefficient imports.

                ### Output Requirements for Frontend Findings
                - Classify each finding as: rendering / reactivity / security / accessibility / bundle-size
                - For performance issues, describe the user-visible impact (jank, layout shift, input lag)
                - For reactivity issues, explain the execution order that causes the bug
                """;
    }
}
