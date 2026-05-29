package com.codepilot.review;

import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PrSplitter {

    private final int maxFilesPerChunk;
    private final int maxDiffLinesPerChunk;

    public PrSplitter(@Value("${review.max-files-per-chunk:10}") int maxFilesPerChunk,
                      @Value("${review.max-diff-lines-per-chunk:500}") int maxDiffLinesPerChunk) {
        this.maxFilesPerChunk = maxFilesPerChunk;
        this.maxDiffLinesPerChunk = maxDiffLinesPerChunk;
    }

    public List<PRAnalysisChunk> split(PrInfo prInfo) {
        List<PRAnalysisChunk> chunks = new ArrayList<>();
        List<PrFile> currentFiles = new ArrayList<>();
        int currentLines = 0;

        for (PrFile file : prInfo.getFiles()) {
            int fileLines = file.getPatch() != null ? countLines(file.getPatch()) : 0;

            if (currentFiles.size() >= maxFilesPerChunk || currentLines + fileLines > maxDiffLinesPerChunk) {
                if (!currentFiles.isEmpty()) {
                    chunks.add(new PRAnalysisChunk(
                            new ArrayList<>(currentFiles),
                            buildChunkDiff(currentFiles, prInfo)));
                    currentFiles.clear();
                    currentLines = 0;
                }
            }
            currentFiles.add(file);
            currentLines += fileLines;
        }

        if (!currentFiles.isEmpty()) {
            chunks.add(new PRAnalysisChunk(
                    new ArrayList<>(currentFiles),
                    buildChunkDiff(currentFiles, prInfo)));
        }

        return chunks;
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
