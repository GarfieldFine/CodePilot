package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.AiProviderFactory;
import com.codepilot.ai.prompting.PromptBuilder;
import com.codepilot.chunk.ChunkAnalyzer;
import com.codepilot.chunk.ChunkAnalyzer.ChunkResult;
import com.codepilot.chunk.ChunkSplitter;
import com.codepilot.chunk.ContextCompressor;
import com.codepilot.review.DiffContextBuilder;
import com.codepilot.review.PRAnalysisChunk;
import com.codepilot.rule.RuleResult;
import com.codepilot.strategy.ReviewStrategy;
import com.codepilot.strategy.ReviewStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates structured AI review for each PR chunk using concurrent analysis.
 *
 * Uses ChunkSplitter for smart type-aware chunking and ChunkAnalyzer for
 * parallel AI invocation across chunks.
 */
@Slf4j
@Component
public class ReviewGenerateAgent implements Agent {

    private final AiProviderFactory aiProviderFactory;
    private final PromptBuilder promptBuilder;
    private final ChunkSplitter chunkSplitter;
    private final ChunkAnalyzer chunkAnalyzer;
    private final ContextCompressor compressor;
    private final DiffContextBuilder contextBuilder;
    private final ReviewStrategyFactory strategyFactory;

    public ReviewGenerateAgent(AiProviderFactory aiProviderFactory,
                               PromptBuilder promptBuilder,
                               ChunkSplitter chunkSplitter,
                               ChunkAnalyzer chunkAnalyzer,
                               ContextCompressor compressor,
                               DiffContextBuilder contextBuilder,
                               ReviewStrategyFactory strategyFactory) {
        this.aiProviderFactory = aiProviderFactory;
        this.promptBuilder = promptBuilder;
        this.chunkSplitter = chunkSplitter;
        this.chunkAnalyzer = chunkAnalyzer;
        this.compressor = compressor;
        this.contextBuilder = contextBuilder;
        this.strategyFactory = strategyFactory;
    }

    @Override
    public String getName() { return "ReviewGenerateAgent"; }

    @Override
    public String getDescription() { return "Generating AI code review with smart chunking and concurrent analysis"; }

    @Override
    public int priority() { return 20; }

    @Override
    public AgentResult execute(AgentContext context) {
        AiProvider aiProvider = aiProviderFactory.getProvider(context.getProviderName());
        List<PRAnalysisChunk> chunks = chunkSplitter.split(context.getPrInfo());

        log.info("{}: Reviewing {} chunks with provider {} (concurrent)",
                getName(), chunks.size(), aiProvider.getProviderName());

        String primaryLanguage = context.getLanguages().isEmpty() ? "Java" : context.getLanguages().get(0);

        // Build system prompt once (shared across chunks)
        String systemPrompt = buildEnhancedSystemPrompt(primaryLanguage, context);

        // Analyze all chunks concurrently
        List<ChunkResult> chunkResults = chunkAnalyzer.analyze(chunks, chunk -> {
            String chunkLang = detectChunkLanguage(chunk);

            Map<String, String> fileContexts = contextBuilder.buildContext(
                    chunk.files(), Collections.emptyMap());

            // Compress contexts if too large
            Map<String, String> compressed = compressor.compressContexts(fileContexts, 3000);

            List<RuleResult> chunkRules = context.getRuleResults().stream()
                    .filter(r -> chunk.files().stream()
                            .anyMatch(f -> f.getFilename().equals(r.getFile())))
                    .collect(Collectors.toList());

            String userPrompt = buildEnhancedUserPrompt(chunk, compressed, chunkRules,
                    context, chunk, chunks.size());

            return aiProvider.chat(systemPrompt, userPrompt);
        });

        // Collect successful reviews
        List<String> chunkReviews = new ArrayList<>();
        for (ChunkResult r : chunkResults) {
            chunkReviews.add(r.output());
        }
        context.setChunkReviews(chunkReviews);

        // Store chunk metadata for SummaryMergeAgent
        Map<String, Object> chunkMeta = new LinkedHashMap<>();
        chunkMeta.put("totalChunks", chunks.size());
        chunkMeta.put("successfulChunks", chunkResults.stream().filter(ChunkResult::isSuccess).count());
        chunkMeta.put("failedChunks", chunkResults.stream().filter(r -> !r.isSuccess()).count());
        chunkMeta.put("totalTokens", chunks.stream().mapToInt(chunkSplitter::estimateChunkTokens).sum());
        chunkMeta.put("totalDurationMs", chunkResults.stream().mapToLong(ChunkResult::durationMs).sum());
        context.put("chunkMeta", chunkMeta);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("chunkCount", chunks.size());
        output.put("totalReviewLength", chunkReviews.stream().mapToInt(String::length).sum());
        output.put("chunkMeta", chunkMeta);

        String summary = String.format("Generated AI review across %d chunks (%d/%d succeeded, %d total chars)",
                chunks.size(),
                chunkMeta.get("successfulChunks"),
                chunkMeta.get("totalChunks"),
                output.get("totalReviewLength"));

        return AgentResult.success(getName(), summary, output);
    }

