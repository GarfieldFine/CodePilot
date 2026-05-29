<script setup lang="ts">
import { ref } from 'vue'
import type { FileAnalysis } from '@/types'

defineProps<{ files: FileAnalysis[] }>()

const expandedFile = ref<string | null>(null)

function toggle(filename: string) {
  expandedFile.value = expandedFile.value === filename ? null : filename
}

const riskColors: Record<string, string> = {
  LOW: 'var(--risk-low)',
  MEDIUM: 'var(--risk-medium)',
  HIGH: 'var(--risk-high)',
  CRITICAL: 'var(--risk-critical)'
}

const statusLabels: Record<string, string> = {
  modified: 'M',
  added: 'A',
  removed: 'D',
  renamed: 'R'
}

const statusColors: Record<string, string> = {
  modified: 'var(--warning)',
  added: 'var(--success)',
  removed: 'var(--danger)',
  renamed: 'var(--info)'
}
</script>

<template>
  <div class="file-list">
    <h3 class="section-title">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      Changed Files
      <span class="count">{{ files.length }}</span>
    </h3>

    <div
      v-for="file in files"
      :key="file.filename"
      class="file-item"
      :class="{ expanded: expandedFile === file.filename }"
    >
      <div class="file-row" @click="toggle(file.filename)">
        <span
          class="file-status"
          :style="{ color: statusColors[file.status] || 'var(--text-muted)' }"
        >
          {{ statusLabels[file.status] || file.status }}
        </span>
        <span class="file-name">{{ file.filename }}</span>
        <span class="file-changes">
          <span class="add">+{{ file.additions }}</span>
          <span class="del">-{{ file.deletions }}</span>
        </span>
        <span
          class="file-risk"
          :style="{ color: riskColors[file.riskLevel] || 'var(--text-muted)' }"
        >
          {{ file.riskLevel }}
        </span>
        <svg class="chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>

      <div v-if="expandedFile === file.filename && file.findings.length > 0" class="file-detail">
        <div v-for="(f, j) in file.findings" :key="j" class="file-finding">
          <span class="finding-badge" :style="{ color: riskColors[f.riskLevel] }">{{ f.riskLevel }}</span>
          <div>
            <p class="finding-msg">{{ f.message }}</p>
            <p class="finding-sug">{{ f.suggestion }}</p>
          </div>
        </div>
      </div>
      <div v-if="expandedFile === file.filename && file.findings.length === 0" class="file-detail">
        <p class="no-findings">No issues found in this file</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.file-list {
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
  margin-bottom: 16px;
  color: var(--text-primary);
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

.file-item {
  border-bottom: 1px solid var(--border);
}

.file-item:last-child { border-bottom: none; }

.file-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 8px;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.file-row:hover {
  background: var(--bg-card-hover);
}

.file-status {
  font-size: 10px;
  font-weight: 700;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
  font-family: var(--font-mono);
}

.file-name {
  flex: 1;
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.file-changes {
  display: flex;
  gap: 6px;
  font-size: 11px;
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.file-changes .add { color: var(--success); }
.file-changes .del { color: var(--danger); }

.file-risk {
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-mono);
  flex-shrink: 0;
  min-width: 55px;
  text-align: right;
}

.chevron {
  flex-shrink: 0;
  color: var(--text-muted);
  transition: transform var(--transition-fast);
}

.expanded .chevron {
  transform: rotate(180deg);
}

.file-detail {
  padding: 10px 8px 14px 34px;
}

.file-finding {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 12px;
}

.file-finding:last-child { margin-bottom: 0; }

.finding-badge {
  font-weight: 700;
  font-family: var(--font-mono);
  font-size: 10px;
  flex-shrink: 0;
  margin-top: 2px;
}

.finding-msg {
  color: var(--text-primary);
  margin-bottom: 2px;
  line-height: 1.5;
}

.finding-sug {
  color: var(--text-muted);
  line-height: 1.4;
  font-size: 11px;
}

.no-findings {
  color: var(--text-muted);
  font-size: 12px;
  font-style: italic;
}
</style>
