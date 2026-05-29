package com.codepilot.semantic;

import com.codepilot.github.model.PrFile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaSemanticAnalyzer implements CodeSemanticAnalyzer {

    private static final Pattern TRANSACTIONAL_PATTERN = Pattern.compile("@Transactional\\s*\\(", Pattern.MULTILINE);
    private static final Pattern SYNCHRONIZED_PATTERN = Pattern.compile("synchronized\\s*\\(");
    private static final Pattern LOCK_PATTERN = Pattern.compile("(ReentrantLock|ReentrantReadWriteLock|StampedLock)");
    private static final Pattern THREAD_POOL_PATTERN = Pattern.compile("(ThreadPoolExecutor|Executors\\.new\\w+ThreadPool|ExecutorService)");
    private static final Pattern COMPLETABLE_FUTURE_PATTERN = Pattern.compile("CompletableFuture\\.(supplyAsync|runAsync|allOf|anyOf)");
    private static final Pattern REDIS_PATTERN = Pattern.compile("(RedisTemplate|StringRedisTemplate|@Cacheable|@CacheEvict|@CachePut)");
    private static final Pattern MYBATIS_DOLLAR_PATTERN = Pattern.compile("\\$\\{[^}]*\\}");
    private static final Pattern MYBATIS_HASH_PATTERN = Pattern.compile("#\\{[^}]*\\}");
    private static final Pattern STREAM_PATTERN = Pattern.compile("\\.(stream|parallelStream)\\(\\)");
    private static final Pattern SWALLOWED_EXCEPTION = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
    private static final Pattern CATCH_LOG_ONLY = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{[^}]*log\\.(warn|info|debug|error)");
    private static final Pattern DEPRECATED_ANNOTATION = Pattern.compile("@Deprecated");
    private static final Pattern VOLATILE_PATTERN = Pattern.compile("volatile\\s+\\w+");
    private static final Pattern NOTNULL_ANNOTATION = Pattern.compile("@(NotNull|NonNull|Nullable|CheckForNull)");
    private static final Pattern BIG_DECIMAL_EQUALS = Pattern.compile("BigDecimal.*\\.equals\\(");

    @Override public String getName() { return "Java"; }
    @Override public int getPriority() { return 10; }

    @Override
    public boolean supports(String language) {
        return language != null && (language.equalsIgnoreCase("Java") || language.equalsIgnoreCase("Kotlin"));
    }

    @Override
    public List<SemanticFinding> analyze(PrFile file) {
        String patch = file.getPatch();
        if (patch == null || patch.isEmpty()) return List.of();

        List<SemanticFinding> findings = new ArrayList<>();

        checkTransactionalUsage(patch, file.getFilename(), findings);
        checkConcurrencyPatterns(patch, file.getFilename(), findings);
        checkRedisCacheUsage(patch, file.getFilename(), findings);
        checkMyBatisInjection(patch, file.getFilename(), findings);
        checkExceptionHandling(patch, file.getFilename(), findings);
        checkResourcePatterns(patch, file.getFilename(), findings);

        return findings;
    }

    private void checkTransactionalUsage(String patch, String filename, List<SemanticFinding> findings) {
        if (TRANSACTIONAL_PATTERN.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("TRANSACTIONAL_USAGE")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("@Transactional")
                    .description("@Transactional annotation detected. Verify propagation level and readOnly flag are correct for this use case.")
                    .suggestion("Explicitly set propagation= and readOnly= on @Transactional. Consider rollbackFor = Exception.class for checked exceptions.")
                    .language("Java")
                    .source(getName())
                    .build());
        }
    }

    private void checkConcurrencyPatterns(String patch, String filename, List<SemanticFinding> findings) {
        boolean hasLock = LOCK_PATTERN.matcher(patch).find();
        boolean hasSync = SYNCHRONIZED_PATTERN.matcher(patch).find();
        boolean hasThreadPool = THREAD_POOL_PATTERN.matcher(patch).find();
        boolean hasCf = COMPLETABLE_FUTURE_PATTERN.matcher(patch).find();
        boolean hasVolatile = VOLATILE_PATTERN.matcher(patch).find();

        if (hasLock || hasSync) {
            findings.add(SemanticFinding.builder()
                    .type("CONCURRENCY_LOCK")
                    .severity("HIGH")
                    .file(filename)
                    .pattern(hasLock ? "Lock" : "synchronized")
                    .description("Lock/synchronization mechanism detected. Verify proper try-finally for lock release and avoid nested locks.")
                    .suggestion("Always release locks in finally block. Use tryLock with timeout to prevent deadlocks.")
                    .language("Java")
                    .source(getName())
                    .build());
        }

        if (hasThreadPool) {
            findings.add(SemanticFinding.builder()
                    .type("THREAD_POOL_USAGE")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("ThreadPoolExecutor/ExecutorService")
                    .description("Thread pool detected. Ensure proper lifecycle management (shutdown/awaitTermination) and appropriate queue sizing.")
                    .suggestion("Configure core/max pool sizes, bounded queue, and rejection policy. Call shutdown() in a finally or @PreDestroy hook.")
                    .language("Java")
                    .source(getName())
                    .build());
        }

        if (hasCf) {
            findings.add(SemanticFinding.builder()
                    .type("COMPLETABLE_FUTURE")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("CompletableFuture")
                    .description("CompletableFuture usage detected. Ensure proper exception handling with exceptionally() or handle() and timeout configuration.")
                    .suggestion("Always chain .exceptionally() or .handle(). Use orTimeout()/completeOnTimeout() for timeout control.")
                    .language("Java")
                    .source(getName())
                    .build());
        }

        if (hasVolatile && hasSync) {
            findings.add(SemanticFinding.builder()
                    .type("VOLATILE_WITH_SYNC")
                    .severity("LOW")
                    .file(filename)
                    .pattern("volatile + synchronized")
                    .description("Both volatile and synchronized used together. Verify if volatile alone is sufficient or if atomic classes would be better.")
                    .suggestion("Consider AtomicReference, AtomicInteger, or other java.util.concurrent.atomic classes.")
                    .language("Java")
                    .source(getName())
                    .build());
        }
    }

    private void checkRedisCacheUsage(String patch, String filename, List<SemanticFinding> findings) {
        if (REDIS_PATTERN.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("REDIS_CACHE_USAGE")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("RedisTemplate/@Cacheable")
                    .description("Redis/cache operations detected. Verify key naming convention, TTL expiry, and cache invalidation strategy.")
                    .suggestion("Use consistent key prefix naming. Set appropriate TTL via @Cacheable unless explicitly. Consider cache warming and avalanche prevention.")
                    .language("Java")
                    .source(getName())
                    .build());
        }
    }

    private void checkMyBatisInjection(String patch, String filename, List<SemanticFinding> findings) {
        Matcher dollarMatcher = MYBATIS_DOLLAR_PATTERN.matcher(patch);
        if (dollarMatcher.find()) {
            findings.add(SemanticFinding.builder()
                    .type("SQL_INJECTION_RISK")
                    .severity("CRITICAL")
                    .file(filename)
                    .pattern("${} in MyBatis")
                    .description("MyBatis ${} placeholder detected — this performs direct string substitution and is vulnerable to SQL injection. Use #{} for parameterized values.")
                    .suggestion("Replace ${...} with #{...} unless ${} is strictly necessary (e.g., dynamic table/column names). If unavoidable, validate and whitelist input values.")
                    .language("Java")
                    .source(getName())
                    .build());
        }
    }

    private void checkExceptionHandling(String patch, String filename, List<SemanticFinding> findings) {
        if (SWALLOWED_EXCEPTION.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("SWALLOWED_EXCEPTION")
                    .severity("HIGH")
                    .file(filename)
                    .pattern("empty catch block")
                    .description("Empty catch block detected — exception is silently swallowed. This hides errors and makes debugging extremely difficult.")
                    .suggestion("At minimum, log the exception. Consider re-throwing, wrapping in a domain exception, or handling gracefully with a fallback.")
                    .language("Java")
                    .source(getName())
                    .build());
        }

        if (CATCH_LOG_ONLY.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("CATCH_LOG_ONLY")
                    .severity("LOW")
                    .file(filename)
                    .pattern("catch + log only")
                    .description("Exception caught and only logged without re-throw or fallback. Ensure the caller is aware of the failure if needed.")
                    .suggestion("Consider if the exception should propagate. If truly recoverable, document the recovery path clearly.")
                    .language("Java")
                    .source(getName())
                    .build());
        }
    }

    private void checkResourcePatterns(String patch, String filename, List<SemanticFinding> findings) {
        if (STREAM_PATTERN.matcher(patch).find() && !patch.contains("try-with-resources") && !patch.contains("try (")) {
            findings.add(SemanticFinding.builder()
                    .type("STREAM_WITHOUT_CLOSE")
                    .severity("LOW")
                    .file(filename)
                    .pattern("Stream without try-with-resources")
                    .description("Stream usage detected without try-with-resources. While most stream operations don't need explicit closing, streams from IO sources do.")
                    .suggestion("If the stream source is IO-based (Files.lines, etc.), wrap in try-with-resources.")
                    .language("Java")
                    .source(getName())
                    .build());
        }

        if (BIG_DECIMAL_EQUALS.matcher(patch).find()) {
            findings.add(SemanticFinding.builder()
                    .type("BIGDECIMAL_EQUALS")
                    .severity("MEDIUM")
                    .file(filename)
                    .pattern("BigDecimal.equals()")
                    .description("BigDecimal.equals() compares scale as well as value — 2.0 and 2.00 are NOT equal. Use compareTo() for value comparison.")
                    .suggestion("Replace .equals() with .compareTo(BigDecimal.ZERO) == 0 or similar for value-based comparison.")
                    .language("Java")
                    .source(getName())
                    .build());
        }
    }
}
