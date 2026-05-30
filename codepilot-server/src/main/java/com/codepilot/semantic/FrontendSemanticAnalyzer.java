package com.codepilot.semantic;

import com.codepilot.github.model.PrFile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FrontendSemanticAnalyzer implements CodeSemanticAnalyzer {

    private static final Pattern USE_EFFECT_PATTERN = Pattern.compile("useEffect\\s*\\(\\s*\\(\\s*\\)\\s*=>\\s*\\{");
    private static final Pattern USE_EFFECT_DEPS = Pattern.compile("useEffect\\s*\\([^)]+,\\s*\\[");
    private static final Pattern USE_STATE_PATTERN = Pattern.compile("useState\\s*\\(");
    private static final Pattern USE_MEMO_PATTERN = Pattern.compile("useMemo\\s*\\(|useCallback\\s*\\(");
    private static final Pattern DANGEROUS_HTML = Pattern.compile("dangerouslySetInnerHTML|v-html");
    private static final Pattern ADD_EVENT_LISTENER = Pattern.compile("\\.addEventListener\\s*\\(");
    private static final Pattern REMOVE_EVENT_LISTENER = Pattern.compile("\\.removeEventListener\\s*\\(");
    private static final Pattern SET_INTERVAL = Pattern.compile("setInterval\\s*\\(");
    private static final Pattern SET_TIMEOUT = Pattern.compile("setTimeout\\s*\\(");
    private static final Pattern DIRECT_DOM = Pattern.compile("document\\.(getElementById|querySelector|createElement|write)");
    private static final Pattern CONSOLE_LOG = Pattern.compile("console\\.(log|warn|error|debug)\\s*\\(");
    private static final Pattern INLINE_STYLE = Pattern.compile("style\\s*=\\s*\\{\\{");
    private static final Pattern FORCE_UPDATE = Pattern.compile("\\$forceUpdate\\(\\)");
    private static final Pattern WATCH_DEEP = Pattern.compile("watch\\s*\\([^,]+,\\s*[^,]+,\\s*\\{\\s*deep\\s*:");
    private static final Pattern ANY_TYPE = Pattern.compile(":\\s*any\\b");
    private static final Pattern TS_IGNORE = Pattern.compile("@ts-ignore|@ts-nocheck");

    @Override public String getName() { return "Frontend"; }
    @Override public int getPriority() { return 20; }

    @Override
    public boolean supports(String language) {
        if (language == null) return false;
        String lang = language.toLowerCase();
        return lang.contains("typescript") || lang.contains("javascript")
                || lang.contains("tsx") || lang.contains("jsx")
                || lang.contains("vue") || lang.contains("react");
    }

    @Override
    public List<SemanticFinding> analyze(PrFile file) {
        String patch = file.getPatch();
        if (patch == null || patch.isEmpty()) return List.of();

        List<SemanticFinding> findings = new ArrayList<>();

        checkEffectHooks(patch, file.getFilename(), findings);
        checkSecurityPatterns(patch, file.getFilename(), findings);
        checkEventListenerCleanup(patch, file.getFilename(), findings);
        checkTimerUsage(patch, file.getFilename(), findings);
        checkAntiPatterns(patch, file.getFilename(), findings);

        return findings;
    }

    private void checkEffectHooks(String patch, String filename, List<SemanticFinding> findings) {
        if (USE_EFFECT_PATTERN.matcher(patch).find() && !USE_EFFECT_DEPS.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("MISSING_EFFECT_DEPS")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("useEffect without deps array")
                    .description("useEffect without dependency array will run on every render. This is usually unintentional and can cause performance issues or infinite loops.")
                    .suggestion("Add a dependency array [] (run once) or list specific dependencies. Use the react-hooks/exhaustive-deps ESLint rule.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }

        if (USE_STATE_PATTERN.matcher(patch).find()) {
            int count = countMatches(patch, USE_STATE_PATTERN);
            if (count > 5) {
                findings.add(SemanticFinding.builder()
                        .type("EXCESSIVE_STATE")
                        .severity("LOW")
                        .file(filename)
                        .pattern("Many useState calls")
                        .description("Component has " + count + " useState calls. Consider using useReducer for complex state or extracting sub-components.")
                        .suggestion("Evaluate if useReducer would simplify state management. Extract related state into custom hooks.")
                        .language("TypeScript")
                        .source(getName())
                        .build());
            }
        }
    }

    private void checkSecurityPatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (DANGEROUS_HTML.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("XSS_RISK")
                    .severity("CRITICAL")
                    .file(filename)
                    .pattern("dangerouslySetInnerHTML / v-html")
                    .description("Direct HTML injection detected. This is a primary XSS attack vector if user-controlled data reaches this code path.")
                    .suggestion("Sanitize HTML with DOMPurify before rendering. Prefer text content over HTML. If v-html is needed, ensure the content is trusted server-generated HTML only.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }
    }

    private void checkEventListenerCleanup(String patch, String filename, List<SemanticFinding> findings) {
        if (ADD_EVENT_LISTENER.matcher(patch).find() && !REMOVE_EVENT_LISTENER.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("MISSING_EVENT_CLEANUP")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("addEventListener without removeEventListener")
                    .description("addEventListener used without corresponding removeEventListener. This causes memory leaks when components unmount.")
                    .suggestion("Return a cleanup function from useEffect that calls removeEventListener with the same function reference.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }
    }

    private void checkTimerUsage(String patch, String filename, List<SemanticFinding> findings) {
        if (SET_INTERVAL.matcher(patch).find() && !patch.contains("clearInterval")) {
            findings.add(SemanticFinding.builder()
                    .type("MISSING_INTERVAL_CLEANUP")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("setInterval without clearInterval")
                    .description("setInterval used without clearInterval. The interval will continue running after component unmount, causing memory leaks and stale state updates.")
                    .suggestion("Store interval ID with useRef and call clearInterval in a useEffect cleanup function.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }

        if (SET_TIMEOUT.matcher(patch).find() && !patch.contains("clearTimeout")) {
            findings.add(SemanticFinding.builder()
                    .type("MISSING_TIMEOUT_CLEANUP")
                    .severity("LOW")
                    .file(filename)
                    .pattern("setTimeout without clearTimeout")
                    .description("setTimeout used without corresponding clearTimeout in cleanup. May cause state updates on unmounted components.")
                    .suggestion("Store timeout ID with useRef and call clearTimeout in a useEffect cleanup return function.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }
    }

    private void checkAntiPatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (DIRECT_DOM.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("DIRECT_DOM_MANIPULATION")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("Direct DOM manipulation")
                    .description("Direct DOM manipulation in React/Vue — bypasses the virtual DOM and can cause inconsistencies.")
                    .suggestion("Use refs (useRef in React, ref in Vue) instead of document.querySelector. For styling, use state/class bindings.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }

        if (CONSOLE_LOG.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("CONSOLE_LOG_LEFT")
                    .severity("LOW")
                    .file(filename)
                    .pattern("console.log/warn/error")
                    .description("Console log statement detected. These should be removed before merging to production.")
                    .suggestion("Remove console.log statements. For error logging, use a proper logging service like Sentry.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }

        if (ANY_TYPE.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("ANY_TYPE_USAGE")
                    .severity("LOW")
                    .file(filename)
                    .pattern(": any type")
                    .description("TypeScript 'any' type usage detected. This defeats type checking and can hide type-related bugs.")
                    .suggestion("Replace 'any' with the proper type, unknown, or a generic constraint.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }

        if (TS_IGNORE.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("TS_IGNORE")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("@ts-ignore / @ts-nocheck")
                    .description("@ts-ignore or @ts-nocheck directive detected. This suppresses TypeScript errors and can hide real bugs.")
                    .suggestion("Fix the underlying type error instead of suppressing it. If truly needed, add a comment explaining why.")
                    .language("TypeScript")
                    .source(getName())
                    .build());
        }

        if (FORCE_UPDATE.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("FORCE_UPDATE")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("$forceUpdate()")
                    .description("Vue $forceUpdate() detected. This is typically a sign of a reactivity issue — the component should re-render automatically when data changes.")
                    .suggestion("Ensure data is properly declared in data() or reactive(). Use Vue.set() or reassign reactive properties.")
                    .language("Vue")
                    .source(getName())
                    .build());
        }
    }

    private int countMatches(String source, Pattern pattern) {
        int count = 0;
        Matcher m = pattern.matcher(source);
        while (m.find()) count++;
        return count;
    }
}
