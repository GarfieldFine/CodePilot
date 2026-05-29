package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class HighIORule implements Rule {

    private static final Pattern IO_IN_LOOP = Pattern.compile(
            "(for|while)\\s*\\([^)]*\\)\\s*\\{[^}]*\\b(HttpClient|RestTemplate|WebClient|FeignClient|" +
                    "JdbcTemplate|RedisTemplate|MongoTemplate|KafkaTemplate|FileInputStream|FileOutputStream)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern IO_CALL = Pattern.compile(
            "(HttpClient\\.|RestTemplate\\.|WebClient\\.|JdbcTemplate\\.|redisTemplate\\.|mongoTemplate\\.)");

    @Override
    public String getRuleName() {
        return "HIGH_IO_IN_LOOP";
    }

    @Override
    public String getCategory() {
        return "PERFORMANCE";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public int getPriority() {
        return 12;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        boolean ioInLoop = IO_IN_LOOP.matcher(content).find();

        if (ioInLoop) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.HIGH)
                    .matched(true)
                    .message("检测到在循环中进行 IO 操作（HTTP 调用、DB 查询、Redis 访问），存在严重性能风险")
                    .suggestion("使用批量接口替代循环中的单次 IO 调用。例如：批量查询、批量缓存操作、批量 HTTP 请求")
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
