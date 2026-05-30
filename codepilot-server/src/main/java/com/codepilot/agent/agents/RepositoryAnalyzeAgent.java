package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.repository.RepositoryAnalyzer;
import com.codepilot.repository.RepositoryContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Analyzes the repository structure to detect languages, frameworks, and project type.
 * Delegates to the RepositoryAnalyzer service for the actual analysis logic.
 */
@Slf4j
@Component
public class RepositoryAnalyzeAgent implements Agent {

    private final RepositoryAnalyzer repositoryAnalyzer;

    public RepositoryAnalyzeAgent(RepositoryAnalyzer repositoryAnalyzer) {
        this.repositoryAnalyzer = repositoryAnalyzer;
    }

    @Override
    public String getName() { return "RepositoryAnalyzeAgent"; }

    @Override
    public String getDescription() { return "Analyzing repository structure, detecting languages and frameworks"; }

    @Override
    public int priority() { return 1; }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context.getPrInfo().getFiles() == null || context.getPrInfo().getFiles().isEmpty()) {
            return AgentResult.success(getName(), "No files to analyze", Map.of());
        }

        // Delegate to the dedicated repository analysis service
        RepositoryContext repoCtx = repositoryAnalyzer.analyze(context.getPrInfo());

        // Populate agent context
        context.setLanguages(repoCtx.getLanguages());
        context.setFrameworks(repoCtx.getFrameworks());
        context.setProjectType(repoCtx.getProjectType());

        Map<String, Object> repoProfile = new LinkedHashMap<>();
        repoProfile.put("languages", repoCtx.getLanguages());
        repoProfile.put("frameworks", repoCtx.getFrameworks());
        repoProfile.put("projectType", repoCtx.getProjectType());
        repoProfile.put("modules", repoCtx.getModules());
        repoProfile.put("totalFiles", repoCtx.getTotalFiles());
        repoProfile.put("extensionDistribution", repoCtx.getExtensionDistribution());
        repoProfile.put("buildFiles", repoCtx.getBuildFiles());
        repoProfile.put("confidence", repoCtx.getConfidence());

        context.setRepositoryContext(repoProfile);

        String summary = String.format("Detected %s project (%s) with %d files across %d modules (confidence: %.0f%%)",
                repoCtx.getProjectType(),
                String.join(", ", repoCtx.getLanguages()),
                repoCtx.getTotalFiles(),
                repoCtx.getModules().size(),
                (repoCtx.getConfidence() != null ? repoCtx.getConfidence() * 100 : 0));

        log.info("{}: {}", getName(), summary);
        return AgentResult.success(getName(), summary, repoProfile);
    }
}
