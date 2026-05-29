package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ThreadLocalLeakRule implements Rule {

    private static final Pattern THREADLOCAL = Pattern.compile("ThreadLocal<[^>]*>");
    private static final Pattern REMOVE_CALL = Pattern.compile("\\.remove\\(\\)");
    private static final Pattern TRY_FINALLY = Pattern.compile("try\\s*\\{[^}]*\\}\\s*finally\\s*\\{");

    @Override
    public String getRuleName() {
        return "THREADLOCAL_LEAK";
    }

    @Override
    public String getCategory() {
        return "MEMORY_LEAK";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public int getPriority() {
        return 8;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        Matcher tlMatcher = THREADLOCAL.matcher(content);
        int tlCount = 0;
        while (tlMatcher.find()) tlCount++;

        if (tlCount == 0) return noMatch();

        int removeCount = 0;
        Matcher rmMatcher = REMOVE_CALL.matcher(content);
        while (rmMatcher.find()) removeCount++;

        Matcher tfMatcher = TRY_FINALLY.matcher(content);
        int tryFinallyCount = 0;
        while (tfMatcher.find()) tryFinallyCount++;

        if (tlCount > removeCount) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.HIGH)
                    .matched(true)
                    .message(String.format("检测到 %d 个 ThreadLocal 变量，但只有 %d 处 remove() 调用。" +
                                    "线程池环境下可能导致内存泄漏或数据错乱. Try-finally 块: %d",
                            tlCount, removeCount, tryFinallyCount))
                    .suggestion("在 finally 块中调用 ThreadLocal.remove() 确保清理，或使用阿里 TransmittableThreadLocal")
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
