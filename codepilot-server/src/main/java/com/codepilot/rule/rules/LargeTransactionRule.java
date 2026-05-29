package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class LargeTransactionRule implements Rule {

    private static final Pattern TRANSACTIONAL = Pattern.compile("@Transactional", Pattern.CASE_INSENSITIVE);
    private static final Pattern DB_CALL = Pattern.compile(
            "(\\.save\\(|\\.update\\(|\\.insert\\(|\\.delete\\(|\\.batchSave|\\.batchUpdate|" +
                    "jdbcTemplate|mybatis|JpaRepository|MongoRepository)");

    @Override
    public String getRuleName() {
        return "LARGE_TRANSACTION";
    }

    @Override
    public String getCategory() {
        return "DATABASE";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public int getPriority() {
        return 15;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        boolean hasTransactional = TRANSACTIONAL.matcher(content).find();
        if (!hasTransactional) return noMatch();

        int dbCallCount = 0;
        var dbMatcher = DB_CALL.matcher(content);
        while (dbMatcher.find()) dbCallCount++;

        boolean hasRemoteCall = Pattern.compile("HttpClient|RestTemplate|WebClient|FeignClient|RpcCall|MQ|Kafka|RabbitMQ|sendMessage",
                Pattern.CASE_INSENSITIVE).matcher(content).find();

        boolean hasFileIO = Pattern.compile("FileInputStream|FileOutputStream|Files\\.(read|write|copy)|BufferedReader|PrintWriter",
                Pattern.CASE_INSENSITIVE).matcher(content).find();

        if (dbCallCount > 3 && (hasRemoteCall || hasFileIO)) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.HIGH)
                    .matched(true)
                    .message(String.format("事务方法中包含 %d 处 DB 操作%s%s，可能导致大事务、长事务",
                            dbCallCount,
                            hasRemoteCall ? " + 远程调用" : "",
                            hasFileIO ? " + 文件IO" : ""))
                    .suggestion("缩小事务范围：将非必要操作移出事务，使用 TransactionTemplate 精细控制事务边界")
                    .build();
        }

        if (dbCallCount > 5) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.MEDIUM)
                    .matched(true)
                    .message(String.format("事务方法中包含 %d 处 DB 操作，事务范围可能过大", dbCallCount))
                    .suggestion("评估是否可以拆分事务或改为异步处理部分操作")
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
