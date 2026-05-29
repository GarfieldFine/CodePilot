package com.codepilot.ai;

import com.codepilot.ai.model.AiReviewRequest;
import com.codepilot.ai.model.AiReviewResponse;
import reactor.core.publisher.Flux;

public interface AiProvider {

    String getProviderName();

    String chat(String systemPrompt, String userPrompt);

    Flux<String> chatStream(String systemPrompt, String userPrompt);

    Flux<String> reviewStream(AiReviewRequest request);

    boolean isAvailable();
}
