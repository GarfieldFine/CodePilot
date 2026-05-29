package com.codepilot.model.enums;

import lombok.Getter;

@Getter
public enum RiskLevel {
    LOW(1, "低风险"),
    MEDIUM(2, "中风险"),
    HIGH(3, "高风险"),
    CRITICAL(4, "严重风险");

    private final int level;
    private final String label;

    RiskLevel(int level, String label) {
        this.level = level;
        this.label = label;
    }
}
