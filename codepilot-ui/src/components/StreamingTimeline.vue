<script setup lang="ts">
import type { AnalysisStatus } from '@/types'

defineProps<{
  history: Array<{ status: string; message: string; time: number }>
  currentStatus: AnalysisStatus | ''
}>()

const statusIcons: Record<string, string> = {
  FETCHING_PR: '⬇',
  ANALYZING_DIFF: '🔍',
  BUILDING_CONTEXT: '🧩',
  RUNNING_RULES: '⚙️',
  AI_REVIEWING: '🤖',
  CALCULATING_SCORE: '📊',
  COMPLETED: '✅',
  FAILED: '❌'
}

const statusLabels: Record<string, string> = {
  FETCHING_PR: '获取 PR 信息',
  ANALYZING_DIFF: '分析 Diff 变更',
  BUILDING_CONTEXT: '构建代码上下文',
  RUNNING_RULES: '运行风险规则引擎',
  AI_REVIEWING: 'AI 智能评审中',
  CALCULATING_SCORE: '计算风险评分',
  COMPLETED: '分析完成',
  FAILED: '分析失败'
}
</script>

<template>
  <div class="timeline">
    <div
      v-for="(step, i) in history"
      :key="i"
      class="step"
      :class="{ active: i === history.length - 1 && currentStatus !== 'COMPLETED' && currentStatus !== 'FAILED', done: currentStatus === 'COMPLETED' || i < history.length - 1 }"
    >
      <div class="step-icon">{{ statusIcons[step.status] || '•' }}</div>
      <div class="step-content">
        <span class="step-status">{{ statusLabels[step.status] || step.status }}</span>
        <span v-if="step.message" class="step-message">{{ step.message }}</span>
      </div>
      <span class="step-time">{{ new Date(step.time).toLocaleTimeString() }}</span>
    </div>
  </div>
</template>

<style scoped>
.timeline {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
  font-size: 13px;
}

.step.active {
  background: rgba(99, 102, 241, 0.08);
}

.step-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.step.done .step-icon {
  opacity: 0.5;
}

.step-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.step-status {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 12px;
}

.step.active .step-status {
  color: var(--primary);
}

.step.done .step-status {
  color: var(--text-muted);
}

.step-message {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 1px;
}

.step-time {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  flex-shrink: 0;
}
</style>
