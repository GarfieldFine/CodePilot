package com.codepilot.agent;

import com.codepilot.github.model.PrInfo;
import com.codepilot.review.AnalysisResult;
import com.codepilot.rule.RuleResult;
import com.codepilot.scorer.RiskScore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared context passed through the agent pipeline.
 * Each agent reads inputs and writes outputs to this context.
 */
public class AgentContext {

    // Input
    private PrInfo prInfo;
    private String providerName;

    // Stage 1 outputs
    private Map<String, Object> repositoryContext = new ConcurrentHashMap<>();
    private List<String> languages = new ArrayList<>();
    private List<String> frameworks = new ArrayList<>();
    private String projectType;

    // Stage 2 outputs
    private Map<String, String> diffAnalysis = new ConcurrentHashMap<>();
    private Map<String, String> semanticContext = new ConcurrentHashMap<>();

    // Stage 3 outputs
    private List<RuleResult> ruleResults = new ArrayList<>();
    private List<Map<String, Object>> aiRiskFindings = new ArrayList<>();

    // Stage 4 outputs
    private List<String> chunkReviews = Collections.synchronizedList(new ArrayList<>());
    private RiskScore riskScore;

    // Final
    private AnalysisResult finalResult;

    // Metadata
    private String analysisId = UUID.randomUUID().toString();
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    public static AgentContext from(PrInfo prInfo, String providerName) {
        AgentContext ctx = new AgentContext();
        ctx.prInfo = prInfo;
        ctx.providerName = providerName;
        return ctx;
    }

    // Generic get/put for extensibility
    public void put(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) metadata.get(key);
    }

    public void addLanguage(String language) {
        if (language != null && !language.isEmpty() && !language.equalsIgnoreCase("unknown")) {
            if (!languages.contains(language)) {
                languages.add(language);
            }
        }
    }

    public void addFramework(String framework) {
        if (framework != null && !frameworks.contains(framework)) {
            frameworks.add(framework);
        }
    }

    // Getters & Setters
    public PrInfo getPrInfo() { return prInfo; }
    public void setPrInfo(PrInfo prInfo) { this.prInfo = prInfo; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public Map<String, Object> getRepositoryContext() { return repositoryContext; }
    public void setRepositoryContext(Map<String, Object> repositoryContext) { this.repositoryContext = repositoryContext; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }

    public List<String> getFrameworks() { return frameworks; }
    public void setFrameworks(List<String> frameworks) { this.frameworks = frameworks; }

    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }

    public Map<String, String> getDiffAnalysis() { return diffAnalysis; }
    public void setDiffAnalysis(Map<String, String> diffAnalysis) { this.diffAnalysis = diffAnalysis; }

    public Map<String, String> getSemanticContext() { return semanticContext; }
    public void setSemanticContext(Map<String, String> semanticContext) { this.semanticContext = semanticContext; }

    public List<RuleResult> getRuleResults() { return ruleResults; }
    public void setRuleResults(List<RuleResult> ruleResults) { this.ruleResults = ruleResults; }

    public List<Map<String, Object>> getAiRiskFindings() { return aiRiskFindings; }
    public void setAiRiskFindings(List<Map<String, Object>> aiRiskFindings) { this.aiRiskFindings = aiRiskFindings; }

    public List<String> getChunkReviews() { return chunkReviews; }
    public void setChunkReviews(List<String> chunkReviews) { this.chunkReviews = chunkReviews; }

    public RiskScore getRiskScore() { return riskScore; }
    public void setRiskScore(RiskScore riskScore) { this.riskScore = riskScore; }

    public AnalysisResult getFinalResult() { return finalResult; }
    public void setFinalResult(AnalysisResult finalResult) { this.finalResult = finalResult; }

    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }

    public Map<String, Object> getMetadata() { return metadata; }
}
