package com.codepilot.rule.rules;

import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.Rule;
import com.codepilot.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RedisLockRule implements Rule {

    private static final Pattern LOCK_ACQUIRE = Pattern.compile(
            "(Redisson|RedisLock|RLock|redisLock|lock\\.lock|lock\\.tryLock|tryLock|setIfAbsent|setnx)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOCK_RELEASE = Pattern.compile(
            "(unlock|lock\\.unlock|\\.unlock\\(\\)|release|forceUnlock)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TRY_FINALLY = Pattern.compile(
            "try\\s*\\{.*?\\}\\s*finally\\s*\\{");

    @Override
    public String getRuleName() {
        return "REDIS_LOCK_UNRELEASED";
    }

    @Override
    public String getCategory() {
        return "DISTRIBUTED_LOCK";
    }

    @Override
    public RiskLevel getDefaultRiskLevel() {
        return RiskLevel.CRITICAL;
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public RuleResult check(String filepath, String content, Map<String, Object> context) {
        if (!isJavaFile(filepath)) return noMatch();

        Matcher lockMatcher = LOCK_ACQUIRE.matcher(content);
        int lockCount = 0;
        while (lockMatcher.find()) lockCount++;

        if (lockCount == 0) return noMatch();

        Matcher unlockMatcher = LOCK_RELEASE.matcher(content);
        int unlockCount = 0;
        while (unlockMatcher.find()) unlockCount++;

        Matcher tfMatcher = TRY_FINALLY.matcher(content);
        int tryFinallyCount = 0;
        while (tfMatcher.find()) tryFinallyCount++;

        if (lockCount > unlockCount && tryFinallyCount < lockCount) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.CRITICAL)
                    .matched(true)
                    .message(String.format("Redis 分布式锁风险：%d 处加锁，仅 %d 处释放，%d 处 try-finally。" +
                                    "锁可能未在 finally 中释放，存在死锁风险",
                            lockCount, unlockCount, tryFinallyCount))
                    .suggestion("始终在 try-finally 中释放 Redis 锁，防止异常导致死锁。确保超时机制和锁续期策略")
                    .build();
        }

        if (lockCount > 0 && tryFinallyCount == 0 && unlockCount > 0) {
            return RuleResult.builder()
                    .ruleName(getRuleName())
                    .category(getCategory())
                    .riskLevel(RiskLevel.HIGH)
                    .matched(true)
                    .message("Redis 锁的释放未在 finally 块中执行，异常情况下可能无法释放锁")
                    .suggestion("将 unlock() 调用放入 finally 块中确保锁释放")
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
