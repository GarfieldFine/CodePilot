package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.github.model.PrFile;
import com.codepilot.semantic.SemanticAnalyzerFactory;
import com.codepilot.semantic.SemanticContext;
import com.codepilot.semantic.SemanticFinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Performs deep diff analysis beyond raw text: classifies changes by type,
 * identifies high-risk change patterns, computes change impact estimates,
 * and runs language-specific semantic analysis on changed files.
 */
@Slf4j
@Component
public class DiffAnalyzeAgent implements Agent {

    private final SemanticAnalyzerFactory semanticFactory;

    public DiffAnalyzeAgent(SemanticAnalyzerFactory semanticFactory) {
        this.semanticFactory = semanticFactory;
    }

    @Override
    public String getName() { return "DiffAnalyzeAgent"; }

    @Override
    public String getDescription() { return "Analyzing diff structure, classifying changes, semantic patterns and impact"; }

    @Override
    public int priority() { return 2; }

    @Override
    public AgentResult execute(AgentContext context) {
        List<PrFile> files = context.getPrInfo().getFiles();
        if (files == null || files.isEmpty()) {
            return AgentResult.success(getName(), "No files to analyze", Map.of());
        }

        Map<String, String> analysis = new LinkedHashMap<>();
        int newFiles = 0, modifiedFiles = 0, deletedFiles = 0;
        int totalAdditions = 0, totalDeletions = 0;
        List<String> highRiskFiles = new ArrayList<>();
        List<String> configChanges = new ArrayList<>();
        List<String> sqlChanges = new ArrayList<>();
        List<String> testChanges = new ArrayList<>();

        for (PrFile file : files) {
            String filename = file.getFilename();
            String status = file.getStatus();

            switch (status) {
                case "added" -> newFiles++;
                case "removed" -> deletedFiles++;
                default -> modifiedFiles++;
            }

            totalAdditions += file.getAdditions();
            totalDeletions += file.getDeletions();

            // Classify changes
            if (isConfigFile(filename)) configChanges.add(filename);
            if (isSqlFile(filename)) sqlChanges.add(filename);
            if (isTestFile(filename)) testChanges.add(filename);

            // Heuristic: large new files or heavily modified files are higher risk
            if (file.getAdditions() > 200 || (file.getAdditions() + file.getDeletions()) > 300) {
                highRiskFiles.add(filename + " (+" + file.getAdditions() + "/-" + file.getDeletions() + ")");
            }

            // File deletion is always noteworthy
            if ("removed".equals(status)) {
                highRiskFiles.add(filename + " (DELETED)");
            }
        }

        int changeScore = computeChangeScore(totalAdditions, totalDeletions, files.size());
        String changeCategory;
        if (changeScore <= 20) changeCategory = "Small (low risk)";
        else if (changeScore <= 50) changeCategory = "Medium (moderate review needed)";
        else if (changeScore <= 80) changeCategory = "Large (thorough review recommended)";
        else changeCategory = "Very Large (consider splitting PR)";

        analysis.put("totalFiles", String.valueOf(files.size()));
        analysis.put("newFiles", String.valueOf(newFiles));
        analysis.put("modifiedFiles", String.valueOf(modifiedFiles));
        analysis.put("deletedFiles", String.valueOf(deletedFiles));
        analysis.put("totalAdditions", String.valueOf(totalAdditions));
        analysis.put("totalDeletions", String.valueOf(totalDeletions));
        analysis.put("changeScore", String.valueOf(changeScore));
        analysis.put("changeCategory", changeCategory);
        analysis.put("highRiskFiles", String.join("; ", highRiskFiles));
        analysis.put("configChanges", String.join("; ", configChanges));
        analysis.put("sqlChanges", String.join("; ", sqlChanges));
        analysis.put("testChanges", testChanges.size() + " test files changed");

        // 4. Run language-specific semantic analysis
        SemanticContext semanticCtx = semanticFactory.analyzeAll(files);
        if (!semanticCtx.isEmpty()) {
            analysis.put("semanticFindingsCount", String.valueOf(semanticCtx.size()));
            analysis.put("semanticCriticalCount", String.valueOf(semanticCtx.countBySeverity("CRITICAL")));
            analysis.put("semanticHighCount", String.valueOf(semanticCtx.countBySeverity("HIGH")));

            // Add top semantic findings summary
            List<String> topFindings = new ArrayList<>();
            for (SemanticFinding f : semanticCtx.findHighRisk()) {
                topFindings.add("[" + f.getSeverity() + "] " + f.getFile() + ": " + f.getType());
                if (topFindings.size() >= 10) break;
            }
            analysis.put("topSemanticFindings", String.join("; ", topFindings));

            // Store in context for downstream agents
            context.put("semanticContext", semanticCtx);
        }

        context.setDiffAnalysis(analysis);

        String summary = String.format("PR change score: %d/100 (%s) — %d files, +%d/-%d, %d high-risk files",
                changeScore, changeCategory, files.size(), totalAdditions, totalDeletions, highRiskFiles.size());

        return AgentResult.success(getName(), summary, new HashMap<>(analysis));
    }

    private int computeChangeScore(int additions, int deletions, int files) {
        int total = additions + deletions;
        int score = 0;
        score += Math.min(total / 10, 40);   // up to 40 pts for volume
        score += Math.min(files * 3, 30);    // up to 30 pts for breadth
        if (additions > 500) score += 15;    // large addition
        if (deletions > 500) score += 15;    // large deletion
        return Math.min(score, 100);
    }

    private boolean isConfigFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".yml") || lower.endsWith(".yaml") ||
                lower.endsWith(".properties") || lower.endsWith(".xml") ||
                lower.endsWith(".conf") || lower.endsWith(".toml") ||
                lower.contains("application") || lower.contains("docker") ||
                lower.contains("nginx") || lower.contains("env");
    }

    private boolean isSqlFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".sql") || lower.contains("migration") ||
                lower.contains("schema") || lower.contains("ddl") || lower.contains("dml");
    }

    private boolean isTestFile(String name) {
        String lower = name.toLowerCase();
        return lower.contains("test") || lower.contains("spec") ||
                lower.endsWith(".test.ts") || lower.endsWith(".test.js") ||
                lower.endsWith(".spec.ts") || lower.endsWith(".spec.js");
    }
}
