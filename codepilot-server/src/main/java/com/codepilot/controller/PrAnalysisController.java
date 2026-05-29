package com.codepilot.controller;

import com.codepilot.ai.AiProviderFactory;
import com.codepilot.cache.AnalysisCacheService;
import com.codepilot.github.GitHubClient;
import com.codepilot.model.dto.ApiResponse;
import com.codepilot.model.dto.PrAnalyzeRequest;
import com.codepilot.review.AnalysisResult;
import com.codepilot.service.PrAnalysisService;
import com.codepilot.sse.SseEmitterService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/pr")
public class PrAnalysisController {

    private final PrAnalysisService prAnalysisService;
    private final GitHubClient gitHubClient;
    private final AiProviderFactory aiProviderFactory;
    private final AnalysisCacheService cacheService;

    public PrAnalysisController(PrAnalysisService prAnalysisService,
                                GitHubClient gitHubClient,
                                AiProviderFactory aiProviderFactory,
                                AnalysisCacheService cacheService) {
        this.prAnalysisService = prAnalysisService;
        this.gitHubClient = gitHubClient;
        this.aiProviderFactory = aiProviderFactory;
        this.cacheService = cacheService;
    }

    @PostMapping("/analyze")
    public Mono<ApiResponse<AnalysisResult>> analyze(@Valid @RequestBody PrAnalyzeRequest request) {
        log.info("Analyzing PR: {} with provider: {}",
                request.getPrUrl(),
                request.getProvider() != null ? request.getProvider() : "default");

        return prAnalysisService.analyzePr(request.getPrUrl(), request.getProvider())
                .map(ApiResponse::success)
                .onErrorResume(e -> {
                    log.error("Analysis failed", e);
                    return Mono.just(ApiResponse.error(500, "Analysis failed: " + e.getMessage()));
                });
    }

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyzeStream(@Valid @RequestBody PrAnalyzeRequest request) {
        log.info("Streaming analysis for: {}", request.getPrUrl());
        return prAnalysisService.analyzePrStream(request.getPrUrl(), request.getProvider());
    }

    @GetMapping("/detail")
    public Mono<ApiResponse<Object>> getDetail(@RequestParam String url) {
        return gitHubClient.fetchPrDetail(url)
                .map(prInfo -> {
                    var data = Map.of(
                            "title", prInfo.getTitle(),
                            "author", prInfo.getAuthor(),
                            "files", prInfo.getFiles(),
                            "commits", prInfo.getCommits(),
                            "additions", prInfo.getAdditions(),
                            "deletions", prInfo.getDeletions(),
                            "changedFiles", prInfo.getChangedFiles()
                    );
                    return ApiResponse.success((Object) data);
                })
                .onErrorResume(e -> Mono.just(ApiResponse.error(500, e.getMessage())));
    }

    @GetMapping("/cache/{owner}/{repo}/{prNumber}")
    public Mono<ApiResponse<AnalysisResult>> getCached(@PathVariable String owner,
                                                        @PathVariable String repo,
                                                        @PathVariable int prNumber) {
        return cacheService.getCached(owner, repo, prNumber)
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.error(404, "No cached analysis found"));
    }

    @DeleteMapping("/cache/{owner}/{repo}/{prNumber}")
    public Mono<ApiResponse<Boolean>> invalidateCache(@PathVariable String owner,
                                                       @PathVariable String repo,
                                                       @PathVariable int prNumber) {
        return cacheService.invalidate(owner, repo, prNumber)
                .map(ApiResponse::success);
    }
}
