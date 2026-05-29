package com.codepilot.repository;

import com.codepilot.github.model.PrFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Detects programming languages from file extensions and build file analysis.
 * Produces a ranked list of languages sorted by file count.
 */
@Slf4j
@Component
public class LanguageDetector {

    private static final Map<String, String> EXT_TO_LANGUAGE = new LinkedHashMap<>();
    private static final Map<String, String> BUILD_FILE_TO_LANG = new LinkedHashMap<>();
    private static final Set<String> CODE_EXTENSIONS = new HashSet<>();

    static {
        EXT_TO_LANGUAGE.put(".java", "Java");
        EXT_TO_LANGUAGE.put(".kt", "Kotlin");
        EXT_TO_LANGUAGE.put(".scala", "Scala");
        EXT_TO_LANGUAGE.put(".groovy", "Groovy");
        EXT_TO_LANGUAGE.put(".py", "Python");
        EXT_TO_LANGUAGE.put(".go", "Go");
        EXT_TO_LANGUAGE.put(".rs", "Rust");
        EXT_TO_LANGUAGE.put(".rb", "Ruby");
        EXT_TO_LANGUAGE.put(".php", "PHP");
        EXT_TO_LANGUAGE.put(".c", "C");
        EXT_TO_LANGUAGE.put(".cpp", "C++");
        EXT_TO_LANGUAGE.put(".h", "C/C++ Header");
        EXT_TO_LANGUAGE.put(".cs", "C#");
        EXT_TO_LANGUAGE.put(".swift", "Swift");
        EXT_TO_LANGUAGE.put(".ts", "TypeScript");
        EXT_TO_LANGUAGE.put(".tsx", "TypeScript");
        EXT_TO_LANGUAGE.put(".js", "JavaScript");
        EXT_TO_LANGUAGE.put(".jsx", "JavaScript");
        EXT_TO_LANGUAGE.put(".mjs", "JavaScript");
        EXT_TO_LANGUAGE.put(".vue", "Vue");
        EXT_TO_LANGUAGE.put(".svelte", "Svelte");
        EXT_TO_LANGUAGE.put(".sql", "SQL");
        EXT_TO_LANGUAGE.put(".xml", "XML");
        EXT_TO_LANGUAGE.put(".yml", "YAML");
        EXT_TO_LANGUAGE.put(".yaml", "YAML");
        EXT_TO_LANGUAGE.put(".json", "JSON");
        EXT_TO_LANGUAGE.put(".properties", "Properties");
        EXT_TO_LANGUAGE.put(".css", "CSS");
        EXT_TO_LANGUAGE.put(".scss", "SCSS");
        EXT_TO_LANGUAGE.put(".less", "Less");
        EXT_TO_LANGUAGE.put(".html", "HTML");
        EXT_TO_LANGUAGE.put(".dockerfile", "Dockerfile");

        BUILD_FILE_TO_LANG.put("pom.xml", "Java");
        BUILD_FILE_TO_LANG.put("build.gradle", "Java");
        BUILD_FILE_TO_LANG.put("build.gradle.kts", "Java/Kotlin");
        BUILD_FILE_TO_LANG.put("package.json", "JavaScript/TypeScript");
        BUILD_FILE_TO_LANG.put("go.mod", "Go");
        BUILD_FILE_TO_LANG.put("requirements.txt", "Python");
        BUILD_FILE_TO_LANG.put("pyproject.toml", "Python");
        BUILD_FILE_TO_LANG.put("setup.py", "Python");
        BUILD_FILE_TO_LANG.put("Cargo.toml", "Rust");
        BUILD_FILE_TO_LANG.put("Gemfile", "Ruby");
        BUILD_FILE_TO_LANG.put("composer.json", "PHP");

        CODE_EXTENSIONS.addAll(Set.of(".java", ".kt", ".py", ".go", ".rs", ".ts", ".tsx",
                ".js", ".jsx", ".vue", ".svelte", ".rb", ".php", ".cs", ".swift", ".scala",
                ".groovy", ".c", ".cpp", ".h"));
    }

    /**
     * Detect languages from PR file list. Returns languages ranked by file count.
     */
    public List<String> detectFromFiles(List<PrFile> files) {
        Map<String, Integer> langCounts = new LinkedHashMap<>();

        for (PrFile file : files) {
            String filename = file.getFilename();
            if (filename == null) continue;

            String baseName = filename.contains("/")
                    ? filename.substring(filename.lastIndexOf('/') + 1)
                    : filename;

            // Check build files
            String langFromBuild = BUILD_FILE_TO_LANG.get(baseName);
            if (langFromBuild != null) {
                for (String part : langFromBuild.split("/")) {
                    langCounts.merge(part.trim(), 5, Integer::sum); // build files carry more weight
                }
            }

            // Check extension
            int dot = filename.lastIndexOf('.');
            if (dot > 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot).toLowerCase();
                String lang = EXT_TO_LANGUAGE.get(ext);
                if (lang != null) {
                    langCounts.merge(lang, 1, Integer::sum);
                }
            }
        }

        // Sort by count descending
        return langCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Detect language from a single filename.
     */
    public String detectFromFilename(String filename) {
        if (filename == null) return "Unknown";
        int dot = filename.lastIndexOf('.');
        if (dot > 0 && dot < filename.length() - 1) {
            String ext = filename.substring(dot).toLowerCase();
            return EXT_TO_LANGUAGE.getOrDefault(ext, ext.substring(1));
        }
        return "Unknown";
    }

    /**
     * Check if a file is a code file (not config/data).
     */
    public boolean isCodeFile(String filename) {
        if (filename == null) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return CODE_EXTENSIONS.contains(filename.substring(dot).toLowerCase());
    }

    /**
     * Build extension distribution map.
     */
    public Map<String, Integer> buildExtensionDistribution(List<PrFile> files) {
        Map<String, Integer> dist = new HashMap<>();
        for (PrFile file : files) {
            String filename = file.getFilename();
            if (filename == null) continue;
            int dot = filename.lastIndexOf('.');
            if (dot > 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot).toLowerCase();
                dist.merge(ext, 1, Integer::sum);
            }
        }
        return dist;
    }
}
