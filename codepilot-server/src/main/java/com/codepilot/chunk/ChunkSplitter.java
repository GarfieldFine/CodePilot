package com.codepilot.chunk;

import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import com.codepilot.review.PRAnalysisChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Enhanced PR splitter that groups files by semantic type and estimates token usage.
 *
 * Improvements over the legacy PrSplitter:
 * - Groups files by ChunkType (config together, SQL together, etc.)
 * - Estimates tokens per chunk for smarter size decisions
 * - Orders chunks by review priority (config first, tests last)
 */
@Slf4j
@Component
public class ChunkSplitter {

    private final int maxFilesPerChunk;
    private final int maxDiffLinesPerChunk;

    public ChunkSplitter(@Value("${review.max-files-per-chunk:10}") int maxFilesPerChunk,
                         @Value("${review.max-diff-lines-per-chunk:500}") int maxDiffLinesPerChunk) {
        this.maxFilesPerChunk = maxFilesPerChunk;
        this.maxDiffLinesPerChunk = maxDiffLinesPerChunk;
    }

    /**
     * Split PR files into smart chunks grouped by file type, ordered by review priority.
     */
    public List<PRAnalysisChunk> split(PrInfo prInfo) {
        if (prInfo.getFiles() == null || prInfo.getFiles().isEmpty()) {
            return List.of();
        }

        // 1. Classify and group files by ChunkType
        Map<ChunkType, List<PrFile>> grouped = new LinkedHashMap<>();
        for (PrFile file : prInfo.getFiles()) {
            ChunkType type = ChunkType.classify(file.getFilename());
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(file);
        }

        // 2. Sort groups by review priority
        List<Map.Entry<ChunkType, List<PrFile>>> sorted = new ArrayList<>(grouped.entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getKey().getPriority()));

        // 3. Split each group into size-limited chunks
        List<PRAnalysisChunk> chunks = new ArrayList<>();
        for (var entry : sorted) {
            ChunkType type = entry.getKey();
            List<PrFile> files = entry.getValue();
            int groupIdx = 0;

            int i = 0;
            while (i < files.size()) {
                List<PrFile> currentBatch = new ArrayList<>();
                int currentLines = 0;

                while (i < files.size() && currentBatch.size() < maxFilesPerChunk) {
                    PrFile f = files.get(i);
                    int fileLines = countLines(f.getPatch());
                    if (currentLines + fileLines > maxDiffLinesPerChunk && !currentBatch.isEmpty()) {
                        break;
                    }
                    currentBatch.add(f);
                    currentLines += fileLines;
                    i++;
                }

                if (!currentBatch.isEmpty()) {
                    groupIdx++;
                    String label = type.name() + (files.size() > maxFilesPerChunk ? "-" + groupIdx : "");
                    chunks.add(new PRAnalysisChunk(
                            new ArrayList<>(currentBatch),
                            buildChunkDiff(currentBatch, prInfo)));
                    log.debug("Chunk [{}]: {} files, ~{} lines, type={}", label, currentBatch.size(), currentLines, type);
                }
            }
        }

        log.info("Smart split: {} files → {} chunks (grouped by type with priority ordering)",
                prInfo.getFiles().size(), chunks.size());

        return chunks;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // Rough estimate: ~4 chars per token for code
        return text.length() / 4;
    }

    public int estimateChunkTokens(PRAnalysisChunk chunk) {
        int tokens = estimateTokens(chunk.diff());
        for (PrFile f : chunk.files()) {
            tokens += estimateTokens(f.getPatch());
        }
        return tokens;
    }

    private String buildChunkDiff(List<PrFile> files, PrInfo prInfo) {
        StringBuilder sb = new StringBuilder();
        String fullDiff = prInfo.getDiffContent();
        if (fullDiff == null) {
            for (PrFile f : files) {
                if (f.getPatch() != null) {
                    sb.append("diff --git a/").append(f.getFilename()).append(" b/").append(f.getFilename()).append("\n");
                    sb.append("--- a/").append(f.getFilename()).append("\n");
                    sb.append("+++ b/").append(f.getFilename()).append("\n");
                    sb.append(f.getPatch()).append("\n");
                }
            }
        } else {
            for (PrFile f : files) {
                String section = extractDiffSection(fullDiff, f.getFilename());
                if (section != null) {
                    sb.append(section).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String extractDiffSection(String fullDiff, String filename) {
        int idx = fullDiff.indexOf("diff --git a/" + filename);
        if (idx < 0) return null;
        int nextIdx = fullDiff.indexOf("diff --git ", idx + 1);
        if (nextIdx < 0) {
            return fullDiff.substring(idx);
        }
        return fullDiff.substring(idx, nextIdx);
    }

    private int countLines(String text) {
        if (text == null) return 0;
        int count = 1;
        for (char c : text.toCharArray()) {
            if (c == '\n') count++;
        }
        return count;
    }
}
