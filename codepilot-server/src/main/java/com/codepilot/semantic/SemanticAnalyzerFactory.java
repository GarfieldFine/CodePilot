package com.codepilot.semantic;

import com.codepilot.github.model.PrFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Factory that dispatches a file to the appropriate language-specific semantic analyzer.
 * Analyzers are auto-injected by Spring and matched by language support + priority.
 */
@Slf4j
@Component
public class SemanticAnalyzerFactory {

    private final List<CodeSemanticAnalyzer> analyzers;

    public SemanticAnalyzerFactory(List<CodeSemanticAnalyzer> analyzers) {
        this.analyzers = new ArrayList<>(analyzers);
        this.analyzers.sort(Comparator.comparingInt(CodeSemanticAnalyzer::getPriority));
        log.info("SemanticAnalyzerFactory initialized with {} analyzers", analyzers.size());
    }

    public List<SemanticFinding> analyze(PrFile file) {
        if (file == null || file.getLanguage() == null) return List.of();
        return analyzers.stream()
                .filter(a -> a.supports(file.getLanguage()))
                .findFirst()
                .map(a -> a.analyze(file))
                .orElse(List.of());
    }

    public SemanticContext analyzeAll(List<PrFile> files) {
        SemanticContext ctx = new SemanticContext();
        for (PrFile file : files) {
            ctx.addAll(analyze(file));
        }
        return ctx;
    }

    public List<CodeSemanticAnalyzer> getAnalyzers() {
        return Collections.unmodifiableList(analyzers);
    }
}
