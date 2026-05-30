package com.codepilot.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Detects frameworks and build tools from project configuration files.
 *
 * Detection sources:
 * - Build files in the PR diff (pom.xml, build.gradle, package.json, etc.)
 * - File content heuristics (dependencies in package.json, imports patterns)
 * - Module structure conventions (Spring Boot, Next.js, etc.)
 */
@Slf4j
@Component
public class FrameworkDetector {

    private static final Map<String, String> BUILD_FILE_TO_FRAMEWORK = new LinkedHashMap<>();
    private static final Map<String, List<String>> IMPORT_FRAMEWORK_HINTS = new LinkedHashMap<>();

    static {
        BUILD_FILE_TO_FRAMEWORK.put("pom.xml", "Maven");
        BUILD_FILE_TO_FRAMEWORK.put("build.gradle", "Gradle");
        BUILD_FILE_TO_FRAMEWORK.put("build.gradle.kts", "Gradle (Kotlin DSL)");
        BUILD_FILE_TO_FRAMEWORK.put("settings.gradle", "Gradle");
        BUILD_FILE_TO_FRAMEWORK.put("application.yml", "Spring Boot");
        BUILD_FILE_TO_FRAMEWORK.put("application.yaml", "Spring Boot");
        BUILD_FILE_TO_FRAMEWORK.put("application.properties", "Spring Boot");
        BUILD_FILE_TO_FRAMEWORK.put("package.json", null);           // special handling
        BUILD_FILE_TO_FRAMEWORK.put("tsconfig.json", "TypeScript");
        BUILD_FILE_TO_FRAMEWORK.put("vite.config.ts", "Vite");
        BUILD_FILE_TO_FRAMEWORK.put("vite.config.js", "Vite");
        BUILD_FILE_TO_FRAMEWORK.put("next.config.js", "Next.js");
        BUILD_FILE_TO_FRAMEWORK.put("next.config.ts", "Next.js");
        BUILD_FILE_TO_FRAMEWORK.put("nuxt.config.ts", "Nuxt");
        BUILD_FILE_TO_FRAMEWORK.put("go.mod", "Go Modules");
        BUILD_FILE_TO_FRAMEWORK.put("requirements.txt", "pip");
        BUILD_FILE_TO_FRAMEWORK.put("pyproject.toml", "Python (pyproject)");
        BUILD_FILE_TO_FRAMEWORK.put("setup.py", "setuptools");
        BUILD_FILE_TO_FRAMEWORK.put("Cargo.toml", "Cargo");
        BUILD_FILE_TO_FRAMEWORK.put("Dockerfile", "Docker");
        BUILD_FILE_TO_FRAMEWORK.put("docker-compose.yml", "Docker Compose");
        BUILD_FILE_TO_FRAMEWORK.put("docker-compose.yaml", "Docker Compose");
        BUILD_FILE_TO_FRAMEWORK.put("Makefile", "Make");
        BUILD_FILE_TO_FRAMEWORK.put(".gitlab-ci.yml", "GitLab CI");
        BUILD_FILE_TO_FRAMEWORK.put(".github/workflows", "GitHub Actions");
        BUILD_FILE_TO_FRAMEWORK.put("Jenkinsfile", "Jenkins");

        // Import patterns -> framework hints
        IMPORT_FRAMEWORK_HINTS.put("org.springframework", List.of("Spring"));
        IMPORT_FRAMEWORK_HINTS.put("org.mybatis", List.of("MyBatis"));
        IMPORT_FRAMEWORK_HINTS.put("org.hibernate", List.of("Hibernate"));
        IMPORT_FRAMEWORK_HINTS.put("jakarta.persistence", List.of("JPA"));
        IMPORT_FRAMEWORK_HINTS.put("lombok", List.of("Lombok"));
        IMPORT_FRAMEWORK_HINTS.put("org.redisson", List.of("Redisson"));
        IMPORT_FRAMEWORK_HINTS.put("redis.clients", List.of("Jedis"));
        IMPORT_FRAMEWORK_HINTS.put("org.apache.kafka", List.of("Kafka"));
        IMPORT_FRAMEWORK_HINTS.put("org.elasticsearch", List.of("Elasticsearch"));
        IMPORT_FRAMEWORK_HINTS.put("react", List.of("React"));
        IMPORT_FRAMEWORK_HINTS.put("vue", List.of("Vue"));
        IMPORT_FRAMEWORK_HINTS.put("@angular", List.of("Angular"));
        IMPORT_FRAMEWORK_HINTS.put("next/", List.of("Next.js"));
        IMPORT_FRAMEWORK_HINTS.put("express", List.of("Express"));
        IMPORT_FRAMEWORK_HINTS.put("tailwindcss", List.of("Tailwind CSS"));
        IMPORT_FRAMEWORK_HINTS.put("@mui/", List.of("MUI"));
        IMPORT_FRAMEWORK_HINTS.put("antd", List.of("Ant Design"));
        IMPORT_FRAMEWORK_HINTS.put("element-plus", List.of("Element Plus"));
        IMPORT_FRAMEWORK_HINTS.put("pinia", List.of("Pinia"));
        IMPORT_FRAMEWORK_HINTS.put("vuex", List.of("Vuex"));
        IMPORT_FRAMEWORK_HINTS.put("redux", List.of("Redux"));
        IMPORT_FRAMEWORK_HINTS.put("zustand", List.of("Zustand"));
    }

