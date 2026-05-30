package com.codepilot.repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured repository profile produced by RepositoryAnalyzer.
 * Contains detected languages, frameworks, project type, and module structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryContext {
    private List<String> languages;
    private List<String> frameworks;
    private String projectType;
    private int totalFiles;
    private Map<String, Integer> extensionDistribution;
    private Map<String, List<String>> modules;
    private Map<String, String> buildFiles;       // detected build files -> content summary
    private Map<String, String> metadata;         // additional metadata (readme, license, etc.)
    private Double confidence;                    // detection confidence (0.0-1.0)

    public boolean isBackend() {
        return "Backend".equals(projectType) || "Fullstack".equals(projectType);
    }

    public boolean isFrontend() {
        return "Frontend".equals(projectType) || "Fullstack".equals(projectType);
    }

    public boolean hasLanguage(String language) {
        return languages != null && languages.stream()
                .anyMatch(l -> l.equalsIgnoreCase(language));
    }

    public boolean hasFramework(String keyword) {
        return frameworks != null && frameworks.stream()
                .anyMatch(f -> f.toLowerCase().contains(keyword.toLowerCase()));
    }

    public String getPrimaryLanguage() {
        return languages != null && !languages.isEmpty() ? languages.get(0) : "Unknown";
    }
}
