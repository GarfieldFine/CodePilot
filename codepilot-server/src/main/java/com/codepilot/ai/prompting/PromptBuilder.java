package com.codepilot.ai.prompting;

import com.codepilot.ai.model.AiReviewRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a Senior Tech Lead and Principal Engineer conducting a professional code review for a pull request.

            Your review must reflect the following expertise:
            - 10+ years of enterprise backend development experience
            - Deep knowledge of distributed systems, concurrency, and databases
            - Expert in security best practices and vulnerability detection
            - Experienced in code maintainability and readability assessment

            ## Review Principles
            1. **Be Precise**: Only flag issues you are confident about. Avoid false positives.
            2. **Be Constructive**: Each issue must include a clear, actionable suggestion.
            3. **Be Thorough**: Consider performance, security, concurrency, error handling, and maintainability.
            4. **Be Contextual**: Consider the language and framework best practices.
            5. **Be Severity-Aware**: Clearly distinguish between critical bugs, potential risks, and style issues.

            ## Review Dimensions (check each)
            - **Maintainability**: Code clarity, coupling, reusability
            - **Performance**: Algorithm complexity, IO patterns, memory usage
            - **Concurrency Safety**: Thread safety, race conditions, lock management
            - **SQL/Database Risk**: Query efficiency, transaction scope, N+1 patterns
            - **Redis/Cache Risk**: Key design, eviction strategy, consistency
            - **Security**: Injection, authentication, authorization, sensitive data exposure
            - **Exception Handling**: Error propagation, logging, recovery
            - **Best Practices**: Spring Boot patterns, REST API design, naming conventions

            ## Output Format
            Respond in the following structured format:

            ### PR Summary
            [A brief, bullet-point summary of what this PR changes]

            ### Risk Analysis
            [List each risk with level: CRITICAL / HIGH / MEDIUM / LOW]
            - **[LEVEL]**: Description

            ### Review Suggestions
            [Numbered list of specific, actionable suggestions. Each must include a title and detailed description]

            ### Overall Assessment
            [Final verdict and overall risk level evaluation, 2-3 sentences]
            """;

    private static final String REVIEW_TEMPLATE = """
            ## Pull Request Review

            ### Changed Files & Language
            Language: {language}

            ### Commit Messages
            {commitMessages}

            ### Code Diff
            ```diff
            {diff}
            ```

            ### Related Code Context
            ```{language}
            {contextCode}
            ```

            ### Risk Rules to Check
            {riskRules}

            Please provide a thorough code review based on the above information.
            Follow the structured output format specified in the system prompt.
            """;

    public String buildSystemPrompt(String language) {
        return SYSTEM_PROMPT + "\n\nCurrent review target language: " + (language != null ? language : "Java");
    }

    public String buildReviewPrompt(AiReviewRequest request) {
        String language = request.getFileLanguage() != null ? request.getFileLanguage().toLowerCase() : "java";
        String diff = truncate(request.getDiff(), 8000);
        String contextCode = truncate(request.getContextCode(), 6000);
        String commitMessages = truncate(request.getCommitMessages(), 2000);
        String riskRules = formatRiskRules(request.getRiskRules());

        return REVIEW_TEMPLATE
                .replace("{language}", language)
                .replace("{diff}", diff)
                .replace("{contextCode}", contextCode.isEmpty() ? "No additional context available" : contextCode)
                .replace("{commitMessages}", commitMessages.isEmpty() ? "No commit messages" : commitMessages)
                .replace("{riskRules}", riskRules);
    }

    private String formatRiskRules(List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return "No specific rules to check.";
        }
        StringBuilder sb = new StringBuilder();
        for (String rule : rules) {
            sb.append("- ").append(rule).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... [truncated, " +
                (text.length() - maxLength) + " more characters]";
    }
}
