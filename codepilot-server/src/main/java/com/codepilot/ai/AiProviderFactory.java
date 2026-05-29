package com.codepilot.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AiProviderFactory {

    private final Map<String, AiProvider> providers;
    private final String defaultProvider;

    public AiProviderFactory(List<AiProvider> providerList,
                             @Value("${ai.default-provider}") String defaultProvider) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AiProvider::getProviderName, p -> p));
        this.defaultProvider = defaultProvider;
        log.info("Loaded AI providers: {}", providers.keySet());
    }

    public AiProvider getProvider() {
        return getProvider(defaultProvider);
    }

    public AiProvider getProvider(String name) {
        AiProvider provider = providers.get(name.toLowerCase());
        if (provider == null) {
            log.warn("Provider '{}' not found, using default: {}", name, defaultProvider);
            provider = providers.get(defaultProvider);
        }
        if (provider == null) {
            throw new IllegalStateException("No AI provider available");
        }
        if (!provider.isAvailable()) {
            log.warn("Provider '{}' is not configured", provider.getProviderName());
            AiProvider fallback = findFirstAvailable();
            if (fallback != null) return fallback;
        }
        return provider;
    }

    public List<String> getAvailableProviders() {
        return providers.values().stream()
                .filter(AiProvider::isAvailable)
                .map(AiProvider::getProviderName)
                .collect(Collectors.toList());
    }

    private AiProvider findFirstAvailable() {
        return providers.values().stream()
                .filter(AiProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }
}
