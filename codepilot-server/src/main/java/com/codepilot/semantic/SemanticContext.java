package com.codepilot.semantic;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Container for all semantic findings across a PR analysis.
 * Provides grouped access by file and by severity level.
 */
public class SemanticContext {

    private final List<SemanticFinding> allFindings = new ArrayList<>();

    public void addFinding(SemanticFinding finding) {
        allFindings.add(finding);
    }

    public void addAll(List<SemanticFinding> findings) {
        allFindings.addAll(findings);
    }

    public List<SemanticFinding> getAllFindings() {
        return Collections.unmodifiableList(allFindings);
    }

    public Map<String, List<SemanticFinding>> byFile() {
        return allFindings.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getFile() != null ? f.getFile() : "unknown",
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    public Map<String, List<SemanticFinding>> bySeverity() {
        return allFindings.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getSeverity() != null ? f.getSeverity() : "LOW",
                        () -> new LinkedHashMap<>(),
                        Collectors.toList()));
    }

    public List<SemanticFinding> findHighRisk() {
        return allFindings.stream()
                .filter(f -> "CRITICAL".equalsIgnoreCase(f.getSeverity())
                        || "HIGH".equalsIgnoreCase(f.getSeverity()))
                .collect(Collectors.toList());
    }

    public int countBySeverity(String severity) {
        return (int) allFindings.stream()
                .filter(f -> severity.equalsIgnoreCase(f.getSeverity()))
                .count();
    }

    public boolean isEmpty() { return allFindings.isEmpty(); }
    public int size() { return allFindings.size(); }
}
