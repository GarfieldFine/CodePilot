package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds enriched semantic context for the PR by combining repository profile,
 * diff analysis, and code-level semantics. This context is used by downstream
 * agents (RiskDetect, ReviewGenerate) to produce more informed reviews.
 */
@Slf4j
@Component
public class ContextBuildAgent implements Agent {

    @Override
    public String getName() { return "ContextBuildAgent"; }

    @Override
    public String getDescription() { return "Building enriched semantic context for review"; }

    @Override
    public int priority() { return 3; }

    @Override
    public AgentResult execute(AgentContext context) {
        Map<String, String> semanticContext = new LinkedHashMap<>();

        // 1. Build language-specific context
        List<String> languages = context.getLanguages();
        List<String> frameworks = context.getFrameworks();
        String projectType = context.getProjectType();
        Map<String, String> diffAnalysis = context.getDiffAnalysis();

        semanticContext.put("projectType", projectType != null ? projectType : "Unknown");
        semanticContext.put("languages", String.join(", ", languages));
        semanticContext.put("frameworks", String.join(", ", frameworks));

        // 2. Determine review focus areas based on technology stack
        List<String> focusAreas = determineFocusAreas(languages, frameworks);
        semanticContext.put("focusAreas", String.join(", ", focusAreas));

        // 3. Determine risk profile from diff analysis
        if (diffAnalysis != null) {
            semanticContext.put("changeCategory", diffAnalysis.getOrDefault("changeCategory", "Unknown"));
            semanticContext.put("changeScore", diffAnalysis.getOrDefault("changeScore", "0"));

            // Identify risk dimensions based on what files changed
            List<String> riskDimensions = new ArrayList<>();
            String configChanges = diffAnalysis.get("configChanges");
            if (configChanges != null && !configChanges.isEmpty() && !configChanges.equals("0 config changes")) {
                riskDimensions.add("Configuration changes detected - verify environment compatibility");
            }
            String sqlChanges = diffAnalysis.get("sqlChanges");
            if (sqlChanges != null && !sqlChanges.isEmpty() && !sqlChanges.equals("0 SQL changes")) {
                riskDimensions.add("Database schema/migration changes - verify backward compatibility");
            }
            String testChanges = diffAnalysis.get("testChanges");
            if (testChanges != null && testChanges.contains("0 test")) {
                riskDimensions.add("No test files changed - verify test coverage for changes");
            }
            semanticContext.put("riskDimensions", String.join("; ", riskDimensions));
        }

        // 4. Add module-level context
        Map<String, Object> repoContext = context.getRepositoryContext();
        if (repoContext != null && repoContext.containsKey("modules")) {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> modules = (Map<String, List<String>>) repoContext.get("modules");
            List<String> affectedModules = new ArrayList<>(modules.keySet());
            semanticContext.put("affectedModules", String.join(", ", affectedModules));
            semanticContext.put("moduleCount", String.valueOf(affectedModules.size()));
        }

        // 5. Store context in shared state
        context.setSemanticContext(semanticContext);

        int contextSize = semanticContext.size();
        String summary = String.format("Built semantic context: %s project, %s, focus areas: %s",
                projectType,
                String.join("/", languages),
                String.join(", ", focusAreas));

        return AgentResult.success(getName(), summary, new HashMap<>(semanticContext));
    }

    private List<String> determineFocusAreas(List<String> languages, List<String> frameworks) {
        List<String> areas = new ArrayList<>();
        String langStr = String.join(",", languages).toLowerCase();
        String fwStr = String.join(",", frameworks).toLowerCase();

        if (langStr.contains("java") || fwStr.contains("spring")) {
            areas.add("Concurrency Safety (thread pools, synchronized, locks)");
            areas.add("Transaction Management (@Transactional scope, isolation levels)");
            areas.add("Spring Context (bean scopes, circular dependencies)");
            areas.add("SQL/ORM (N+1 queries, indexing, batch operations)");
            areas.add("Redis/Cache (key design, consistency, eviction)");
        }
        if (langStr.contains("python")) {
            areas.add("Type Safety (type hints, None handling)");
            areas.add("Exception Handling (try-except granularity)");
            areas.add("Dependency Management (requirements.txt changes)");
        }
        if (langStr.contains("go")) {
            areas.add("Goroutine Lifecycle (leaks, context cancellation)");
            areas.add("Channel Usage (deadlocks, unbuffered vs buffered)");
            areas.add("Error Handling (error wrapping, sentinel errors)");
        }
        if (langStr.contains("typescript") || langStr.contains("javascript") || langStr.contains("vue") || langStr.contains("react")) {
            areas.add("Component Architecture (props drilling, coupling)");
            areas.add("State Management (hooks dependencies, render loops)");
            areas.add("Performance (memo, useMemo, lazy loading)");
            areas.add("Bundle Impact (large dependencies, tree-shaking)");
        }
        if (fwStr.contains("mybatis")) {
            areas.add("SQL Injection (${} vs #{} in MyBatis)");
            areas.add("Mapper XML correctness");
        }

        if (areas.isEmpty()) {
            areas.add("Code Quality (naming, structure, duplication)");
            areas.add("Error Handling (exception propagation, logging)");
            areas.add("Testing (coverage, edge cases)");
        }

        return areas;
    }
}
