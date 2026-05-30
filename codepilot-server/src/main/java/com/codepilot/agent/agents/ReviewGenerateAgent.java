package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.AiProviderFactory;
import com.codepilot.chunk.ChunkAnalyzer;
import com.codepilot.chunk.ChunkAnalyzer.ChunkResult;
import com.codepilot.chunk.ChunkSplitter;
import com.codepilot.chunk.ContextCompressor;
import com.codepilot.prompt.PersonaPromptBuilder;
import com.codepilot.review.DiffContextBuilder;
import com.codepilot.review.PRAnalysisChunk;
import com.codepilot.rule.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates structured AI review using persona-enriched prompts and concurrent chunk analysis.
 *
 * PersonaPromptBuilder selects appropriate reviewer personas based on detected languages,
 * frameworks, and risk dimensions. ChunkAnalyzer runs AI calls concurrently.
 */
@Slf4j
@Component
public class ReviewGenerateAgent implements Agent {

    private final AiProviderFactory aiProviderFactory;
    private final ChunkSplitter chunkSplitter;
    private final ChunkAnalyzer chunkAnalyzer;
    private final ContextCompressor compressor;
    private final DiffContextBuilder contextBuilder;
    private final PersonaPromptBuilder personaPromptBuilder;

    public ReviewGenerateAgent(AiProviderFactory aiProviderFactory,
                               ChunkSplitter chunkSplitter,
                               ChunkAnalyzer chunkAnalyzer,
                               ContextCompressor compressor,
                               DiffContextBuilder contextBuilder,
                               PersonaPromptBuilder personaPromptBuilder) {
        this.aiProviderFactory = aiProviderFactory;
        this.chunkSplitter = chunkSplitter;
        this.chunkAnalyzer = chunkAnalyzer;
        this.compressor = compressor;
        this.contextBuilder = contextBuilder;
        this.personaPromptBuilder = personaPromptBuilder;
    }

    @Override
    public String getName() { return "ReviewGenerateAgent"; }

    @Override
    public String getDescription() { return "Generating AI code review with persona-enriched prompts and concurrent analysis"; }

    @Override
    public int priority() { return 20; }

    @Override
    public AgentResult execute(AgentContext context) {
        AiProvider aiProvider = aiProviderFactory.getProvider(context.getProviderName());
        List<PRAnalysisChunk> chunks = chunkSplitter.split(context.getPrInfo());

        log.info("{}: Reviewing {} chunks with provider {} (persona-enriched)",
                getName(), chunks.size(), aiProvider.getProviderName());

        String primaryLanguage = context.getLanguages().isEmpty() ? "Java" : context.getLanguages().get(0);

        // Extract risk dimensions from diff analysis
        List<String> riskDimensions = extractRiskDimensions(context);

        // Build persona-enriched system prompt once (shared across chunks)
        String systemPrompt = personaPromptBuilder.buildSystemPrompt(
                primaryLanguage,
                context.getLanguages(),
                context.getFrameworks(),
                context.getProjectType(),
                riskDimensions);

        // Store active persona names in context for downstream agents
        context.put("personaSystemPrompt", systemPrompt);

        // Analyze all chunks concurrently
        List<ChunkResult> chunkResults = chunkAnalyzer.analyze(chunks, chunk -> {
            String chunkLang = detectChunkLanguage(chunk);

            Map<String, String> fileContexts = contextBuilder.buildContext(
                    chunk.files(), Collections.emptyMap());
            Map<String, String> compressed = compressor.compressContexts(fileContexts, 3000);

            List<RuleResult> chunkRules = context.getRuleResults().stream()
                    .filter(r -> chunk.files().stream()
                            .anyMatch(f -> f.getFilename().equals(r.getFile())))
                    .collect(Collectors.toList());

            // Build user prompt using externalized template
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("fileSummary", compressor.summarizeChunk(chunk.files(), 15));
            vars.put("projectContext", context.getProjectType()
                    + " | Language: " + chunkLang
                    + " | Frameworks: " + String.join(", ", context.getFrameworks()));
            vars.put("diff", compressor.compressDiff(chunk.diff(), 5000));
            vars.put("relatedCode", compressed.isEmpty() ? "No additional context" :
                    truncate(String.join("\n\n", compressed.values()), 4000));
            vars.put("ruleFindings", formatRuleFindings(chunkRules));
            vars.put("commits", formatCommits(context));

            String userPrompt = personaPromptBuilder.buildUserPrompt(vars);

            return aiProvider.chat(systemPrompt, userPrompt);
        });

        // Collect reviews
        List<String> chunkReviews = new ArrayList<>();
        for (ChunkResult r : chunkResults) {
            chunkReviews.add(r.output());
        }
        context.setChunkReviews(chunkReviews);

        // Chunk metadata
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

    private List<String> extractRiskDimensions(AgentContext context) {
        List<String> dimensions = new ArrayList<>();
        Map<String, String> diffAnalysis = context.getDiffAnalysis();
        if (diffAnalysis == null) return dimensions;

        String configChanges = diffAnalysis.get("configChanges");
        if (configChanges != null && !configChanges.isEmpty() && configChanges.length() > 3) {
            dimensions.add("Configuration changes detected");
        }
        String sqlChanges = diffAnalysis.get("sqlChanges");
        if (sqlChanges != null && !sqlChanges.isEmpty() && sqlChanges.length() > 3) {
            dimensions.add("SQL/Database changes detected");
        }
        String testChanges = diffAnalysis.get("testChanges");
        if (testChanges != null && testChanges.contains("0 test")) {
            dimensions.add("No test coverage for changes");
        }
        String highRiskFiles = diffAnalysis.get("highRiskFiles");
        if (highRiskFiles != null && !highRiskFiles.isEmpty()) {
            dimensions.add("Large or high-risk file changes");
        }
        return dimensions;
    }

    private String detectChunkLanguage(PRAnalysisChunk chunk) {
        if (chunk.files().isEmpty()) return "Java";
        return chunk.files().get(0).getLanguage();
    }

    private String formatRuleFindings(List<RuleResult> chunkRules) {
        if (chunkRules.isEmpty()) return "No static analysis findings for this chunk.";
        StringBuilder sb = new StringBuilder();
        for (RuleResult r : chunkRules) {
            sb.append("- [").append(r.getRiskLevel()).append("] ").append(r.getRuleName())
                    .append(": ").append(r.getMessage()).append("\n");
        }
        return sb.toString();
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
