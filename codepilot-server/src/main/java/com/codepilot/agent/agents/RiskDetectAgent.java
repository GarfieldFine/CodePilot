package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleEngine;
import com.codepilot.rule.RuleResult;
import com.codepilot.semantic.SemanticContext;
import com.codepilot.semantic.SemanticFinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Detects risks by combining the static rule engine with semantic context.
 *
 * Two modes:
 * - RULE mode (fast, no AI): runs the existing RuleEngine against the PR
 * - SEMANTIC mode (context-aware): applies heuristic checks based on repository context
 */
@Slf4j
@Component
public class RiskDetectAgent implements Agent {

    private final RuleEngine ruleEngine;

    public RiskDetectAgent(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @Override
    public String getName() { return "RiskDetectAgent"; }

    @Override
    public String getDescription() { return "Detecting risks using rule engine and semantic analysis"; }

    @Override
    public int priority() { return 10; }

    @Override
    public AgentResult execute(AgentContext context) {
        // 1. Run static rule engine
        List<RuleResult> ruleResults = ruleEngine.analyze(context.getPrInfo());
        context.setRuleResults(ruleResults);

        // 2. Apply semantic risk heuristics based on repository context
        List<Map<String, Object>> semanticRisks = detectSemanticRisks(context);

        // 3. Merge language-specific semantic analysis findings
        SemanticContext semanticCtx = context.get("semanticContext");
        if (semanticCtx != null && !semanticCtx.isEmpty()) {
            for (SemanticFinding sf : semanticCtx.getAllFindings()) {
                Map<String, Object> risk = new LinkedHashMap<>();
                risk.put("code", sf.getType());
                risk.put("level", sf.getSeverity());
                risk.put("description", sf.getDescription());
                risk.put("suggestion", sf.getSuggestion());
                risk.put("file", sf.getFile());
                risk.put("source", "semantic:" + sf.getSource());
                semanticRisks.add(risk);
            }
        }
        context.setAiRiskFindings(semanticRisks);

        // 4. Combine and summarize
        long criticalCount = ruleResults.stream().filter(r -> r.getRiskLevel() == RiskLevel.CRITICAL).count();
        long highCount = ruleResults.stream().filter(r -> r.getRiskLevel() == RiskLevel.HIGH).count();
        long mediumCount = ruleResults.stream().filter(r -> r.getRiskLevel() == RiskLevel.MEDIUM).count();
        long lowCount = ruleResults.stream().filter(r -> r.getRiskLevel() == RiskLevel.LOW).count();

        // Count by category
        Map<String, Long> byCategory = ruleResults.stream()
                .filter(RuleResult::isMatched)
                .collect(Collectors.groupingBy(RuleResult::getCategory, Collectors.counting()));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("totalFindings", ruleResults.size());
        output.put("criticalCount", criticalCount);
        output.put("highCount", highCount);
        output.put("mediumCount", mediumCount);
        output.put("lowCount", lowCount);
        output.put("byCategory", byCategory);
        output.put("semanticRisks", semanticRisks);
        output.put("overallRiskLevel", determineOverallRisk(
                criticalCount, highCount, mediumCount, lowCount, semanticRisks.size()));

        String summary = String.format("Risk detection complete: %d findings (%d critical, %d high, %d medium, %d low) + %d semantic risks",
                ruleResults.size(), criticalCount, highCount, mediumCount, lowCount, semanticRisks.size());

        log.info("{}: critical={}, high={}, medium={}, low={}, semantic={}",
                getName(), criticalCount, highCount, mediumCount, lowCount, semanticRisks.size());

        return AgentResult.success(getName(), summary, output);
    }

    private List<Map<String, Object>> detectSemanticRisks(AgentContext context) {
        List<Map<String, Object>> risks = new ArrayList<>();
        Map<String, String> diffAnalysis = context.getDiffAnalysis();
        Map<String, String> semanticContext = context.getSemanticContext();
        List<String> frameworks = context.getFrameworks();

        // Risk: Large PR without tests
        if (diffAnalysis != null) {
            String testChanges = diffAnalysis.get("testChanges");
            String changeCategory = diffAnalysis.get("changeCategory");
            if (testChanges != null && testChanges.contains("0 test") &&
                    changeCategory != null && !changeCategory.startsWith("Small")) {
                risks.add(buildSemanticRisk("NO_TEST_COVERAGE", RiskLevel.MEDIUM,
                        "No test files were changed in this PR. " +
                                "For a " + changeCategory + " change, verify that test coverage is adequate.",
                        "Add or update unit/integration tests covering the changed logic."));
            }
        }

        // Risk: Configuration changes without documentation
        if (diffAnalysis != null) {
            String configChanges = diffAnalysis.get("configChanges");
            if (configChanges != null && !configChanges.isEmpty() && configChanges.length() > 3) {
                risks.add(buildSemanticRisk("CONFIG_CHANGE", RiskLevel.MEDIUM,
                        "Configuration files changed: " + configChanges + ". " +
                                "Verify environment-specific configs are properly separated.",
                        "Confirm that production configuration values are safe and not accidentally exposed."));
            }
        }

        // Risk: SQL changes in Spring Boot without migration tool
        if (diffAnalysis != null) {
            String sqlChanges = diffAnalysis.get("sqlChanges");
            if (sqlChanges != null && !sqlChanges.isEmpty() && sqlChanges.length() > 3) {
                boolean hasMigration = frameworks.stream().anyMatch(f -> f.contains("Flyway") || f.contains("Liquibase"));
                if (!hasMigration) {
                    risks.add(buildSemanticRisk("SQL_WITHOUT_MIGRATION", RiskLevel.HIGH,
                            "SQL changes detected without a migration framework (Flyway/Liquibase) in the project. " +
                                    "Manual SQL changes risk environment inconsistency.",
                            "Consider using Flyway or Liquibase for versioned database migrations."));
                }
            }
        }

        // Risk: High concurrency changes in Java project
        if (context.getLanguages().contains("Java")) {
            String diff = context.getPrInfo().getDiffContent();
            if (diff != null) {
                boolean hasSyncBlock = diff.contains("synchronized") || diff.contains("ReentrantLock") ||
                        diff.contains("CountDownLatch") || diff.contains("Semaphore");
                boolean hasThreadPool = diff.contains("ThreadPoolExecutor") || diff.contains("ExecutorService") ||
                        diff.contains("@Async") || diff.contains("CompletableFuture");
                if (hasSyncBlock || hasThreadPool) {
                    risks.add(buildSemanticRisk("CONCURRENCY_CHANGE", RiskLevel.HIGH,
                            "Concurrency-related code changes detected (locks, thread pools, or async operations). " +
                                    "Verify thread safety, deadlock risks, and proper resource cleanup.",
                            "Review all synchronized blocks, ensure try-finally for lock release, " +
                                    "and verify thread pool lifecycle management."));
                }
            }
        }

        return risks;
    }

    private Map<String, Object> buildSemanticRisk(String code, RiskLevel level, String description, String suggestion) {
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("code", code);
        risk.put("level", level.name());
        risk.put("description", description);
        risk.put("suggestion", suggestion);
        risk.put("source", "semantic");
        return risk;
    }

    private String determineOverallRisk(long critical, long high, long medium, long low, int semanticCount) {
        int score = (int)(critical * 40 + high * 25 + medium * 10 + low * 3 + semanticCount * 15);
        if (score >= 80) return "CRITICAL";
        if (score >= 50) return "HIGH";
        if (score >= 20) return "MEDIUM";
        return "LOW";
    }
}
