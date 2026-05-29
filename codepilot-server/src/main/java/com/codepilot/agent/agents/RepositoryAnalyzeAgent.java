package com.codepilot.agent.agents;

import com.codepilot.agent.Agent;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentResult;
import com.codepilot.github.model.PrFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Analyzes the repository structure to detect languages, frameworks, and project type.
 * Uses PR file extensions and standard project file heuristics (pom.xml, package.json, etc.)
 * to build a repository profile.
 */
@Slf4j
@Component
public class RepositoryAnalyzeAgent implements Agent {

    private static final Map<String, String> BUILD_FILE_TO_FRAMEWORK = new LinkedHashMap<>();
    private static final Map<String, String> BUILD_FILE_TO_LANG = new LinkedHashMap<>();
    private static final Map<String, String> EXT_TO_LANG = new LinkedHashMap<>();

    static {
        BUILD_FILE_TO_FRAMEWORK.put("pom.xml", "Spring Boot (Maven)");
        BUILD_FILE_TO_FRAMEWORK.put("build.gradle", "Spring Boot (Gradle)");
        BUILD_FILE_TO_FRAMEWORK.put("build.gradle.kts", "Spring Boot (Gradle Kotlin DSL)");
        BUILD_FILE_TO_FRAMEWORK.put("application.yml", "Spring Boot");
        BUILD_FILE_TO_FRAMEWORK.put("application.properties", "Spring Boot");
        BUILD_FILE_TO_FRAMEWORK.put("package.json", null); // special handling
        BUILD_FILE_TO_FRAMEWORK.put("go.mod", "Go Module");
        BUILD_FILE_TO_FRAMEWORK.put("requirements.txt", "Python");
        BUILD_FILE_TO_FRAMEWORK.put("pyproject.toml", "Python");
        BUILD_FILE_TO_FRAMEWORK.put("setup.py", "Python");
        BUILD_FILE_TO_FRAMEWORK.put("Cargo.toml", "Rust");
        BUILD_FILE_TO_FRAMEWORK.put("Dockerfile", "Docker");
        BUILD_FILE_TO_FRAMEWORK.put("docker-compose.yml", "Docker Compose");
        BUILD_FILE_TO_FRAMEWORK.put("Makefile", "Make");

        BUILD_FILE_TO_LANG.put("pom.xml", "Java");
        BUILD_FILE_TO_LANG.put("build.gradle", "Java");
        BUILD_FILE_TO_LANG.put("build.gradle.kts", "Java/Kotlin");
        BUILD_FILE_TO_LANG.put("package.json", "JavaScript/TypeScript");
        BUILD_FILE_TO_LANG.put("go.mod", "Go");
        BUILD_FILE_TO_LANG.put("requirements.txt", "Python");
        BUILD_FILE_TO_LANG.put("pyproject.toml", "Python");
        BUILD_FILE_TO_LANG.put("Cargo.toml", "Rust");

        EXT_TO_LANG.put(".java", "Java");
        EXT_TO_LANG.put(".kt", "Kotlin");
        EXT_TO_LANG.put(".py", "Python");
        EXT_TO_LANG.put(".go", "Go");
        EXT_TO_LANG.put(".rs", "Rust");
        EXT_TO_LANG.put(".ts", "TypeScript");
        EXT_TO_LANG.put(".tsx", "TypeScript (React)");
        EXT_TO_LANG.put(".js", "JavaScript");
        EXT_TO_LANG.put(".jsx", "JavaScript (React)");
        EXT_TO_LANG.put(".vue", "Vue");
        EXT_TO_LANG.put(".sql", "SQL");
        EXT_TO_LANG.put(".xml", "XML");
        EXT_TO_LANG.put(".yml", "YAML");
        EXT_TO_LANG.put(".yaml", "YAML");
        EXT_TO_LANG.put(".properties", "Properties");
    }

    @Override
    public String getName() { return "RepositoryAnalyzeAgent"; }

    @Override
    public String getDescription() { return "Analyzing repository structure, detecting languages and frameworks"; }

    @Override
    public int priority() { return 1; }

    @Override
    public AgentResult execute(AgentContext context) {
        List<PrFile> files = context.getPrInfo().getFiles();
        if (files == null || files.isEmpty()) {
            return AgentResult.success(getName(), "No files to analyze", Map.of());
        }

        Set<String> detectedLanguages = new LinkedHashSet<>();
        Set<String> detectedFrameworks = new LinkedHashSet<>();
        Map<String, Object> repoProfile = new LinkedHashMap<>();

        // 1. Detect from file extensions
        Map<String, Integer> extCounts = new HashMap<>();
        for (PrFile file : files) {
            String filename = file.getFilename();
            if (filename == null) continue;

            // Extract extension
            int dot = filename.lastIndexOf('.');
            if (dot > 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot).toLowerCase();
                extCounts.merge(ext, 1, Integer::sum);
                String lang = EXT_TO_LANG.get(ext);
                if (lang != null) detectedLanguages.add(lang);
            }

            // Detect build/config files from the filename
            String baseName = filename.contains("/")
                    ? filename.substring(filename.lastIndexOf('/') + 1)
                    : filename;

            String framework = BUILD_FILE_TO_FRAMEWORK.get(baseName);
            if (framework != null) {
                if (baseName.equals("package.json")) {
                    // package.json could be Node/React/Vue/Next.js — check the file content
                    String pkgFramework = detectFromPackageJson(file);
                    if (pkgFramework != null) detectedFrameworks.add(pkgFramework);
                } else {
                    detectedFrameworks.add(framework);
                }
            }

            String langFromBuild = BUILD_FILE_TO_LANG.get(baseName);
            if (langFromBuild != null) {
                for (String part : langFromBuild.split("/")) {
                    detectedLanguages.add(part.trim());
                }
            }
        }