    private String buildEnhancedSystemPrompt(String language, AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(promptBuilder.buildSystemPrompt(language));
        sb.append("\n\n## Project Context (Auto-Detected)\n");
        sb.append("- **Project Type:** ").append(context.getProjectType()).append("\n");
        sb.append("- **Frameworks:** ").append(String.join(", ", context.getFrameworks())).append("\n");
        sb.append("- **Languages:** ").append(String.join(", ", context.getLanguages())).append("\n");

        ReviewStrategy strategy = strategyFactory.findStrategy(language, context.getFrameworks());
        if (strategy != null && !"Default".equals(strategy.getName())) {
            sb.append("\n").append(strategy.getSystemPromptExtension());
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
                                           PRAnalysisChunk fullChunk,
                                           int totalChunks) {
        String language = detectChunkLanguage(chunk);

        StringBuilder sb = new StringBuilder();
        sb.append("## Pull Request Review Chunk\n\n");

        sb.append("**PR Title:** ").append(context.getPrInfo().getTitle()).append("\n");
        sb.append("**Files in chunk:** ").append(chunk.files().size()).append("\n");

        // File summary
        sb.append("### Files\n");
        sb.append(compressor.summarizeChunk(chunk.files(), 15));
        sb.append("\n");

        // Repository context
        sb.append("### Context\n");
        sb.append("Project: ").append(context.getProjectType())
                .append(" | Language: ").append(language)
                .append(" | Frameworks: ").append(String.join(", ", context.getFrameworks()))
                .append("\n\n");

        // Code diff (compressed)
        sb.append("### Code Diff\n```diff\n");
        sb.append(compressor.compressDiff(fullChunk.diff(), 5000));
        sb.append("\n```\n\n");

        // Related context
        if (!fileContexts.isEmpty()) {
            sb.append("### Related Code\n```").append(language).append("\n");
            String ctx = String.join("\n\n", fileContexts.values());
            sb.append(truncate(ctx, 4000));
            sb.append("\n```\n\n");
        }

        // Rule findings for reference
        if (!chunkRules.isEmpty()) {
            sb.append("### Static Analysis Findings\n");
            for (RuleResult r : chunkRules) {
                sb.append("- [").append(r.getRiskLevel()).append("] ").append(r.getRuleName())
                        .append(": ").append(r.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        // Commit messages
        sb.append("### Commits\n");
        sb.append(truncate(formatCommits(context), 1500));
        sb.append("\n\n");

        sb.append("Provide a thorough code review following the structured format.");
        return sb.toString();
    }

    private String detectChunkLanguage(PRAnalysisChunk chunk) {
        if (chunk.files().isEmpty()) return "Java";
        return chunk.files().get(0).getLanguage();
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
