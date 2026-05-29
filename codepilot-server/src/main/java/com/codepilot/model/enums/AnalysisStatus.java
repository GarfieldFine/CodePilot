package com.codepilot.model.enums;

public enum AnalysisStatus {
    PENDING,
    FETCHING_PR,
    ANALYZING_DIFF,
    BUILDING_CONTEXT,
    RUNNING_RULES,
    AI_REVIEWING,
    CALCULATING_SCORE,
    COMPLETED,
    FAILED
}
