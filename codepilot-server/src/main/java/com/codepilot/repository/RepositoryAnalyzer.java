package com.codepilot.repository;

import com.codepilot.github.GitHubClient;
import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Main entry point for repository analysis. Orchestrates LanguageDetector,
 * FrameworkDetector, and ModuleClassifier to produce a comprehensive
 * RepositoryContext profile.
 *
 * Can operate both synchronously (from PR files already fetched)
 * and asynchronously (fetching additional repo metadata from GitHub).
 */
@Slf4j
@Service
public class RepositoryAnalyzer {

    private final LanguageDetector languageDetector;
    private final FrameworkDetector frameworkDetector;
    private final ModuleClassifier moduleClassifier;
    private final GitHubClient gitHubClient;

    public RepositoryAnalyzer(LanguageDetector languageDetector,
                               FrameworkDetector frameworkDetector,
                               ModuleClassifier moduleClassifier,
                               GitHubClient gitHubClient) {
        this.languageDetector = languageDetector;
        this.frameworkDetector = frameworkDetector;
        this.moduleClassifier = moduleClassifier;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Analyze repository from PR info (sync, uses already-fetched file data).
     */
    public RepositoryContext analyze(PrInfo prInfo) {
        List<PrFile> files = prInfo.getFiles();
        if (files == null || files.isEmpty()) {
            return RepositoryContext.builder()
                    .languages(List.of())
                    .frameworks(List.of())
                    .projectType("Unknown")
                    .totalFiles(0)
                    .modules(Map.of())
                    .buildFiles(Map.of())
                    .confidence(0.0)
                    .build();
        }

        List<String> languages = languageDetector.detectFromFiles(files);
        List<String> frameworks = frameworkDetector.detect(files);
        Map<String, List<String>> modules = moduleClassifier.classify(files);
        Map<String, Integer> extDist = languageDetector.buildExtensionDistribution(files);
        String projectType = classifyProjectType(languages, frameworks, modules);

        // Calculate detection confidence
        double confidence = computeConfidence(languages, frameworks, modules, files.size());

        log.info("Repository analysis: type={}, languages={}, frameworks={}, modules={}, confidence={}%",
                projectType, languages, frameworks, modules.keySet(), String.format("%.0f", confidence * 100));

        return RepositoryContext.builder()
                .languages(languages)
                .frameworks(frameworks)
                .projectType(projectType)
                .totalFiles(files.size())
                .extensionDistribution(extDist)
                .modules(modules)
                .buildFiles(detectBuildFiles(files))
                .confidence(confidence)
                .build();
    }

    /**
     * Analyze repository asynchronously, also fetching repo root file tree from GitHub.
     */
    public Mono<RepositoryContext> analyzeAsync(PrInfo prInfo) {
        // Start with sync analysis from PR files
        RepositoryContext syncResult = analyze(prInfo);

        // Fetch root directory to improve framework detection
        return gitHubClient.fetchRepoContents(prInfo.getOwner(), prInfo.getRepo())
                .map(rootFiles -> {
                    // Check for build files not changed in this PR
                    Set<String> additionalFrameworks = new LinkedHashSet<>();
                    for (String rootFile : rootFiles) {
                        String baseName = rootFile.contains("/")
                                ? rootFile.substring(rootFile.lastIndexOf('/') + 1)
                                : rootFile;

                        switch (baseName) {
                            case "pom.xml" -> additionalFrameworks.add("Maven");
                            case "build.gradle", "build.gradle.kts" -> additionalFrameworks.add("Gradle");
                            case "package.json" -> additionalFrameworks.add("Node.js");
                            case "go.mod" -> additionalFrameworks.add("Go Modules");
                            case "requirements.txt", "pyproject.toml" -> additionalFrameworks.add("Python");
                            case "Cargo.toml" -> additionalFrameworks.add("Rust");
                            case "Dockerfile" -> additionalFrameworks.add("Docker");
                            case "Makefile" -> additionalFrameworks.add("Make");
                        }
                    }

                    if (!additionalFrameworks.isEmpty()) {
                        List<String> mergedFrameworks = new ArrayList<>(syncResult.getFrameworks());
                        for (String fw : additionalFrameworks) {
                            if (!mergedFrameworks.contains(fw)) {
                                mergedFrameworks.add(fw);
                            }
                        }
                        syncResult.setFrameworks(mergedFrameworks);
                        syncResult.setConfidence(Math.min(1.0, syncResult.getConfidence() + 0.1));
                    }

                    return syncResult;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch repo contents, using sync analysis: {}", e.getMessage());
                    return Mono.just(syncResult);
                })
                .defaultIfEmpty(syncResult);
    }

    private String classifyProjectType(List<String> languages, List<String> frameworks,
                                        Map<String, List<String>> modules) {
        String langStr = String.join(",", languages).toLowerCase();
        String fwStr = String.join(",", frameworks).toLowerCase();

        boolean hasBackend = langStr.contains("java") || langStr.contains("go") ||
                langStr.contains("python") || langStr.contains("kotlin") ||
                langStr.contains("rust") || langStr.contains("ruby") ||
                fwStr.contains("spring") || fwStr.contains("express") ||
                fwStr.contains("django") || fwStr.contains("flask");

        boolean hasFrontend = langStr.contains("typescript") || langStr.contains("javascript") ||
                langStr.contains("vue") || fwStr.contains("react") ||
                fwStr.contains("vue") || fwStr.contains("angular") ||
                fwStr.contains("next.js") || fwStr.contains("nuxt");

        boolean hasDatabase = langStr.contains("sql") || fwStr.contains("mybatis") ||
                fwStr.contains("jpa") || fwStr.contains("flyway") ||
                fwStr.contains("liquibase");

        boolean hasDevops = fwStr.contains("docker") || fwStr.contains("jenkins") ||
                fwStr.contains("github actions") || fwStr.contains("gitlab ci");

        if (hasBackend && hasFrontend) return "Fullstack";
        if (hasBackend) return "Backend";
        if (hasFrontend) return "Frontend";
        if (hasDatabase) return "Database";
        if (hasDevops) return "DevOps";
        return "Unknown";
    }

    private double computeConfidence(List<String> languages, List<String> frameworks,
                                      Map<String, List<String>> modules, int totalFiles) {
        double score = 0.0;
        if (!languages.isEmpty()) score += 0.3;
        if (!frameworks.isEmpty()) score += 0.3;
        if (modules.size() >= 2) score += 0.2;
        if (totalFiles > 10) score += 0.1;
        if (languages.size() >= 2) score += 0.05;
        if (frameworks.size() >= 2) score += 0.05;
        return Math.min(1.0, score);
    }

    private Map<String, String> detectBuildFiles(List<PrFile> files) {
        Map<String, String> buildFiles = new LinkedHashMap<>();
        Set<String> buildFileNames = Set.of(
                "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
                "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
                "go.mod", "go.sum", "requirements.txt", "pyproject.toml", "setup.py",
                "Cargo.toml", "Cargo.lock", "Makefile", "Dockerfile",
                "docker-compose.yml", "docker-compose.yaml"
        );

        for (PrFile file : files) {
            String filename = file.getFilename();
            if (filename == null) continue;
            String baseName = filename.contains("/")
                    ? filename.substring(filename.lastIndexOf('/') + 1)
                    : filename;
            if (buildFileNames.contains(baseName)) {
                buildFiles.put(baseName, file.getStatus());
            }
        }
        return buildFiles;
    }
}
