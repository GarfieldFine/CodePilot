package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlInjectionRule implements Rule {

    private static final Pattern STRING_CONCAT_SQL = Pattern.compile(
            "(String\\.format|\\+|StringBuilder|StringBuffer|concat)\\s*.*\\b(SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|DROP|CREATE)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DYNAMIC_SQL = Pattern.compile(
            "\"\\s*\\+\\s*\\w+\\s*\\+\\s*\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PREPARED_STMT = Pattern.compile(
            "PreparedStatement|prepareStatement|#\\{|:param|@Param|@Bind");

    @Override
    public String getRuleName() {
        return "SQL_INJECTION_RISK";
    }

    @Override
    public String getCategory() {
        return "SECURITY";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.CRITICAL;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isRelevantFile(filepath)) return noMatch();

        String addedLines = extractAddedLines(content);

        Matcher sqlConcat = STRING_CONCAT_SQL.matcher(addedLines);
        Matcher dynamicSql = DYNAMIC_SQL.matcher(addedLines);
        Matcher prepared = PREPARED_STMT.matcher(content);

        boolean hasSqlConcat = sqlConcat.find();
        boolean hasDynamicSql = dynamicSql.find();
        boolean hasPrepared = prepared.find();

        if (hasSqlConcat && !hasPrepared) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.CRITICAL)
                    .matched(true)
                    .message("检测到 SQL 字符串拼接，存在 SQL 注入风险。未检测到 PreparedStatement 或参数绑定")
                    .suggestion("使用 PreparedStatement、MyBatis #{} 参数绑定或 JPA Criteria API，避免字符串拼接 SQL")
                    .codeSnippet(extractSnippet(content, sqlConcat.start()))
                    .build();
        }

        if (hasDynamicSql && !hasPrepared) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.HIGH)
                    .matched(true)
                    .message("检测到动态 SQL 拼接模式，可能存在 SQL 注入风险")
                    .suggestion("使用参数化查询或 MyBatis 动态 SQL 标签替代字符串拼接")
                    .codeSnippet(extractSnippet(content, dynamicSql.start()))
                    .build();
        }

        return noMatch();
    }

    private boolean isRelevantFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".java") && (lower.contains("mapper") || lower.contains("dao")
                || lower.contains("repository") || lower.contains("sql"));
    }

    private String extractAddedLines(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                sb.append(line.substring(1)).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractSnippet(String content, int pos) {
        int start = Math.max(0, pos - 30);
        int end = Math.min(content.length(), pos + 100);
        return content.substring(start, end).trim();
    }

    private RuleResult noMatch() {
        return RuleResult.builder().matched(false).build();
    }
}
