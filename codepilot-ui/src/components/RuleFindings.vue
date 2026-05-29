<script setup lang="ts">
import type { RuleResult } from '@/types'

defineProps<{ findings: RuleResult[] }>()

const riskConfig: Record<string, { color: string; bg: string }> = {
  LOW: { color: 'var(--risk-low)', bg: 'rgba(52,211,153,0.08)' },
  MEDIUM: { color: 'var(--risk-medium)', bg: 'rgba(251,191,36,0.08)' },
  HIGH: { color: 'var(--risk-high)', bg: 'rgba(249,115,22,0.08)' },
  CRITICAL: { color: 'var(--risk-critical)', bg: 'rgba(239,68,68,0.08)' }
}

const categoryIcons: Record<string, string> = {
  NULL_SAFETY: '🔍',
  SECURITY: '🔒',
  EXCEPTION_HANDLING: '⚠️',
  MEMORY_LEAK: '💧',
  CODE_QUALITY: '📝',
  DISTRIBUTED_LOCK: '🔐',
  DATABASE: '🗄️',
  PERFORMANCE: '⚡',
  MEMORY: '🧠'
}
</script>

<template>
  <div class="findings">
    <h3 class="section-title">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
      风险规则发现
      <span class="count">{{ findings.length }}</span>
    </h3>

    <div v-if="findings.length === 0" class="empty">
      <span class="empty-icon">✅</span>
      <p>静态规则未发现明显问题</p>
    </div>

    <div
      v-for="(finding, i) in findings"
      :key="i"
      class="finding-item"
      :style="{
        borderLeftColor: riskConfig[finding.riskLevel]?.color || 'var(--border)',
        background: riskConfig[finding.riskLevel]?.bg || 'transparent'
      }"
    >
      <div class="finding-header">
        <span class="finding-category">
          {{ categoryIcons[finding.category] || '📋' }}
          {{ finding.ruleName }}
        </span>
        <span
          class="finding-level"
          :style="{ color: riskConfig[finding.riskLevel]?.color }"
        >
          {{ finding.riskLevel }}
        </span>
      </div>
      <p class="finding-message">{{ finding.message }}</p>
      <p class="finding-suggestion">{{ finding.suggestion }}</p>
      <div v-if="finding.file" class="finding-file">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        {{ finding.file }}<template v-if="finding.line">:{{ finding.line }}</template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.findings {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  color: var(--text-primary);
}

.section-title svg {
  color: var(--warning);
}

.count {
  font-size: 12px;
  background: var(--bg-elevated);
  color: var(--text-muted);
  padding: 2px 8px;
  border-radius: 100px;
  font-weight: 500;
  margin-left: auto;
}

.empty {
  text-align: center;
  padding: 32px 16px;
  color: var(--text-muted);
  font-size: 14px;
}

.empty-icon {
  font-size: 28px;
  display: block;
  margin-bottom: 8px;
}

.finding-item {
  border-left: 3px solid var(--border);
  padding: 14px 16px;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  margin-bottom: 10px;
}

.finding-item:last-child { margin-bottom: 0; }

.finding-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.finding-category {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.finding-level {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
}

.finding-message {
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 4px;
  line-height: 1.5;
}

.finding-suggestion {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  line-height: 1.5;
}

.finding-file {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
