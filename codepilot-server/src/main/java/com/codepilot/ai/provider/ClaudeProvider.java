package com.codepilot.ai.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.model.AiReviewRequest;
import com.codepilot.ai.prompting.PromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("claude")
public class ClaudeProvider implements AiProvider {

    private final WebClient webClient;
    private final PromptBuilder promptBuilder;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public ClaudeProvider(WebClient webClient,
                          PromptBuilder promptBuilder,
                          @Value("${ai.providers.claude.api-url}") String apiUrl,
                          @Value("${ai.providers.claude.api-key}") String apiKey,
                          @Value("${ai.providers.claude.model}") String model) {
        this.webClient = webClient;
        this.promptBuilder = promptBuilder;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        String requestBody = buildClaudeBody(systemPrompt, userPrompt, false);
        log.debug("Sending Claude request: {} chars", requestBody.length());

        try {
            String response = webClient.post()
                    .uri(apiUrl)
                    .headers(h -> {
                        h.add("x-api-key", apiKey);
                        h.add("anthropic-version", "2023-06-01");
                        h.add("Content-Type", "application/json");
                    })
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(120));
            return extractClaudeContent(response);
        } catch (Exception e) {
            log.error("Claude API error: {}", e.getMessage());
            throw new RuntimeException("Claude API error: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        String requestBody = buildClaudeBody(systemPrompt, userPrompt, true);
        log.debug("Starting Claude stream");

        return webClient.post()
                .uri(apiUrl)
                .headers(h -> {
                    h.add("x-api-key", apiKey);
                    h.add("anthropic-version", "2023-06-01");
                    h.add("Content-Type", "application/json");
                })
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .concatMap(this::extractClaudeStreamContent)
                .onErrorResume(e -> {
                    log.error("Claude stream error: {}", e.getMessage());
                    return Flux.just("[Claude Stream Error: " + e.getMessage() + "]");
                });
    }

    @Override
    public Flux<String> reviewStream(AiReviewRequest request) {
        String userPrompt = promptBuilder.buildReviewPrompt(request);
        String systemPrompt = promptBuilder.buildSystemPrompt(request.getFileLanguage());
        return chatStream(systemPrompt, userPrompt);
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    private String buildClaudeBody(String systemPrompt, String userPrompt, boolean stream) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("max_tokens", 4096);
        body.set("system", systemPrompt);

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");

        JSONArray content = new JSONArray();
        JSONObject textBlock = new JSONObject();
        textBlock.set("type", "text");
        textBlock.set("text", userPrompt);
        content.add(textBlock);
        userMsg.set("content", content);
        messages.add(userMsg);

        body.set("messages", messages);

        if (stream) {
            body.set("stream", true);
        }
        return body.toString();
    }

    private String extractClaudeContent(String response) {
        try {
            JSONObject json = new JSONObject(response);
            JSONArray content = json.getJSONArray("content");
            if (content != null && !content.isEmpty()) {
                return content.getJSONObject(0).getStr("text");
            }
        } catch (Exception e) {
            log.warn("Failed to parse Claude response: {}", e.getMessage());
        }
        return response;
    }

    private Flux<String> extractClaudeStreamContent(String chunk) {
        if (chunk == null || chunk.trim().isEmpty()) return Flux.empty();

        try {
            String data = chunk.startsWith("data:") ? chunk.substring(5).trim() : chunk.trim();
            if (data.isEmpty()) return Flux.empty();

            JSONObject json = new JSONObject(data);
            String type = json.getStr("type");

            if ("content_block_delta".equals(type)) {
                JSONObject delta = json.getJSONObject("delta");
                if (delta != null && "text_delta".equals(delta.getStr("type"))) {
                    String text = delta.getStr("text");
                    return text != null ? Flux.just(text) : Flux.empty();
                }
            }
        } catch (Exception e) {
            // Skip parse errors in stream
        }
        return Flux.empty();
    }
}
