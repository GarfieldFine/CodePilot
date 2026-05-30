<script setup lang="ts">
import { computed } from 'vue'
import type { FileAnalysis, RiskLevel } from '@/types'

const props = defineProps<{
  files: FileAnalysis[]
}>()

const emit = defineEmits<{
  select: [filename: string]
}>()

const riskColor: Record<RiskLevel, string> = {
  CRITICAL: 'var(--risk-critical)',
  HIGH: 'var(--risk-high)',
  MEDIUM: 'var(--risk-medium)',
  LOW: 'var(--risk-low)'
}

const riskBg: Record<RiskLevel, string> = {
  CRITICAL: 'rgba(239, 68, 68, 0.18)',
  HIGH: 'rgba(249, 115, 22, 0.14)',
  MEDIUM: 'rgba(251, 191, 36, 0.12)',
  LOW: 'rgba(52, 211, 153, 0.08)'
}

// Sort files by risk level severity
const sortedFiles = computed(() => {
  const order: Record<RiskLevel, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
  return [...props.files].sort((a, b) => order[a.riskLevel] - order[b.riskLevel])
})

const stats = computed(() => {
  const counts: Record<RiskLevel, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const f of props.files) {
    counts[f.riskLevel]++
  }
  return counts
})

function getStatusLabel(status: string): string {
  const map: Record<string, string> = { added: 'A', modified: 'M', removed: 'D', renamed: 'R' }
  return map[status] || status
}

function getStatusColor(status: string): string {
  const map: Record<string, string> = {
    added: 'var(--success)',
    modified: 'var(--warning)',
    removed: 'var(--danger)',
    renamed: 'var(--info)'
  }
  return map[status] || 'var(--text-muted)'
}
</script>

<template>
  <div class="risk-heatmap">
    <div class="heatmap-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/>
      </svg>
      <span>文件风险热力图</span>
      <span class="file-count">{{ files.length }} files</span>
    </div>

    <!-- Summary stats pills -->
    <div class="risk-summary">
      <span v-if="stats.CRITICAL" class="risk-pill critical">
        {{ stats.CRITICAL }} CRITICAL
      </span>
      <span v-if="stats.HIGH" class="risk-pill high">
        {{ stats.HIGH }} HIGH
      </span>
      <span v-if="stats.MEDIUM" class="risk-pill medium">
        {{ stats.MEDIUM }} MEDIUM
      </span>
      <span v-if="stats.LOW" class="risk-pill low">
        {{ stats.LOW }} LOW
      </span>
    </div>

    <!-- Heatmap grid -->
    <div class="heatmap-grid">
      <div
        v-for="file in sortedFiles"
        :key="file.filename"
        class="heatmap-cell"
        :style="{
          background: riskBg[file.riskLevel],
          borderLeftColor: riskColor[file.riskLevel]
        }"
        @click="emit('select', file.filename)"
        :title="`${file.filename} — ${file.riskLevel} (${file.findings.length} findings)`"
      >
        <span
          class="file-status"
          :style="{ color: getStatusColor(file.status) }"
        >{{ getStatusLabel(file.status) }}</span>
        <span class="file-name">{{ file.filename }}</span>
        <span class="file-changes">
          <span class="additions">+{{ file.additions }}</span>
          <span class="deletions">-{{ file.deletions }}</span>
        </span>
        <span
          class="risk-dot"
          :style="{ background: riskColor[file.riskLevel] }"
        ></span>
      </div>
    </div>

    <!-- Empty -->
    <div v-if="files.length === 0" class="empty">
      <p>暂无文件数据</p>
    </div>
  </div>
</template>

<style scoped>
.risk-heatmap {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
}

.heatmap-header {
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

.heatmap-header svg {
  color: var(--primary);
}

.file-count {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-muted);
  font-weight: 400;
}

.risk-summary {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.risk-pill {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 8px;
}

.risk-pill.critical { background: rgba(239, 68, 68, 0.15); color: var(--critical); }
.risk-pill.high { background: rgba(249, 115, 22, 0.12); color: var(--risk-high); }
.risk-pill.medium { background: rgba(251, 191, 36, 0.12); color: var(--risk-medium); }
.risk-pill.low { background: rgba(52, 211, 153, 0.1); color: var(--success); }

.heatmap-grid {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.heatmap-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  border-left: 3px solid transparent;
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 12px;
}

.heatmap-cell:hover {
  background: var(--bg-card-hover) !important;
  transform: translateX(2px);
}

.file-status {
  font-weight: 700;
  font-size: 11px;
  width: 16px;
  flex-shrink: 0;
}

.file-name {
  flex: 1;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-changes {
  display: flex;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  flex-shrink: 0;
}

.additions { color: var(--success); }
.deletions { color: var(--danger); }

.risk-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 6px currentColor;
}

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
