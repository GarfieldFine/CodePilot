package com.codepilot.rule;

import com.codepilot.model.enums.RiskLevel;

import java.util.Map;

public interface Rule {

    String getRuleName();

    String getCategory();

    RiskLevel getDefaultRiskLevel();

    RuleResult check(String filepath, String content, Map<String, Object> context);

    default boolean isEnabled() {
        return true;
    }

    default int getPriority() {
        return 100;
    }
}
