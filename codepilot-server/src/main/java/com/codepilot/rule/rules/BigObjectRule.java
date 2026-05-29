package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BigObjectRule implements Rule {

    private static final Pattern NEW_ARRAYLIST = Pattern.compile("new ArrayList<>\\(\\s*\\)");
    private static final Pattern NEW_HASHMAP = Pattern.compile("new HashMap<>\\(\\s*\\)");
    private static final Pattern POTENTIAL_LARGE = Pattern.compile(
            "select\\s+\\*\\s+from|SELECT\\s+\\*\\s+FROM|findAll\\(\\)|\\.listAll\\(\\)|selectAll");

    @Override
    public String getRuleName() {
        return "BIG_OBJECT_CREATION";
    }

    @Override
    public String getCategory() {
        return "MEMORY";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.MEDIUM;
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        boolean hasListNoSize = NEW_ARRAYLIST.matcher(content).find();
        boolean hasMapNoSize = NEW_HASHMAP.matcher(content).find();
        boolean hasSelectAll = POTENTIAL_LARGE.matcher(content).find();

        int newArrayListCount = 0;
        Matcher m = NEW_ARRAYLIST.matcher(content);
        while (m.find()) newArrayListCount++;

        if ((hasListNoSize || hasMapNoSize) && hasSelectAll && newArrayListCount > 1) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.MEDIUM)
                    .matched(true)
                    .message("检测到未指定初始容量的集合创建 + 全量查询(" + newArrayListCount + " 个)，可能导致大对象和频繁扩容")
                    .suggestion("1. ArrayList/HashMap 指定合理初始容量避免扩容\n2. 避免 SELECT * 全量查询，使用分页或条件过滤")
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
