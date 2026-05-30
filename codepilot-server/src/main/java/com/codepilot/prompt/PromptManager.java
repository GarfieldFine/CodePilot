package com.codepilot.prompt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads externalized prompt templates from classpath:prompts/ and provides
 * rendering with {{placeholder}} interpolation.
 *
 * Templates are loaded once at startup and cached in memory.
 */
@Slf4j
@Component
public class PromptManager {

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void loadTemplates() {
        load("system-base");
        load("review-template");
        log.info("Loaded {} prompt templates", templates.size());
    }

    /**
     * Get the raw template content by name (without .md extension).
     */
    public String getTemplate(String name) {
        String template = templates.get(name);
        if (template == null) {
            log.warn("Template '{}' not found, attempting lazy load", name);
            load(name);
            template = templates.get(name);
        }
        return template != null ? template : "";
    }

    /**
     * Render a template by replacing {{key}} placeholders with values.
     */
    public String render(String name, Map<String, String> variables) {
        String template = getTemplate(name);
        if (template.isEmpty()) return "";

        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        // Remove any unreplaced placeholders with empty strings
        result = result.replaceAll("\\{\\{\\w+\\}\\}", "");

        return result;
    }

    private void load(String name) {
        try {
            String path = "prompts/" + name + ".md";
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("Prompt template not found: {}", path);
                return;
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            String template = content.toString().trim();
            templates.put(name, template);
            log.info("Loaded prompt template: {} ({} chars)", name, template.length());
        } catch (Exception e) {
            log.error("Failed to load prompt template '{}': {}", name, e.getMessage());
        }
    }
}
