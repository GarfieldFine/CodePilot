package com.codepilot.repository;

import com.codepilot.github.model.PrFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Classifies changed files into architectural modules (Controller, Service, DAO, etc.)
 * based on file path conventions and naming patterns.
 */
@Slf4j
@Component
public class ModuleClassifier {

    public enum ModuleType {
        CONTROLLER("Controller", "API/Request handling layer"),
        SERVICE("Service", "Business logic layer"),
        DATA_ACCESS("Data Access", "DAO/Repository/Mapper layer"),
        MODEL("Model", "Entity/DTO/Domain model layer"),
        CONFIG("Config", "Configuration and infrastructure"),
        FRONTEND("Frontend", "UI components and views"),
        DATABASE("Database", "SQL migrations and schema changes"),
        TEST("Test", "Test files"),
        UTIL("Util", "Utilities and helpers"),
        OTHER("Other", "Unclassified files");

        private final String label;
        private final String description;

        ModuleType(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    private static final Map<String, ModuleType> PATH_KEYWORD_TO_MODULE = new LinkedHashMap<>();

    static {
        PATH_KEYWORD_TO_MODULE.put("controller", ModuleType.CONTROLLER);
        PATH_KEYWORD_TO_MODULE.put("handler", ModuleType.CONTROLLER);
        PATH_KEYWORD_TO_MODULE.put("router", ModuleType.CONTROLLER);
        PATH_KEYWORD_TO_MODULE.put("endpoint", ModuleType.CONTROLLER);
        PATH_KEYWORD_TO_MODULE.put("resource", ModuleType.CONTROLLER);

        PATH_KEYWORD_TO_MODULE.put("service", ModuleType.SERVICE);
        PATH_KEYWORD_TO_MODULE.put("usecase", ModuleType.SERVICE);
        PATH_KEYWORD_TO_MODULE.put("application", ModuleType.SERVICE);
        PATH_KEYWORD_TO_MODULE.put("manager", ModuleType.SERVICE);

        PATH_KEYWORD_TO_MODULE.put("dao", ModuleType.DATA_ACCESS);
        PATH_KEYWORD_TO_MODULE.put("repository", ModuleType.DATA_ACCESS);
        PATH_KEYWORD_TO_MODULE.put("mapper", ModuleType.DATA_ACCESS);
        PATH_KEYWORD_TO_MODULE.put("datasource", ModuleType.DATA_ACCESS);

        PATH_KEYWORD_TO_MODULE.put("model", ModuleType.MODEL);
        PATH_KEYWORD_TO_MODULE.put("entity", ModuleType.MODEL);
        PATH_KEYWORD_TO_MODULE.put("domain", ModuleType.MODEL);
        PATH_KEYWORD_TO_MODULE.put("dto", ModuleType.MODEL);
        PATH_KEYWORD_TO_MODULE.put("vo", ModuleType.MODEL);
        PATH_KEYWORD_TO_MODULE.put("pojo", ModuleType.MODEL);
        PATH_KEYWORD_TO_MODULE.put("bean", ModuleType.MODEL);

        PATH_KEYWORD_TO_MODULE.put("config", ModuleType.CONFIG);
        PATH_KEYWORD_TO_MODULE.put("properties", ModuleType.CONFIG);
        PATH_KEYWORD_TO_MODULE.put("filter", ModuleType.CONFIG);
        PATH_KEYWORD_TO_MODULE.put("interceptor", ModuleType.CONFIG);
        PATH_KEYWORD_TO_MODULE.put("listener", ModuleType.CONFIG);
        PATH_KEYWORD_TO_MODULE.put("aspect", ModuleType.CONFIG);
        PATH_KEYWORD_TO_MODULE.put("aop", ModuleType.CONFIG);

        PATH_KEYWORD_TO_MODULE.put("test", ModuleType.TEST);
        PATH_KEYWORD_TO_MODULE.put("spec", ModuleType.TEST);

        PATH_KEYWORD_TO_MODULE.put("util", ModuleType.UTIL);
        PATH_KEYWORD_TO_MODULE.put("helper", ModuleType.UTIL);
        PATH_KEYWORD_TO_MODULE.put("common", ModuleType.UTIL);
        PATH_KEYWORD_TO_MODULE.put("constant", ModuleType.UTIL);
        PATH_KEYWORD_TO_MODULE.put("enums", ModuleType.UTIL);
        PATH_KEYWORD_TO_MODULE.put("exception", ModuleType.UTIL);
    }

    /**
     * Classify all files into modules.
     */
    public Map<String, List<String>> classify(List<PrFile> files) {
        Map<String, List<String>> modules = new LinkedHashMap<>();
        for (PrFile file : files) {
            String filename = file.getFilename();
            if (filename == null) continue;

            ModuleType type = classifyFile(filename);
            modules.computeIfAbsent(type.getLabel(), k -> new ArrayList<>()).add(filename);
        }
        return modules;
    }

    /**
     * Classify a single file into a module type.
     */
    public ModuleType classifyFile(String filename) {
        if (filename == null) return ModuleType.OTHER;

        String lower = filename.toLowerCase();

        // Frontend file types
        if (lower.endsWith(".vue") || lower.endsWith(".svelte") ||
                lower.endsWith(".tsx") || lower.endsWith(".jsx") ||
                lower.endsWith(".css") || lower.endsWith(".scss") ||
                lower.contains("component") || lower.contains("pages/") ||
                lower.contains("views/") || lower.contains("layouts/")) {
            return ModuleType.FRONTEND;
        }

        // Database files
        if (lower.endsWith(".sql") || lower.contains("migration") ||
                lower.contains("flyway") || lower.contains("liquibase") ||
                lower.contains("schema") || lower.contains("ddl")) {
            return ModuleType.DATABASE;
        }

        // Path-based classification
        for (var entry : PATH_KEYWORD_TO_MODULE.entrySet()) {
            if (lower.contains("/" + entry.getKey() + "/")
                    || lower.contains("\\" + entry.getKey() + "\\")
                    || lower.contains(entry.getKey() + "/")
                    || lower.contains(entry.getKey() + "\\")
                    || lower.endsWith(entry.getKey() + ".java")
                    || lower.endsWith(entry.getKey() + ".ts")
                    || lower.endsWith(entry.getKey() + ".py")
                    || lower.endsWith(entry.getKey() + ".go")) {
                return entry.getValue();
            }
        }

        return ModuleType.OTHER;
    }

    /**
     * Get risk weight for a module type (used in risk scoring).
     */
    public int getRiskWeight(ModuleType type) {
        return switch (type) {
            case CONTROLLER -> 3;
            case SERVICE -> 4;
            case DATA_ACCESS -> 4;
            case MODEL -> 2;
            case CONFIG -> 5;
            case FRONTEND -> 2;
            case DATABASE -> 5;
            case TEST -> 1;
            case UTIL -> 2;
            default -> 2;
        };
    }
}
