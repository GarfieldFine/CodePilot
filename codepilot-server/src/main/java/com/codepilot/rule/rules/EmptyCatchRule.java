package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EmptyCatchRule implements Rule {

    private static final Pattern EMPTY_CATCH = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{\\s*(\\}\\s*catch\\s*\\([^)]*\\)\\s*\\{)?\\s*\\}",
            Pattern.DOTALL);

    private static final Pattern EMPTY_CATCH_MULTI = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{(?:\\s*//.*)?\\s*\\}");

    @Override
    public String getRuleName() {
        return "EMPTY_CATCH";
    }

    @Override
    public String getCategory() {
        return "EXCEPTION_HANDLING";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        Matcher matcher = EMPTY_CATCH_MULTI.matcher(content);
        int count = 0;
        StringBuilder snippets = new StringBuilder();

        while (matcher.find()) {
            String match = matcher.group();
            boolean hasComment = match.contains("//") || match.contains("/*");
            boolean hasLog = match.contains("log.") || match.contains("logger.");

            if (!hasLog && !hasComment) {
                count++;
                if (snippets.length() < 200) {
                    snippets.append(match.trim()).append("\n");
                }
            }
        }

        if (count > 0) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.HIGH)
                    .matched(true)
                    .message(String.format("检测到 %d 个空 catch 块，异常被吞没未处理", count))
                    .suggestion("至少记录日志 log.error(\"...\", e)，根据业务场景决定是否需要重新抛出或降级处理")
                    .codeSnippet(snippets.toString().trim())
                    .build();
        }

        return noMatch();
    }

    private boolean isJavaFile(String filename) {
        return filename != null && filename.endsWith(".java");
    }

    private RuleResult noMatch() {
        return RuleResult.builder().matched(false).build();
    }
}
