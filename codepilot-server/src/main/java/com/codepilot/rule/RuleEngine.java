package com.codepilot.rule;

import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import com.codepilot.model.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules.stream()
                .filter(Rule::isEnabled)
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .collect(Collectors.toList());
        log.info("Loaded {} risk rules: {}", this.rules.size(),
                this.rules.stream().map(Rule::getRuleName).collect(Collectors.toList()));
    }

    public List<RuleResult> analyze(PrInfo prInfo) {
        List<RuleResult> allResults = new ArrayList<>();
        Map<String, Object> context = buildContext(prInfo);

        for (PrFile file : prInfo.getFiles()) {
            if (file.getPatch() == null) continue;
            String content = file.getPatch();

            for (Rule rule : rules) {
                try {
                    RuleResult result = rule.check(file.getFilename(), content, context);
                    if (result.isMatched()) {
                        result.setFile(file.getFilename());
                        allResults.add(result);
                    }
                } catch (Exception e) {
                    log.warn("Rule {} failed on file {}: {}",
                            rule.getRuleName(), file.getFilename(), e.getMessage());
                }
            }
        }

        // Also check the full diff for cross-file patterns
        String fullDiff = prInfo.getDiffContent();
        if (fullDiff != null) {
            for (Rule rule : rules) {
                try {
                    RuleResult result = rule.check("__full_diff__", fullDiff, context);
                    if (result.isMatched()) {
                        allResults.add(result);
                    }
                } catch (Exception e) {
                    log.warn("Rule {} failed on full diff: {}", rule.getRuleName(), e.getMessage());
                }
            }
        }

        log.info("Rule engine completed: {} findings across {} files",
                allResults.size(), prInfo.getFiles().size());
        return allResults;
    }

    public Map<RiskLevel, List<RuleResult>> groupByRiskLevel(List<RuleResult> results) {
        return results.stream()
                .collect(Collectors.groupingBy(RuleResult::getRiskLevel));
    }

    public RiskLevel getHighestRiskLevel(List<RuleResult> results) {
        return results.stream()
                .map(RuleResult::getRiskLevel)
                .max(Comparator.comparingInt(RiskLevel::getLevel))
                .orElse(RiskLevel.LOW);
    }

    private Map<String, Object> buildContext(PrInfo prInfo) {
        Map<String, Object> context = new HashMap<>();
        context.put("prTitle", prInfo.getTitle());
        context.put("prDescription", prInfo.getDescription());
        context.put("changedFiles", prInfo.getChangedFiles());
        context.put("additions", prInfo.getAdditions());
        context.put("deletions", prInfo.getDeletions());
        context.put("author", prInfo.getAuthor());
        context.put("baseBranch", prInfo.getBaseBranch());

        List<String> fileNames = prInfo.getFiles().stream()
                .map(PrFile::getFilename)
                .collect(Collectors.toList());
        context.put("fileNames", fileNames);
        return context;
    }
}
