package com.codepilot.controller;

import com.codepilot.ai.AiProviderFactory;
import com.codepilot.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
public class AiProviderController {

    private final AiProviderFactory aiProviderFactory;

    public AiProviderController(AiProviderFactory aiProviderFactory) {
        this.aiProviderFactory = aiProviderFactory;
    }

    @GetMapping("/providers")
    public ApiResponse<List<String>> getAvailableProviders() {
        return ApiResponse.success(aiProviderFactory.getAvailableProviders());
    }

    @PostMapping("/test/{provider}")
    public ApiResponse<Map<String, Object>> testProvider(@PathVariable String provider) {
        try {
            var p = aiProviderFactory.getProvider(provider);
            boolean available = p.isAvailable();
            return ApiResponse.success(Map.of(
                    "provider", provider,
                    "available", available,
                    "name", p.getProviderName()
            ));
        } catch (Exception e) {
            return ApiResponse.error(500, "Provider test failed: " + e.getMessage());
        }
    }
}
