package com.codepilot.persona;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityReviewer implements ReviewerPersona {

    @Override
    public String getName() { return "Security Reviewer"; }

    @Override
    public String getDescription() { return "OWASP-focused security audit specialist"; }

    @Override
    public int getPriority() { return 5; }

    @Override
    public boolean supports(String language, List<String> frameworks) { return true; }

    @Override
    public boolean shouldActivate(List<String> riskDimensions) {
        if (riskDimensions == null || riskDimensions.isEmpty()) return true;
        return riskDimensions.stream().anyMatch(d ->
                d.contains("SQL") || d.contains("Security") || d.contains("auth")
                        || d.contains("token") || d.contains("secret") || d.contains("Config"));
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Security Reviewer Persona Active
                You are reviewing this code through a **security-first** lens. Prioritize security findings above all else.

                ### Security Checklist
                - **Injection Risks**: SQL injection, command injection, XSS, SSTI, path traversal
                - **Authentication & Authorization**: Missing auth checks, privilege escalation, JWT weaknesses, session fixation
                - **Sensitive Data**: Hardcoded secrets, API keys in code, PII exposure in logs, unencrypted credentials
                - **Input Validation**: Missing validation on user-controlled input, type confusion, mass assignment
                - **Cryptography**: Weak algorithms (MD5, SHA1, DES), hardcoded IVs, insecure random, missing salt
                - **Secure Communication**: Missing TLS verification, HTTP instead of HTTPS for sensitive endpoints
                - **Dependency Security**: Known vulnerable dependencies, unpinned versions, supply chain risks

                ### Output Requirements for Security Findings
                - Rate each security finding with CVSS-like severity: CRITICAL / HIGH / MEDIUM / LOW
                - Include CWE reference where applicable (e.g., CWE-89 for SQL injection)
                - For each finding, explicitly state: attack vector, prerequisites, and worst-case impact
                """;
    }
}