    /**
     * Detect frameworks from file names in the PR.
     * For package.json, the content (diff patch) is analyzed to identify specific frameworks.
     */
    public List<String> detect(List<com.codepilot.github.model.PrFile> files) {
        Set<String> frameworks = new LinkedHashSet<>();

        for (com.codepilot.github.model.PrFile file : files) {
            String filename = file.getFilename();
            if (filename == null) continue;

            String baseName = filename.contains("/")
                    ? filename.substring(filename.lastIndexOf('/') + 1)
                    : filename;

            // Direct build file match
            String fw = BUILD_FILE_TO_FRAMEWORK.get(baseName);
            if (fw != null) {
                frameworks.add(fw);
            }

            // Special handling for package.json (detect from content)
            if (baseName.equals("package.json")) {
                List<String> jsFrameworks = detectFromPackageJson(file);
                frameworks.addAll(jsFrameworks);
            }

            // Special handling for pom.xml (detect Spring Boot starters)
            if (baseName.equals("pom.xml")) {
                List<String> springModules = detectFromPomXml(file);
                frameworks.addAll(springModules);
            }

            // Detect from file content imports
            List<String> importFrameworks = detectFromImports(file);
            frameworks.addAll(importFrameworks);
        }

        return new ArrayList<>(frameworks);
    }

    /**
     * Analyze code diff imports to detect framework usage.
     */
    public List<String> detectFromImports(com.codepilot.github.model.PrFile file) {
        String patch = file.getPatch();
        if (patch == null) return List.of();

        Set<String> found = new LinkedHashSet<>();
        // Only scan added lines (+)
        for (String line : patch.split("\n")) {
            if (!line.startsWith("+") || line.length() < 2) continue;
            String code = line.substring(1).trim();

            // Check import statements
            if (code.startsWith("import ")) {
                for (var entry : IMPORT_FRAMEWORK_HINTS.entrySet()) {
                    if (code.contains(entry.getKey())) {
                        found.addAll(entry.getValue());
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    private List<String> detectFromPackageJson(com.codepilot.github.model.PrFile file) {
        String content = file.getPatch();
        if (content == null) return List.of("Node.js");

        List<String> frameworks = new ArrayList<>();
        if (containsDep(content, "react") || containsDep(content, "@nextui-org") || containsDep(content, "@radix-ui")) {
            frameworks.add("React");
        }
        if (containsDep(content, "next")) {
            frameworks.add("Next.js");
        }
        if (containsDep(content, "vue") || containsDep(content, "nuxt") || containsDep(content, "@vue")) {
            frameworks.add("Vue");
        }
        if (containsDep(content, "nuxt")) {
            frameworks.add("Nuxt");
        }
        if (containsDep(content, "@angular")) {
            frameworks.add("Angular");
        }
        if (containsDep(content, "express")) {
            frameworks.add("Express");
        }
        if (containsDep(content, "nestjs") || containsDep(content, "@nestjs")) {
            frameworks.add("NestJS");
        }
        if (containsDep(content, "tailwindcss")) {
            frameworks.add("Tailwind CSS");
        }
        if (containsDep(content, "typescript")) {
            frameworks.add("TypeScript");
        }
        if (containsDep(content, "vite")) {
            frameworks.add("Vite");
        }
        if (containsDep(content, "webpack")) {
            frameworks.add("Webpack");
        }
        if (containsDep(content, "vitest") || containsDep(content, "jest")) {
            frameworks.add(frameworks.contains("Vitest") ? "Jest" : "Vitest");
        }

        return frameworks.isEmpty() ? List.of("Node.js") : frameworks;
    }

    private List<String> detectFromPomXml(com.codepilot.github.model.PrFile file) {
        String content = file.getPatch();
        if (content == null) return List.of("Spring Boot");

        List<String> modules = new ArrayList<>();
        if (content.contains("spring-boot-starter-web") || content.contains("spring-boot-starter-webflux")) {
            modules.add("Spring MVC/WebFlux");
        }
        if (content.contains("spring-boot-starter-data-jpa") || content.contains("spring-boot-starter-data-jdbc")) {
            modules.add("Spring Data JPA");
        }
        if (content.contains("mybatis") || content.contains("mybatis-plus")) {
            modules.add("MyBatis");
        }
        if (content.contains("spring-boot-starter-data-redis") || content.contains("redisson")) {
            modules.add("Redis");
        }
        if (content.contains("spring-cloud")) {
            modules.add("Spring Cloud");
        }
        if (content.contains("spring-boot-starter-security")) {
            modules.add("Spring Security");
        }
        if (content.contains("flyway") || content.contains("liquibase")) {
            modules.add(content.contains("flyway") ? "Flyway" : "Liquibase");
        }
        if (content.contains("hutool")) {
            modules.add("Hutool");
        }

        return modules;
    }

    private boolean containsDep(String content, String depName) {
        // Check for dependency in package.json format: "depName" or "@scope/depName"
        return content.contains("\"" + depName + "\"")
                || content.contains("\"@" + depName + "\"")
                || content.contains("\"@" + depName + "/");
    }
}
