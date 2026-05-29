<script setup lang="ts">
import type { AnalysisResult } from '@/types'

defineProps<{ result: AnalysisResult | null }>()

const riskLabel: Record<string, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CRITICAL: '严重风险'
}
</script>

<template>
  <div class="pr-summary" v-if="result">
    <div class="summary-header">
      <div class="pr-meta">
        <span class="pr-repo">{{ result.owner }}/{{ result.repo }}</span>
        <span class="pr-number">#{{ result.prNumber }}</span>
      </div>
      <h2 class="pr-title">{{ result.prTitle }}</h2>
      <div class="pr-stats">
        <span class="stat">
          <span class="stat-dot add"></span>
          +{{ result.additions }}
        </span>
        <span class="stat">
          <span class="stat-dot del"></span>
          -{{ result.deletions }}
        </span>
        <span class="stat">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          {{ result.changedFiles }} files
        </span>
        <span class="stat author">{{ result.author }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pr-summary {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
}

.pr-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.pr-repo {
  font-size: 13px;
  color: var(--primary);
  font-weight: 500;
}

.pr-number {
  font-size: 13px;
  color: var(--text-muted);
  background: var(--bg-elevated);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.pr-title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 16px;
  color: var(--text-primary);
  line-height: 1.4;
}

.pr-stats {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.stat {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
}

.stat svg {
  opacity: 0.6;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.stat-dot.add { background: var(--success); }
.stat-dot.del { background: var(--danger); }

.stat.author {
  font-family: var(--font-sans);
}
</style>
