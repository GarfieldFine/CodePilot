package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.AiProviderFactory;
import com.codepilot.ai.model.AiReviewRequest;
import com.codepilot.ai.prompting.PromptBuilder;
import com.codepilot.review.DiffContextBuilder;
import com.codepilot.review.PRAnalysisChunk;
import com.codepilot.review.PrSplitter;
import com.codepilot.rule.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates structured AI review for each PR chunk, enriched with semantic context.
 *
 * This agent is the primary AI interaction point — it builds enhanced prompts using
 * repository context, diff analysis, risk findings, and semantic focus areas.
 */
@Slf4j
@Component
public class ReviewGenerateAgent implements Agent {

    private final AiProviderFactory aiProviderFactory;
    private final PromptBuilder promptBuilder;
    private final PrSplitter prSplitter;
    private final DiffContextBuilder contextBuilder;

    public ReviewGenerateAgent(AiProviderFactory aiProviderFactory,
                               PromptBuilder promptBuilder,
                               PrSplitter prSplitter,
                               DiffContextBuilder contextBuilder) {
        this.aiProviderFactory = aiProviderFactory;
        this.promptBuilder = promptBuilder;
        this.prSplitter = prSplitter;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public String getName() { return "ReviewGenerateAgent"; }

    @Override
    public String getDescription() { return "Generating AI code review with semantic context"; }

    @Override
    public int priority() { return 20; }

    @Override
    public AgentResult execute(AgentContext context) {
        AiProvider aiProvider = aiProviderFactory.getProvider(context.getProviderName());
        List<PRAnalysisChunk> chunks = prSplitter.split(context.getPrInfo());
        List<String> chunkReviews = Collections.synchronizedList(new ArrayList<>());

        log.info("{}: Reviewing {} chunks with provider {}", getName(), chunks.size(), aiProvider.getProviderName());

        String primaryLanguage = context.getLanguages().isEmpty() ? "Java" : context.getLanguages().get(0);
        List<String> focusAreas = getFocusAreasFromContext(context);

        for (int i = 0; i < chunks.size(); i++) {
            PRAnalysisChunk chunk = chunks.get(i);
            String chunkLang = detectChunkLanguage(chunk);

            Map<String, String> fileContexts = contextBuilder.buildContext(
                    chunk.files(), Collections.emptyMap());

            List<RuleResult> chunkRules = context.getRuleResults().stream()
                    .filter(r -> chunk.files().stream()
                            .anyMatch(f -> f.getFilename().equals(r.getFile())))
                    .collect(Collectors.toList());

            // Build enhanced prompt with all agent context
            String systemPrompt = buildEnhancedSystemPrompt(primaryLanguage, focusAreas, context);
            String userPrompt = buildEnhancedUserPrompt(chunk, fileContexts, chunkRules, context, i + 1, chunks.size());

            try {
                String aiResponse = aiProvider.chat(systemPrompt, userPrompt);
                chunkReviews.add(aiResponse);
                log.info("{}: Chunk {}/{} completed ({} chars)", getName(), i + 1, chunks.size(), aiResponse.length());
            } catch (Exception e) {
                log.error("{}: Chunk {}/{} failed: {}", getName(), i + 1, chunks.size(), e.getMessage());
                chunkReviews.add("[AI Review failed for chunk " + (i + 1) + ": " + e.getMessage() + "]");
            }
        }

        context.setChunkReviews(chunkReviews);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("chunkCount", chunks.size());
        output.put("totalReviewLength", chunkReviews.stream().mapToInt(String::length).sum());

        String summary = String.format("Generated AI review across %d chunks (%d total chars)",
                chunks.size(), output.get("totalReviewLength"));

        return AgentResult.success(getName(), summary, output);
    }

    private String buildEnhancedSystemPrompt(String language, List<String> focusAreas, AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(promptBuilder.buildSystemPrompt(language));
        sb.append("\n\n## Project Context (Auto-Detected)\n");
        sb.append("- **Project Type:** ").append(context.getProjectType()).append("\n");
        sb.append("- **Frameworks:** ").append(String.join(", ", context.getFrameworks())).append("\n");
        sb.append("- **Languages:** ").append(String.join(", ", context.getLanguages())).append("\n");

        if (!focusAreas.isEmpty()) {
            sb.append("\n## Critical Focus Areas for This Review\n");
            for (String area : focusAreas) {
                sb.append("- ").append(area).append("\n");
            }
        }

        sb.append("\n## Explainability Requirement\n");
        sb.append("For every issue you flag, you MUST include:\n");
        sb.append("- **Why:** Explain why this is a problem (root cause reasoning)\n");
        sb.append("- **Evidence:** Cite specific code lines or patterns as evidence\n");
        sb.append("- **Impact:** Describe the potential impact if this issue goes to production\n");
        sb.append("- **Suggestion:** Provide a concrete, actionable fix with code example where appropriate\n");

        sb.append("\nDo NOT use vague phrases like 'code can be improved' or 'consider refactoring' without specifics.\n");

        return sb.toString();
    }

    private String buildEnhancedUserPrompt(PRAnalysisChunk chunk,
                                           Map<String, String> fileContexts,
                                           List<RuleResult> chunkRules,
                                           AgentContext context,
                                           int chunkIndex,
                                           int totalChunks) {
        // Start with standard review template enriched with agent context
        String language = detectChunkLanguage(chunk);

        StringBuilder sb = new StringBuilder();
        sb.append("## Pull Request Review (Chunk ").append(chunkIndex).append("/").append(totalChunks).append(")\n\n");

        sb.append("**PR Title:** ").append(context.getPrInfo().getTitle()).append("\n");
        sb.append("**Changed Files in this chunk:** ").append(chunk.files().size()).append("\n\n");

        // Add repository context summary
        sb.append("### Repository Context\n");
        sb.append("Project: ").append(context.getProjectType())
                .append(" | Language: ").append(language)
                .append(" | Frameworks: ").append(String.join(", ", context.getFrameworks()))
                .append("\n\n");

        // Add semantic focus areas
        Map<String, String> semCtx = context.getSemanticContext();
        if (semCtx != null && semCtx.containsKey("focusAreas")) {
            sb.append("### Review Focus Areas\n").append(semCtx.get("focusAreas")).append("\n\n");
        }

        // Code diff
        sb.append("### Code Diff\n```diff\n");
        sb.append(truncate(chunk.diff(), 6000));
        sb.append("\n```\n\n");

        // Related context
        if (!fileContexts.isEmpty()) {
            sb.append("### Related Code Context\n```").append(language).append("\n");
            sb.append(truncate(String.join("\n\n", fileContexts.values()), 4000));
            sb.append("\n```\n\n");
        }

        // Rule findings for this chunk
        if (!chunkRules.isEmpty()) {
            sb.append("### Static Analysis Findings (for reference)\n");
            for (RuleResult r : chunkRules) {
                sb.append("- [").append(r.getRiskLevel()).append("] ").append(r.getRuleName())
                        .append(": ").append(r.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        // Commit messages
        sb.append("### Commit Messages\n");
        sb.append(truncate(formatCommits(context), 1500));
        sb.append("\n\n");

        sb.append("Provide a thorough code review following the structured format.");
        return sb.toString();
    }

    private String detectChunkLanguage(PRAnalysisChunk chunk) {
        if (chunk.files().isEmpty()) return "Java";
        return chunk.files().get(0).getLanguage();
    }

    private List<String> getFocusAreasFromContext(AgentContext context) {
        Map<String, String> semCtx = context.getSemanticContext();
        if (semCtx != null && semCtx.containsKey("focusAreas")) {
            return Arrays.asList(semCtx.get("focusAreas").split(", "));
        }
        return List.of();
    }

    private String formatCommits(AgentContext context) {
        if (context.getPrInfo().getCommits() == null || context.getPrInfo().getCommits().isEmpty()) return "N/A";
        StringBuilder sb = new StringBuilder();
        for (var c : context.getPrInfo().getCommits()) {
            sb.append("- ").append(c.getMessage()).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