        // 2. Detect project type
        String projectType = classifyProjectType(detectedLanguages, detectedFrameworks);

        // 3. Detect module categories
        Map<String, List<String>> modules = detectModules(files);

        // 4. Populate context
        context.setLanguages(new ArrayList<>(detectedLanguages));
        context.setFrameworks(new ArrayList<>(detectedFrameworks));
        context.setProjectType(projectType);

        repoProfile.put("languages", detectedLanguages);
        repoProfile.put("frameworks", detectedFrameworks);
        repoProfile.put("projectType", projectType);
        repoProfile.put("modules", modules);
        repoProfile.put("totalFiles", files.size());
        repoProfile.put("extensionDistribution", extCounts);

        context.setRepositoryContext(repoProfile);

        String summary = String.format("Detected %s project (%s) with %d files across %d modules",
                projectType,
                String.join(", ", detectedLanguages),
                files.size(),
                modules.size());

        log.info("{}: {}", getName(), summary);
        return AgentResult.success(getName(), summary, repoProfile);
    }

    private String detectFromPackageJson(PrFile file) {
        String content = file.getPatch();
        if (content == null) return "Node.js";

        List<String> frameworks = new ArrayList<>();
        if (content.contains("\"react\"") || content.contains("\"next\"") ||
                content.contains("\"@nextui-org\"") || content.contains("\"@radix-ui\"")) {
            frameworks.add("React");
        }
        if (content.contains("\"vue\"") || content.contains("\"nuxt\"") ||
                content.contains("\"@vue\"")) {
            frameworks.add("Vue");
        }
        if (content.contains("\"@angular\"")) {
            frameworks.add("Angular");
        }
        if (content.contains("\"next\"")) {
            frameworks.add("Next.js");
        }
        if (content.contains("\"express\"")) {
            frameworks.add("Express");
        }
        if (content.contains("\"nestjs\"") || content.contains("\"@nestjs\"")) {
            frameworks.add("NestJS");
        }
        if (content.contains("\"tailwindcss\"")) {
            frameworks.add("Tailwind CSS");
        }
        if (content.contains("\"typescript\"") || content.contains("\"@types/")) {
            frameworks.add("TypeScript");
        }

        return frameworks.isEmpty() ? "Node.js" : String.join(" + ", frameworks);
    }

    private String classifyProjectType(Set<String> languages, Set<String> frameworks) {
        boolean hasBackend = languages.stream().anyMatch(l ->
                l.equals("Java") || l.equals("Go") || l.equals("Python") || l.equals("Kotlin") || l.equals("Rust"));
        boolean hasFrontend = languages.stream().anyMatch(l ->
                l.contains("TypeScript") || l.contains("JavaScript") || l.equals("Vue"));
        boolean hasSql = languages.contains("SQL") || frameworks.stream().anyMatch(f -> f.contains("MyBatis"));

        if (hasBackend && hasFrontend) return "Fullstack";
        if (hasBackend) return "Backend";
        if (hasFrontend) return "Frontend";
        if (hasSql) return "Database";
        return "Unknown";
    }

    private Map<String, List<String>> detectModules(List<PrFile> files) {
        Map<String, List<String>> modules = new LinkedHashMap<>();

        for (PrFile file : files) {
            String filename = file.getFilename();
            if (filename == null) continue;

            String module = classifyFileModule(filename.toLowerCase());
            modules.computeIfAbsent(module, k -> new ArrayList<>()).add(filename);
        }

        return modules;
    }

    private String classifyFileModule(String filename) {
        if (filename.contains("controller") || filename.contains("handler") || filename.contains("router")) return "Controller";
        if (filename.contains("service") || filename.contains("usecase") || filename.contains("application")) return "Service";
        if (filename.contains("dao") || filename.contains("repository") || filename.contains("mapper")) return "Data Access";
        if (filename.contains("model") || filename.contains("entity") || filename.contains("domain") || filename.contains("dto")) return "Model";
        if (filename.contains("config") || filename.contains("properties") || filename.contains("yaml") || filename.contains("yml")) return "Config";
        if (filename.contains("test") || filename.contains("spec") || filename.contains("test.")) return "Test";
        if (filename.contains("util") || filename.contains("helper") || filename.contains("common")) return "Util";
        if (filename.endsWith(".sql")) return "Database";
        if (filename.endsWith(".vue") || filename.endsWith(".tsx") || filename.endsWith(".jsx")) return "Frontend Component";
        return "Other";
    }
}
