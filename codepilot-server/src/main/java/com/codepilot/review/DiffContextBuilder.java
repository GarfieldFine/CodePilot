package com.codepilot.review;

import com.codepilot.github.model.PrFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DiffContextBuilder {

    private static final Pattern CLASS_DECL = Pattern.compile(
            "(public|private)?\\s*(abstract|final)?\\s*(class|interface|enum)\\s+(\\w+)");
    private static final Pattern METHOD_DECL = Pattern.compile(
            "(public|private|protected|static|final|synchronized|abstract|\\s)+" +
                    "[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\([^)]*\\)");
    private static final Pattern IMPORT_STMT = Pattern.compile(
            "import\\s+([\\w.]+(?:\\.[A-Z]\\w*)*(?:\\.\\*)?);");

    public Map<String, String> buildContext(List<PrFile> files, Map<String, String> fullContents) {
        Map<String, String> contextMap = new LinkedHashMap<>();

        for (PrFile file : files) {
            if (file.getFilename() == null || !isCodeFile(file.getFilename())) continue;

            String context = buildFileContext(file, fullContents.getOrDefault(file.getFilename(), ""));
            contextMap.put(file.getFilename(), context);
        }

        return contextMap;
    }

    public String buildOverallContext(List<PrFile> files, Map<String, String> fullContents) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PR Context Summary ===\n");
        sb.append("Changed files: ").append(files.size()).append("\n\n");

        // Group files by type
        Map<String, List<PrFile>> byType = files.stream()
                .collect(Collectors.groupingBy(f -> getFileType(f.getFilename())));

        for (var entry : byType.entrySet()) {
            sb.append("## ").append(entry.getKey()).append(" files:\n");
            for (PrFile f : entry.getValue()) {
                sb.append("- ").append(f.getFilename())
                        .append(" (").append(f.getLanguage()).append(", ")
                        .append(f.getAdditions()).append("+, ")
                        .append(f.getDeletions()).append("-)\n");
            }
        }
        sb.append("\n");

        // Include key class structures
        for (PrFile file : files) {
            if (file.getPatch() == null) continue;
            String fullContent = fullContents.get(file.getFilename());
            String fileStructure = extractStructure(file.getPatch(), fullContent);
            if (!fileStructure.isEmpty()) {
                sb.append("### ").append(file.getFilename()).append("\n");
                sb.append(fileStructure).append("\n");
            }
        }

        return sb.toString();
    }

    public String buildMethodContext(String methodName, String fileContent) {
        if (fileContent == null || methodName == null) return "";
        Pattern methodPattern = Pattern.compile(
                "((?:public|private|protected|static|final|synchronized|\\s)+" +
                        "[\\w<>\\[\\],\\s]+\\s+" + Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w\\s,]+)?\\s*\\{)");
        Matcher m = methodPattern.matcher(fileContent);
        if (m.find()) {
            return extractBlock(fileContent, m.end() - 1);
        }
        return "";
    }

    public Set<String> extractImports(String fileContent) {
        Set<String> imports = new HashSet<>();
        if (fileContent == null) return imports;

        Matcher m = IMPORT_STMT.matcher(fileContent);
        while (m.find()) {
            imports.add(m.group(1));
        }

        // Include Service/Mapper/Repository dependencies
        return imports.stream()
                .filter(imp -> imp.contains("Service") || imp.contains("Mapper")
                        || imp.contains("Repository") || imp.contains("DAO")
                        || imp.contains("DTO") || imp.contains("Entity")
                        || imp.contains("Util") || imp.contains("Helper"))
                .collect(Collectors.toSet());
    }

    private String buildFileContext(PrFile file, String fullContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(file.getFilename()).append("\n");
        sb.append("Status: ").append(file.getStatus())
                .append(" (+").append(file.getAdditions())
                .append(" -").append(file.getDeletions()).append(")\n\n");

        if (fullContent != null && !fullContent.isEmpty()) {
            sb.append(extractStructure(fullContent, fullContent));
        } else if (file.getPatch() != null) {
            sb.append(extractStructure(file.getPatch(), null));
        }

        return sb.toString();
    }

    private String extractStructure(String content, String fullContent) {
        StringBuilder sb = new StringBuilder();

        // Class declaration
        Matcher classMatcher = CLASS_DECL.matcher(content);
        if (classMatcher.find()) {
            sb.append("Class: ").append(classMatcher.group(4)).append("\n");
        }

        // Method declarations
        Set<String> methods = new LinkedHashSet<>();
        Matcher methodMatcher = METHOD_DECL.matcher(content);
        int count = 0;
        while (methodMatcher.find() && count < 20) {
            String methodName = methodMatcher.group(2);
            if (!"if".equals(methodName) && !"while".equals(methodName)
                    && !"for".equals(methodName) && !"switch".equals(methodName)
                    && !"return".equals(methodName) && !"throw".equals(methodName)
                    && !"new".equals(methodName) && !"synchronized".equals(methodName)
                    && !"catch".equals(methodName) && !"try".equals(methodName)) {
                methods.add(methodName);
            }
            count++;
        }
        if (!methods.isEmpty()) {
            sb.append("Methods: ").append(String.join(", ", methods)).append("\n");
        }

        // Imports for this file
        String sourceForImports = fullContent != null ? fullContent : content;
        Set<String> keyImports = extractImports(sourceForImports);
        if (!keyImports.isEmpty()) {
            sb.append("Dependencies: ");
            sb.append(String.join(", ", keyImports.stream().limit(10).collect(Collectors.toSet())));
            sb.append("\n");
        }

        return sb.toString();
    }

    private String extractBlock(String content, int openBracePos) {
        int braceCount = 0;
        int start = Math.max(0, openBracePos - 100);
        int end = Math.min(content.length(), openBracePos + 3000);

        for (int i = openBracePos; i < end && braceCount >= 0; i++) {
            if (content.charAt(i) == '{') braceCount++;
            if (content.charAt(i) == '}') {
                braceCount--;
                if (braceCount == 0) {
                    end = i + 1;
                    break;
                }
            }
        }

        return content.substring(start, Math.min(end, content.length()));
    }

    private String getFileType(String filename) {
        if (filename == null) return "other";
        String lower = filename.toLowerCase();
        if (lower.contains("controller")) return "controller";
        if (lower.contains("service") || lower.contains("impl")) return "service";
        if (lower.contains("mapper") || lower.contains("dao") || lower.contains("repository")) return "data_access";
        if (lower.contains("entity") || lower.contains("model") || lower.contains("domain")) return "model";
        if (lower.contains("dto") || lower.contains("vo") || lower.contains("request") || lower.contains("response"))
            return "dto";
        if (lower.contains("config") || lower.contains("properties")) return "config";
        if (lower.contains("util") || lower.contains("helper") || lower.contains("common")) return "util";
        if (lower.contains("test")) return "test";
        if (lower.contains(".sql") || lower.contains("migration")) return "database";
        return "other";
    }

    private boolean isCodeFile(String filename) {
        if (filename == null) return false;
        return filename.endsWith(".java") || filename.endsWith(".kt") || filename.endsWith(".py")
                || filename.endsWith(".ts") || filename.endsWith(".tsx") || filename.endsWith(".js")
                || filename.endsWith(".go") || filename.endsWith(".rs") || filename.endsWith(".vue");
    }
}
