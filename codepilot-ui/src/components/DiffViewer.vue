<script setup lang="ts">
import { computed } from 'vue'
import hljs from 'highlight.js'

const props = defineProps<{
  diff: string
  filename: string
  language?: string
}>()

interface DiffLine {
  type: 'add' | 'remove' | 'context' | 'header'
  content: string
  lineNumber?: number
}

const diffLines = computed<DiffLine[]>(() => {
  if (!props.diff) return []
  const lines = props.diff.split('\n')
  const result: DiffLine[] = []
  let oldLine = 0
  let newLine = 0

  for (const line of lines) {
    if (line.startsWith('diff --git') || line.startsWith('index ') ||
        line.startsWith('---') || line.startsWith('+++') || line.startsWith('@@')) {
      // Parse line numbers from @@ -old,count +new,count @@
      const match = line.match(/@@ -(\d+),\d+ \+(\d+),\d+ @@/)
      if (match) {
        oldLine = parseInt(match[1])
        newLine = parseInt(match[2])
      }
      result.push({ type: 'header', content: line })
    } else if (line.startsWith('+')) {
      result.push({ type: 'add', content: line.substring(1), lineNumber: newLine++ })
    } else if (line.startsWith('-')) {
      result.push({ type: 'remove', content: line.substring(1), lineNumber: oldLine++ })
    } else {
      result.push({ type: 'context', content: line.startsWith(' ') ? line.substring(1) : line, lineNumber: oldLine++ })
      newLine++
    }
  }

  return result
})

function highlightCode(code: string): string {
  if (!code.trim()) return '&nbsp;'
  const lang = props.language || detectLanguage(props.filename)
  try {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
  } catch { /* use auto */ }
  return hljs.highlightAuto(code).value
}

function detectLanguage(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  const map: Record<string, string> = {
    java: 'java', py: 'python', go: 'go', js: 'javascript', ts: 'typescript',
    vue: 'html', jsx: 'javascript', tsx: 'typescript', css: 'css',
    scss: 'scss', html: 'html', xml: 'xml', yaml: 'yaml', yml: 'yaml',
    sql: 'sql', sh: 'bash', md: 'markdown', json: 'json', kt: 'kotlin',
    rs: 'rust', cpp: 'cpp', c: 'c', h: 'c', rb: 'ruby', php: 'php',
    swift: 'swift', dart: 'dart'
  }
  return map[ext] || ''
}

function isCollapsible(lines: DiffLine[], index: number): boolean {
  // Check if we have 3+ context lines that could be collapsed
  if (lines[index].type !== 'context') return false
  let count = 0
  for (let i = index; i < lines.length && lines[i].type === 'context'; i++) {
    count++
  }
  return count > 6
}
</script>

<template>
  <div class="diff-viewer">
    <div class="diff-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="16 3 21 3 21 8"/><line x1="4" y1="20" x2="21" y2="3"/>
        <polyline points="21 16 21 21 16 21"/><line x1="15" y1="15" x2="21" y2="21"/>
        <line x1="4" y1="4" x2="9" y2="9"/>
      </svg>
      <span class="diff-filename">{{ filename }}</span>
      <span class="diff-lang" v-if="language">{{ language }}</span>
    </div>

    <div class="diff-content" v-if="diff">
      <div
        v-for="(line, i) in diffLines"
        :key="i"
        class="diff-line"
        :class="line.type"
      >
        <span class="line-prefix">
          {{ line.type === 'add' ? '+' : line.type === 'remove' ? '-' : ' ' }}
        </span>
        <span class="line-number" v-if="line.lineNumber">{{ line.lineNumber }}</span>
        <span class="line-number" v-else></span>
        <span class="line-code" v-html="highlightCode(line.content)" v-if="line.type !== 'header'"></span>
        <span class="line-header" v-else>{{ line.content }}</span>
      </div>
    </div>

    <div v-else class="diff-empty">
      <p>Diff 内容不可用</p>
    </div>
  </div>
</template>

<style scoped>
.diff-viewer {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.diff-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}

.diff-header svg {
  color: var(--text-muted);
  flex-shrink: 0;
}

.diff-filename {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-primary);
  font-weight: 500;
}

.diff-lang {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-muted);
  background: var(--bg-elevated);
  padding: 2px 8px;
  border-radius: 8px;
}

.diff-content {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
}

.diff-line {
  display: flex;
  align-items: flex-start;
  min-height: 20px;
}

.diff-line.header {
  background: var(--bg-secondary);
  color: var(--primary);
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
}

.diff-line.add {
  background: var(--diff-add-bg);
}

.diff-line.remove {
  background: var(--diff-remove-bg);
}

.line-prefix {
  width: 20px;
  text-align: center;
  flex-shrink: 0;
  font-weight: 700;
  font-size: 11px;
  user-select: none;
  padding: 0 4px;
}

.diff-line.add .line-prefix { color: var(--success); }
.diff-line.remove .line-prefix { color: var(--danger); }
.diff-line.context .line-prefix { color: var(--text-muted); }

.line-number {
  width: 44px;
  text-align: right;
  padding-right: 8px;
  color: var(--text-muted);
  font-size: 10px;
  user-select: none;
  flex-shrink: 0;
}

.line-code {
  flex: 1;
  white-space: pre;
  padding-right: 8px;
}

.line-header {
  padding: 0 4px;
  font-family: var(--font-mono);
}

.diff-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: var(--text-muted);
  font-size: 13px;
}

/* Highlight.js overrides for dark theme */
.diff-content :deep(.hljs-keyword) { color: #c792ea; }
.diff-content :deep(.hljs-string) { color: #c3e88d; }
.diff-content :deep(.hljs-number) { color: #f78c6c; }
.diff-content :deep(.hljs-comment) { color: #676e95; font-style: italic; }
.diff-content :deep(.hljs-type) { color: #ffcb6b; }
.diff-content :deep(.hljs-function) { color: #82aaff; }
.diff-content :deep(.hljs-attr) { color: #ffcb6b; }
.diff-content :deep(.hljs-built_in) { color: #82aaff; }
.diff-content :deep(.hljs-literal) { color: #f78c6c; }
.diff-content :deep(.hljs-meta) { color: #89ddff; }
.diff-content :deep(.hljs-title) { color: #82aaff; }
.diff-content :deep(.hljs-params) { color: #eeffff; }
</style>
