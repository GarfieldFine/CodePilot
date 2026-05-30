package com.codepilot.chunk;

/**
 * Classifies PR files into semantic categories for smarter chunk grouping.
 * Priority ordering: lower number = higher review priority.
 */
public enum ChunkType {

    CONFIG(1),
    SQL(2),
    SERVICE(3),
    CONTROLLER(4),
    DAO(5),
    FRONTEND(6),
    TEST(7),
    OTHER(8);

    private final int priority;

    ChunkType(int priority) { this.priority = priority; }

    public int getPriority() { return priority; }

    public static ChunkType classify(String filename) {
        if (filename == null) return OTHER;
        String lower = filename.toLowerCase();

        if (isConfig(lower)) return CONFIG;
        if (isSql(lower)) return SQL;
        if (isTest(lower)) return TEST;
        if (isController(lower)) return CONTROLLER;
        if (isService(lower)) return SERVICE;
        if (isDao(lower)) return DAO;
        if (isFrontend(lower)) return FRONTEND;

        return OTHER;
    }

    private static boolean isConfig(String lower) {
        return lower.endsWith(".yml") || lower.endsWith(".yaml")
                || lower.endsWith(".properties") || lower.endsWith(".xml")
                || lower.endsWith(".conf") || lower.endsWith(".toml")
                || lower.contains("application") || lower.contains("docker")
                || lower.contains("nginx") || lower.contains("env")
                || lower.endsWith("pom.xml") || lower.endsWith("build.gradle")
                || lower.equals("package.json") || lower.equals("go.mod");
    }

    private static boolean isSql(String lower) {
        return lower.endsWith(".sql") || lower.contains("migration")
                || lower.contains("schema") || lower.contains("ddl")
                || lower.contains("dml");
    }

    private static boolean isTest(String lower) {
        return lower.contains("test") || lower.contains("spec")
                || lower.endsWith(".test.ts") || lower.endsWith(".test.js")
                || lower.endsWith(".spec.ts") || lower.endsWith(".spec.js")
                || (lower.contains("test") && lower.endsWith(".java"));
    }

    private static boolean isController(String lower) {
        return lower.contains("controller") || lower.contains("handler")
                || lower.contains("endpoint") || lower.endsWith("controller.java")
                || lower.endsWith("handler.java") || lower.contains("resource");
    }

    private static boolean isService(String lower) {
        return lower.contains("service") || lower.contains("manager")
                || lower.endsWith("service.java") || lower.endsWith("manager.java")
                || lower.contains("usecase") || lower.contains("usecase.java");
    }

    private static boolean isDao(String lower) {
        return lower.contains("dao") || lower.contains("repository")
                || lower.contains("mapper") || lower.endsWith("dao.java")
                || lower.endsWith("repository.java") || lower.endsWith("mapper.java");
    }

    private static boolean isFrontend(String lower) {
        return lower.endsWith(".vue") || lower.endsWith(".tsx")
                || lower.endsWith(".jsx") || lower.endsWith(".ts")
                || lower.endsWith(".js") || lower.endsWith(".css")
                || lower.endsWith(".scss") || lower.endsWith(".less")
                || lower.endsWith(".html");
    }
}
