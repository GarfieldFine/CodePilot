package com.codepilot.chunk;

import com.codepilot.github.model.PrFile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Compresses code context to fit within token budgets.
 *
 * Strategies:
 * - Diff compaction: keep change lines, reduce context lines
 * - File summarization: one-line per-file summaries
 * - Token-aware truncation at hunk boundaries
 */
@Component
public class ContextCompressor {

    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Compress a diff to fit within a token budget.
     * Keeps all +/- lines (actual changes), limits context lines.
     */
    public String compressDiff(String diff, int maxTokens) {
        if (diff == null || diff.isEmpty()) return "";
        if (estimateTokens(diff) <= maxTokens) return diff;

        StringBuilder result = new StringBuilder();
        int budget = maxTokens;
        int contextLinesSinceLastChange = 0;
        int maxContextGap = 3;

        for (String line : diff.split("\n", -1)) {
            boolean isChangeLine = line.startsWith("+") || line.startsWith("-");
            boolean isHunkHeader = line.startsWith("@@");
            boolean isFileHeader = line.startsWith("diff --git") || line.startsWith("---") || line.startsWith("+++");

            if (isFileHeader || isHunkHeader) {
                result.append(line).append("\n");
                budget -= line.length() / CHARS_PER_TOKEN + 1;
                contextLinesSinceLastChange = 0;
                continue;
            }

            if (isChangeLine) {
                result.append(line).append("\n");
                budget -= line.length() / CHARS_PER_TOKEN + 1;
                contextLinesSinceLastChange = 0;
            } else {
                if (contextLinesSinceLastChange < maxContextGap) {
                    result.append(line).append("\n");
                    budget -= line.length() / CHARS_PER_TOKEN + 1;
                    contextLinesSinceLastChange++;
                }
            }

            if (budget <= 0) {
                result.append("... (diff truncated, ").append(estimateTokens(diff) - maxTokens)
                        .append(" estimated tokens omitted)\n");
                break;
            }
        }

        return result.toString();
    }

    /**
     * Generate a one-line summary for a file.
     */
    public String summarizeFile(PrFile file) {
        if (file == null) return "";
        String name = file.getFilename();
        String status = file.getStatus();
        int adds = file.getAdditions();
        int dels = file.getDeletions();
        ChunkType type = ChunkType.classify(name);

        return String.format("[%s] %s %s (+%d/-%d)", type, status, name, adds, dels);
    }

    /**
     * Generate a bullet list of file summaries for a chunk.
     */
    public String summarizeChunk(List<PrFile> files, int maxLines) {
        if (files == null || files.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (PrFile f : files) {
            if (shown >= maxLines) {
                sb.append("... and ").append(files.size() - shown).append(" more files\n");
                break;
            }
            sb.append("- ").append(summarizeFile(f)).append("\n");
            shown++;
        }
        return sb.toString();
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / CHARS_PER_TOKEN;
    }

    /**
     * Compress a map of file contexts, prioritizing more relevant files.
     */
    public Map<String, String> compressContexts(Map<String, String> contexts, int maxTotalTokens) {
        if (contexts == null || contexts.isEmpty()) return contexts;
        Map<String, String> result = new LinkedHashMap<>();
        int budget = maxTotalTokens;
        int perFileBudget = maxTotalTokens / Math.max(contexts.size(), 1);

        for (var entry : contexts.entrySet()) {
            String compressed = compressDiff(entry.getValue(), Math.min(perFileBudget, budget));
            result.put(entry.getKey(), compressed);
            budget -= estimateTokens(compressed);
            if (budget <= 0) break;
        }

        return result;
    }
}
