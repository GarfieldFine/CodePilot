export interface PrAnalyzeRequest {
  prUrl: string
  provider?: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface RiskFinding {
  file: string
  line: number | null
  level: RiskLevel
  category: string
  description: string
  suggestion: string
  // Explainability fields (Phase 7)
  why?: string
  evidence?: string
  impact?: string
  confidenceScore?: number
}

export interface ReviewSuggestion {
  category: string
  title: string
  description: string
  file: string | null
  line: number | null
  codeSnippet: string
  suggestedFix: string
  priority: string
  // Explainability fields (Phase 7)
  why?: string
  evidence?: string
  impact?: string
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface RuleResult {
  ruleName: string
  category: string
  riskLevel: RiskLevel
  file: string | null
  line: number | null
  message: string
  suggestion: string
  codeSnippet: string
  matched: boolean
}

export interface RiskScore {
  totalScore: number
  maxScore: number
  riskLevel: RiskLevel
  fileCountScore: number
  ruleFindingsScore: number
  moduleScore: number
  volumeScore: number
  aiScore: number
  ruleBreakdown: Record<RiskLevel, number>
  fileCount: number
  totalAdditions: number
  totalDeletions: number
}

export interface TokenUsage {
  totalTokens: number
  promptTokens: number
  completionTokens: number
  totalChunks: number
  successfulChunks: number
}

export interface ConfidenceScores {
  overallConfidence: number
  confidenceLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  factors: Record<string, number>
  summary: string
}

export interface RepositoryProfile {
  projectType: string
  languages: string[]
  frameworks: string[]
}

export interface FileAnalysis {
  filename: string
  language: string
  status: string
  additions: number
  deletions: number
  riskLevel: RiskLevel
  findings: RuleResult[]
}

export interface AnalysisResult {
  analysisId: string
  prTitle: string
  prUrl: string
  prNumber: number
  owner: string
  repo: string
  author: string
  changedFiles: number
  additions: number
  deletions: number
  ruleResults: RuleResult[]
  riskScore: RiskScore
  aiRawOutput: string
  status: AnalysisStatus
  fileAnalysis: FileAnalysis[]
  // Phase 2+ fields
  repositoryProfile?: RepositoryProfile
  confidenceScores?: ConfidenceScores
  agentTimeline?: string[]
  tokenUsage?: TokenUsage
}

export type AnalysisStatus =
  | 'PENDING'
  | 'FETCHING_PR'
  | 'ANALYZING_DIFF'
  | 'BUILDING_CONTEXT'
  | 'RUNNING_RULES'
  | 'AI_REVIEWING'
  | 'CALCULATING_SCORE'
  | 'COMPLETED'
  | 'FAILED'

export interface AgentEvent {
  type: 'agent_start' | 'agent_complete' | 'agent_error' | 'agent_skip' | 'pipeline_stage'
  agentName: string
  message: string
}

export interface SseEvent {
  type: 'status' | 'token' | 'ai_token' | 'complete' | 'error'
    | 'agent_start' | 'agent_complete' | 'agent_error' | 'agent_skip' | 'pipeline_stage'
  status?: string
  message?: string
  content?: string
  data?: AnalysisResult
  count?: number
  agentName?: string
}

export interface PrDetail {
  title: string
  author: string
  files: any[]
  commits: any[]
  additions: number
  deletions: number
  changedFiles: number
}
