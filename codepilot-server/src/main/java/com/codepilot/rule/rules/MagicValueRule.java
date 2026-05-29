package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MagicValueRule implements Rule {

    private static final Pattern MAGIC_NUMBER = Pattern.compile(
            "(?<![a-zA-Z_$])(?:if|while|for|return|case|assertEquals|assertThat)\\s*\\(?.*?(?<![\\w'\"])(\\d{2,})(?![\\w'\"]).*?(?:\\)|;)");

    private static final Pattern MAGIC_STRING = Pattern.compile(
            "\"(?!http|https|SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|ORDER BY|GET |POST |PUT |DELETE |PATCH )[a-z_]{3,30}\"");

    @Override
    public String getRuleName() {
        return "MAGIC_VALUE";
    }

    @Override
    public String getCategory() {
        return "CODE_QUALITY";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        String addedLines = extractAddedLines(content);

        Matcher matcher = MAGIC_NUMBER.matcher(addedLines);
        int magicCount = 0;
        StringBuilder snippets = new StringBuilder();

        while (matcher.find()) {
            String num = matcher.group(1);
            int value = Integer.parseInt(num);

            boolean isCommonValue = value == 0 || value == 1 || value == -1 || value == 2
                    || value == 100 || value == 200 || value == 404 || value == 500;

            if (!isCommonValue) {
                magicCount++;
                if (snippets.length() < 200) {
                    snippets.append(matcher.group().trim()).append("\n");
                }
            }
        }

        if (magicCount > 2) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.LOW)
                    .matched(true)
                    .message(String.format("检测到 %d 个魔法值（非标准常量），建议定义为具名常量", magicCount))
                    .suggestion("将魔法值定义为 private static final 常量或使用枚举，提高代码可读性和可维护性")
                    .codeSnippet(snippets.toString().trim())
                    .build();
        }

        return noMatch();
    }

    private boolean isJavaFile(String filename) {
        return filename != null && filename.endsWith(".java");
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

    private RuleResult noMatch() {
        return RuleResult.builder().matched(false).build();
    }
}
