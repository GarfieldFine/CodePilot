package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NullPointerRule implements Rule {

    private static final Pattern NPE_PATTERN = Pattern.compile(
            "(\\.get\\(|\\.getString\\(|\\.getInt\\(|\\.getLong\\(|\\.getBoolean\\(|\\.getDouble\\(|" +
                    "\\.getJSONObject\\(|\\.getJSONArray\\(|\\.getList\\(|\\.getMap\\()");

    @Override
    public String getRuleName() {
        return "NULL_POINTER_RISK";
    }

    @Override
    public String getCategory() {
        return "NULL_SAFETY";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        int nullChecks = countMatches(content, "if\\s*\\(.*\\s*(==|!=)\\s*null\\)");
        int optionalUses = countMatches(content, "Optional\\.ofNullable|Optional\\.empty|Optional\\.of");
        int nullableAnns = countMatches(content, "@Nullable|@NonNull|@NotNull");

        boolean hasMapGetWithoutCheck = false;
        Matcher matcher = NPE_PATTERN.matcher(content);
        int unsafeCalls = 0;
        int lineNum = 0;
        StringBuilder snippet = new StringBuilder();

        while (matcher.find()) {
            int start = Math.max(0, matcher.start() - 20);
            int end = Math.min(content.length(), matcher.end() + 30);
            String contextStr = content.substring(start, end);

            if (!contextStr.contains("null") && !contextStr.contains("Optional") && !contextStr.contains("@Nullable")) {
                unsafeCalls++;
                if (snippet.length() < 200) {
                    snippet.append(contextStr.trim()).append("\n");
                }
                if (lineNum == 0) {
                    lineNum = countLines(content, matcher.start());
                    hasMapGetWithoutCheck = true;
                }
            }
        }

        if (hasMapGetWithoutCheck && nullChecks < 2 && optionalUses < 1) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.HIGH)
                    .line(lineNum)
                    .matched(true)
                    .message(String.format("发现 %d 处潜在空指针调用，缺少 null 检查或 Optional 防护 (%d 处空检查, %d 处 Optional 使用)",
                            unsafeCalls, nullChecks, optionalUses))
                    .suggestion("建议使用 Optional、@Nullable/@NonNull 注解或显式 null 检查来防止空指针异常")
                    .codeSnippet(snippet.toString().trim())
                    .build();
        }

        if (nullChecks == 0 && optionalUses == 0 && nullableAnns == 0 && NPE_PATTERN.matcher(content).find()) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.MEDIUM)
                    .matched(true)
                    .message("代码中缺少 null 安全检查机制（无 @Nullable、Optional 或 null 检查）")
                    .suggestion("建议引入 null 安全策略，使用 Optional 或 IDE null-safety 注解")
                    .build();
        }

        return noMatch();
    }

    private boolean isJavaFile(String filename) {
        return filename != null && filename.endsWith(".java");
    }

    private int countMatches(String content, String regex) {
        Matcher m = Pattern.compile(regex).matcher(content);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private int countLines(String content, int pos) {
        int count = 1;
        for (int i = 0; i < pos && i < content.length(); i++) {
            if (content.charAt(i) == '\n') count++;
        }
        return count;
    }

    private RuleResult noMatch() {
        return RuleResult.builder().matched(false).build();
    }
}
