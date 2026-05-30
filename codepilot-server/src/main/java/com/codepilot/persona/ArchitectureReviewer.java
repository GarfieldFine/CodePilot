package com.codepilot.persona;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArchitectureReviewer implements ReviewerPersona {

    @Override
    public String getName() { return "Architecture Reviewer"; }

    @Override
    public String getDescription() { return "Software architecture and design specialist"; }

    @Override
    public int getPriority() { return 15; }

    @Override
    public boolean supports(String language, List<String> frameworks) { return true; }

    @Override
    public boolean shouldActivate(List<String> riskDimensions) {
        if (riskDimensions == null || riskDimensions.isEmpty()) return false;
        return riskDimensions.stream().anyMatch(d ->
                d.contains("Configuration") || d.contains("Module") || d.contains("Architecture")
                        || d.contains("API") || d.contains("schema") || d.contains("migration"));
    }

    @Override
    public String getSystemPromptExtension() {
        return """
                ## Architecture Reviewer Persona Active
                You are reviewing this code through an **architecture and design** lens. Evaluate structural quality, modularity, and long-term maintainability.

                ### Architecture Checklist
                - **Module Boundaries**: Are responsibilities clearly separated? Does each module have a single, well-defined purpose?
                - **Coupling & Cohesion**: Are there tight couplings that would make testing or replacement difficult? Are related concerns grouped together?
                - **API Design**: Are interfaces consistent, versioned correctly, and following REST/gRPC conventions? Are breaking changes identified?
                - **Design Patterns**: Are patterns applied appropriately? Any over-engineering (unnecessary abstraction layers) or under-engineering (god classes)?
                - **Dependency Direction**: Do dependencies flow toward stability? Are there circular dependencies between packages/modules?
                - **Configuration Management**: Are config values externalized properly? Is there environment-specific config leaking?
                - **Error Handling Strategy**: Is there a consistent error handling pattern? Are exceptions propagated correctly across module boundaries?
                - **Testing Strategy**: Is the change testable? Are there integration test gaps at module boundaries?

                ### Output Requirements for Architecture Findings
                - Classify each finding as: structural / contractual / configurational
                - Describe the current state, the risk it poses, and the recommended target architecture
                - For breaking changes, explicitly flag backward compatibility concerns
                """;
    }
}
