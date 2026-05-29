package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.strategy.ReviewStrategy;
import com.codepilot.strategy.ReviewStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds enriched semantic context for the PR by combining repository profile,
 * diff analysis, and code-level semantics. Uses ReviewStrategyFactory for
 * dynamic, language-aware focus area selection.
 */
@Slf4j
@Component
public class ContextBuildAgent implements Agent {

    private final ReviewStrategyFactory strategyFactory;

    public ContextBuildAgent(ReviewStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

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

        // 2. Determine review focus areas using dynamic strategy
        List<String> focusAreas = strategyFactory.collectAllFocusAreas(languages, frameworks);
        semanticContext.put("focusAreas", String.join(", ", focusAreas));

        // Determine active strategy names for logging
        List<String> activeStrategies = new ArrayList<>();
        for (String lang : languages) {
            ReviewStrategy strategy = strategyFactory.findStrategy(lang, frameworks);
            if (!"Default".equals(strategy.getName()) && !activeStrategies.contains(strategy.getName())) {
                activeStrategies.add(strategy.getName());
            }
        }
        if (!activeStrategies.isEmpty()) {
            semanticContext.put("activeStrategies", String.join(", ", activeStrategies));
        }

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

}
