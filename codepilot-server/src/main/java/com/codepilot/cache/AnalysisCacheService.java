package com.codepilot.cache;

import com.codepilot.review.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
public class AnalysisCacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final Duration cacheTtl;

    private static final String CACHE_KEY_PREFIX = "codepilot:analysis:";
    private static final String PR_INDEX_KEY = "codepilot:pr_index:";

    public AnalysisCacheService(ReactiveRedisTemplate<String, Object> redisTemplate,
                                 @Value("${review.cache-ttl-minutes:30}") int cacheTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = Duration.ofMinutes(cacheTtlMinutes);
    }

    public Mono<AnalysisResult> getCached(String owner, String repo, int prNumber) {
        String key = buildKey(owner, repo, prNumber);
        return redisTemplate.opsForValue()
                .get(key)
                .map(obj -> obj instanceof AnalysisResult ? (AnalysisResult) obj : null)
                .doOnNext(result -> {
                    if (result != null) {
                        log.info("Cache hit for {}/{}/#{}", owner, repo, prNumber);
                    }
                });
    }

    public Mono<Boolean> cacheResult(String owner, String repo, int prNumber, AnalysisResult result) {
        String key = buildKey(owner, repo, prNumber);
        return redisTemplate.opsForValue()
                .set(key, result, cacheTtl)
                .doOnSuccess(success -> log.info("Cached analysis for {}/{}/#{}", owner, repo, prNumber));
    }

    public Mono<Boolean> invalidate(String owner, String repo, int prNumber) {
        String key = buildKey(owner, repo, prNumber);
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }

    private String buildKey(String owner, String repo, int prNumber) {
        return CACHE_KEY_PREFIX + owner + ":" + repo + ":" + prNumber;
    }
}
