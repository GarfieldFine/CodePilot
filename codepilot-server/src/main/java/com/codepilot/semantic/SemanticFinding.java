package com.codepilot.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single semantic finding discovered by a language-specific analyzer.
 * Captures the pattern, its location, severity, and actionable guidance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticFinding {

    private String type;
    private String severity;   // CRITICAL, HIGH, MEDIUM, LOW
    private String file;
    private Integer line;
    private String pattern;
    private String description;
    private String suggestion;
    private String language;
    private String source;     // analyzer name

    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW;

        public static Severity fromString(String s) {
            if (s == null) return LOW;
            try { return valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return LOW; }
        }
    }
}
