<script setup lang="ts">
import type { AgentEvent } from '@/types'
import type { AnalysisStatus } from '@/types'

const props = defineProps<{
  agentEvents: Array<AgentEvent & { time: number }>
  activeAgents: string[]
  currentStage: string
  status: AnalysisStatus | ''
}>()

const agentLabels: Record<string, string> = {
  'RepositoryAnalyzeAgent': '仓库结构分析',
  'DiffAnalyzeAgent': 'Diff 变更分析',
  'ContextBuildAgent': '代码上下文构建',
  'RiskDetectAgent': '风险模式检测',
  'ReviewGenerateAgent': 'AI 智能评审',
  'SummaryMergeAgent': '结果汇总合并'
}

const stageLabels: Record<string, string> = {
  'Understanding Repository': '理解仓库结构',
  'Detecting Risks': '检测代码风险',
  'Generating AI Review': 'AI 生成评审',
  'Merging Analysis': '汇总分析结果'
}

function getAgentLabel(name: string): string {
  return agentLabels[name] || name
}

function getStageLabel(name: string): string {
  return stageLabels[name] || name
}

function isAgentActive(name: string): boolean {
  return props.activeAgents.includes(name)
}

function isAgentDone(name: string): boolean {
  return props.agentEvents.some(e => e.type === 'agent_complete' && e.agentName === name)
}

function isAgentFailed(name: string): boolean {
  return props.agentEvents.some(e => e.type === 'agent_error' && e.agentName === name)
}

// Get unique agents from events while preserving order
function getUniqueAgents(): string[] {
  const seen = new Set<string>()
  const agents: string[] = []
  for (const event of props.agentEvents) {
    if (!seen.has(event.agentName) && event.agentName) {
      seen.add(event.agentName)
      agents.push(event.agentName)
    }
  }
  return agents
}
</script>

<template>
  <div class="agent-timeline">
    <div class="timeline-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="3"/><path d="M12 1v4m0 14v4M1 12h4m14 0h4"/>
      </svg>
      <span>AI Agent 工作流</span>
      <span class="live-badge" v-if="status && status !== 'COMPLETED' && status !== 'FAILED'">LIVE</span>
    </div>

    <!-- Stage indicators -->
    <div v-if="currentStage" class="current-stage">
      <span class="stage-dot"></span>
      {{ getStageLabel(currentStage) || currentStage }}
    </div>

    <!-- Agent list -->
    <div class="agent-list" v-if="agentEvents.length > 0">
      <div
        v-for="name in getUniqueAgents()"
        :key="name"
        class="agent-item"
        :class="{
          active: isAgentActive(name),
          done: isAgentDone(name),
          failed: isAgentFailed(name)
        }"
      >
        <div class="agent-indicator">
          <svg v-if="isAgentDone(name)" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--success)" stroke-width="2.5">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
          <svg v-else-if="isAgentFailed(name)" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--danger)" stroke-width="2.5">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
          <span v-else-if="isAgentActive(name)" class="active-ring"></span>
          <span v-else class="pending-dot"></span>
        </div>
        <div class="agent-info">
          <span class="agent-name">{{ getAgentLabel(name) }}</span>
        </div>
        <span v-if="isAgentActive(name)" class="agent-status-label running">运行中</span>
        <span v-else-if="isAgentDone(name)" class="agent-status-label done">完成</span>
        <span v-else-if="isAgentFailed(name)" class="agent-status-label failed">失败</span>
        <span v-else class="agent-status-label waiting">等待</span>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else class="empty-state">
      <div class="empty-pulse"></div>
      <p>等待 Agent 启动...</p>
    </div>
  </div>
</template>

<style scoped>
.agent-timeline {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 12px;
}

.timeline-header svg {
  color: var(--primary);
}

.live-badge {
  margin-left: auto;
  font-size: 10px;
  font-weight: 700;
  color: var(--success);
  background: rgba(52, 211, 153, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
  animation: live-pulse 2s ease-in-out infinite;
}

@keyframes live-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.current-stage {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--primary);
  font-weight: 500;
  padding: 6px 10px;
  background: rgba(99, 102, 241, 0.06);
  border-radius: var(--radius-sm);
  margin-bottom: 10px;
}

.stage-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse-dot 1.5s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.5); }
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.agent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
  font-size: 13px;
}

.agent-item.active {
  background: rgba(99, 102, 241, 0.08);
}

.agent-item.done {
  opacity: 0.6;
}

.agent-item.failed {
  background: rgba(248, 113, 113, 0.06);
}

.agent-indicator {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.active-ring {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--primary);
  animation: ring-rotate 1.5s linear infinite;
  position: relative;
}

.active-ring::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  right: 2px;
  bottom: 2px;
  border-radius: 50%;
  background: var(--primary);
  animation: ring-pulse 1.5s ease-in-out infinite;
}

@keyframes ring-rotate {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes ring-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.pending-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  opacity: 0.3;
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  color: var(--text-primary);
  font-weight: 500;
  font-size: 12px;
}

.agent-item.done .agent-name {
  color: var(--text-secondary);
}

.agent-status-label {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 8px;
  flex-shrink: 0;
}

.agent-status-label.running {
  color: var(--primary);
  background: rgba(99, 102, 241, 0.12);
}

.agent-status-label.done {
  color: var(--success);
  background: rgba(52, 211, 153, 0.1);
}

.agent-status-label.failed {
  color: var(--danger);
  background: rgba(248, 113, 113, 0.1);
}

.agent-status-label.waiting {
  color: var(--text-muted);
  background: rgba(107, 107, 128, 0.1);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;
  gap: 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.empty-pulse {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid var(--border);
  border-top-color: var(--primary);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
