package com.codepilot.ai.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.model.AiReviewRequest;
import com.codepilot.ai.prompting.PromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

@Slf4j
public abstract class AbstractAiProvider implements AiProvider {

    protected final WebClient webClient;
    protected final PromptBuilder promptBuilder;

    protected AbstractAiProvider(WebClient webClient, PromptBuilder promptBuilder) {
        this.webClient = webClient;
        this.promptBuilder = promptBuilder;
    }

    protected abstract String getApiUrl();
    protected abstract String getApiKey();
    protected abstract String getModel();
    protected abstract Map<String, String> getHeaders();

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        String requestBody = buildRequestBody(systemPrompt, userPrompt);
        log.debug("Sending chat request to {}: {}", getProviderName(), requestBody.length());

        try {
            String response = webClient.post()
                    .uri(getApiUrl())
                    .headers(h -> getHeaders().forEach(h::add))
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(120));
            return extractContent(response);
        } catch (Exception e) {
            log.error("{} API call failed: {}", getProviderName(), e.getMessage());
            throw new RuntimeException(getProviderName() + " API error: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        String requestBody = buildStreamRequestBody(systemPrompt, userPrompt);
        log.debug("Starting stream to {}: {} chars", getProviderName(), requestBody.length());

        return webClient.post()
                .uri(getApiUrl())
                .headers(h -> getHeaders().forEach(h::add))
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .concatMap(this::extractStreamContent)
                .onErrorResume(e -> {
                    log.error("{} stream error: {}", getProviderName(), e.getMessage());
                    return Flux.just("[Stream Error: " + e.getMessage() + "]");
                });
    }

    @Override
    public Flux<String> reviewStream(AiReviewRequest request) {
        String userPrompt = promptBuilder.buildReviewPrompt(request);
        String systemPrompt = promptBuilder.buildSystemPrompt(request.getFileLanguage());
        return chatStream(systemPrompt, userPrompt);
    }

    protected String buildRequestBody(String systemPrompt, String userPrompt) {
        JSONObject body = new JSONObject();
        body.set("model", getModel());
        body.set("temperature", 0.3);
        body.set("max_tokens", 4096);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.set("role", "system");
        sysMsg.set("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");
        userMsg.set("content", userPrompt);
        messages.add(userMsg);

        body.set("messages", messages);
        return body.toString();
    }

    protected String buildStreamRequestBody(String systemPrompt, String userPrompt) {
        JSONObject body = new JSONObject(buildRequestBody(systemPrompt, userPrompt));
        body.set("stream", true);
        return body.toString();
    }

    protected String extractContent(String response) {
        try {
            JSONObject json = new JSONObject(response);
            return json.getByPath("choices[0].message.content", String.class);
        } catch (Exception e) {
            log.warn("Failed to parse response from {}: {}", getProviderName(), e.getMessage());
            return response;
        }
    }

    protected Flux<String> extractStreamContent(String chunk) {
        if (chunk == null || chunk.trim().isEmpty()) return Flux.empty();
        if ("[DONE]".equals(chunk.trim())) return Flux.empty();

        try {
            String data = chunk.startsWith("data:") ? chunk.substring(5).trim() : chunk.trim();
            if (data.isEmpty() || "[DONE]".equals(data)) return Flux.empty();

            JSONObject json = new JSONObject(data);
            String content = json.getByPath("choices[0].delta.content", String.class);
            return content != null ? Flux.just(content) : Flux.empty();
        } catch (Exception e) {
            return Flux.empty();
        }
    }
}
