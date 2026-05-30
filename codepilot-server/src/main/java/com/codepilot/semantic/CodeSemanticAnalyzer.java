package com.codepilot.semantic;

import com.codepilot.github.model.PrFile;

import java.util.List;

/**
 * Language-specific semantic code analyzer.
 * Each implementation detects language-specific patterns and anti-patterns
 * by analyzing the diff/patch content of changed files.
 */
public interface CodeSemanticAnalyzer {

    String getName();

    boolean supports(String language);

    List<SemanticFinding> analyze(PrFile file);

    default int getPriority() { return 100; }
}
