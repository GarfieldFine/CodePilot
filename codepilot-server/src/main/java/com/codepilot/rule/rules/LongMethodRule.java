package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LongMethodRule implements Rule {

    private static final int MAX_METHOD_LINES = 50;

    @Override
    public String getRuleName() {
        return "LONG_METHOD";
    }

    @Override
    public String getCategory() {
        return "CODE_QUALITY";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.MEDIUM;
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isCodeFile(filepath)) return noMatch();

        String addedLines = extractAddedContext(content);

        Pattern methodStart = Pattern.compile(
                "(public|private|protected|static|\\s)+[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(throws\\s+[\\w\\s,]+)?\\s*\\{");

        Matcher matcher = methodStart.matcher(addedLines);
        int longMethods = 0;
        StringBuilder details = new StringBuilder();

        while (matcher.find()) {
            int methodStartPos = matcher.end();
            int braceCount = 1;
            int methodEnd = methodStartPos;
            int lines = 1;

            for (int i = methodStartPos; i < addedLines.length() && braceCount > 0; i++) {
                char c = addedLines.charAt(i);
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
                if (c == '\n') lines++;
                if (braceCount == 0) methodEnd = i;
            }

            if (lines > MAX_METHOD_LINES) {
                longMethods++;
                String methodName = matcher.group(2);
                if (details.length() < 300) {
                    details.append(methodName).append(": ").append(lines).append(" lines\n");
                }
            }
        }

        if (longMethods > 0) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.MEDIUM)
                    .matched(true)
                    .message(String.format("检测到 %d 个方法超过 %d 行，建议拆分", longMethods, MAX_METHOD_LINES))
                    .suggestion("将超长方法拆分为多个职责单一的小方法，每个方法不超过 " + MAX_METHOD_LINES + " 行")
                    .codeSnippet(details.toString().trim())
                    .build();
        }

        return noMatch();
    }

    private String extractAddedContext(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                sb.append(line.substring(1)).append("\n");
            } else if (!line.startsWith("-") && !line.startsWith("---") && !line.startsWith("@@")) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private boolean isCodeFile(String filename) {
        if (filename == null) return false;
        return filename.endsWith(".java") || filename.endsWith(".kt") || filename.endsWith(".py")
                || filename.endsWith(".ts") || filename.endsWith(".js") || filename.endsWith(".go");
    }

    private RuleResult noMatch() {
        return RuleResult.builder().matched(false).build();
    }
}
