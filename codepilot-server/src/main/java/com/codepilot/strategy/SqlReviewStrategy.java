package com.codepilot.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SqlReviewStrategy implements ReviewStrategy {

    @Override
    public String getName() { return "SQL"; }

    @Override
    public int getPriority() { return 40; }

    @Override
    public boolean supports(String language, List<String> frameworks) {
        if (language == null) return false;
        String lang = language.toLowerCase();
        return lang.contains("sql") || lang.contains("plsql") || lang.contains("pl/pgsql");
    }

    @Override
    public List<String> getFocusAreas() {
        return List.of(
                "Index Usage (missing indexes, sargable WHERE clauses, covering indexes)",
                "Lock Contention (table locks, row-level locking, deadlock risk)",
                "Migration Safety (backward compatibility, data integrity, rollback plan)",
                "Performance (SELECT *, large OFFSET, implicit conversions, Cartesian joins)",
                "Transaction Scope (transaction boundaries, isolation levels, long-running txns)",
                "Schema Design (normalization, column types, constraints, cascading deletes)",
                "Data Integrity (foreign keys, CHECK constraints, NOT NULL enforcement)",
                "Injection Prevention (parameterized queries, dynamic SQL safety)"
        );
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## SQL/Database Expertise Required
                - Check index usage: new queries must be covered by existing indexes or add new ones
                - Review migration safety: ALTER TABLE on large tables needs batching, NOT NULL with default
                - Validate lock patterns: explicit LOCK TABLE usage, SELECT ... FOR UPDATE scope
                - Examine query performance: avoid SELECT *, use LIMIT, watch for N+1 patterns
                - Check transaction boundaries: transactions should be as short as possible
                - Review schema changes: new columns should have safe defaults, dropped columns need deprecation period
                - Validate data integrity: proper foreign key constraints, check constraints for business rules
                - Prevent SQL injection: all dynamic SQL must use parameterization or proper escaping
                """;
    }
}
