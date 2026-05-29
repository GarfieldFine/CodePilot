<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string; loading: boolean }>()

const sections = computed(() => {
  const raw = props.content || ''
  const parts: { type: string; title: string; content: string }[] = []

  const headers = ['### PR Summary', '### Risk Analysis', '### Review Suggestions', '### Overall Assessment']
  let lastIdx = 0

  for (let i = 0; i < headers.length; i++) {
    const header = headers[i]
    const idx = raw.indexOf(header)
    if (idx >= 0) {
      const nextIdx = i + 1 < headers.length ? raw.indexOf(headers[i + 1], idx) : raw.length
      const next = nextIdx > 0 ? nextIdx : raw.length
      const sectionContent = raw.substring(idx + header.length, next).trim()
      parts.push({
        type: header.replace('### ', '').toLowerCase().replace(/\s+/g, '-'),
        title: header.replace('### ', ''),
        content: sectionContent
      })
    }
  }

  if (parts.length === 0 && raw) {
    parts.push({ type: 'raw', title: 'AI Analysis', content: raw })
  }

  return parts
})

function renderMarkdown(text: string): string {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
    .replace(/^- (.*)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
    .replace(/\n\n/g, '<br/><br/>')
    .replace(/\n/g, '<br/>')
}

const sectionIcons: Record<string, string> = {
  'pr-summary': '📋',
  'risk-analysis': '🔍',
  'review-suggestions': '💡',
  'overall-assessment': '✅'
}
</script>

<template>
  <div class="ai-review">
    <h3 class="section-title">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
      AI 评审结果
    </h3>

    <div v-if="loading && !content" class="ai-thinking">
      <div class="thinking-dots">
        <span></span><span></span><span></span>
      </div>
      <p>AI 正在分析代码变更...</p>
    </div>

    <div v-if="content" class="sections">
      <div
        v-for="section in sections"
        :key="section.type"
        class="section"
      >
        <h4 class="section-header">
          {{ section.title }}
        </h4>
        <div class="section-body" v-html="renderMarkdown(section.content)"></div>
      </div>
    </div>

    <div v-if="!loading && !content" class="empty">
      <p>等待 AI 评审结果...</p>
    </div>
  </div>
</template>

<style scoped>
.ai-review {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
  min-height: 300px;
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
  color: var(--primary);
}

.ai-thinking {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-secondary);
  font-size: 14px;
  gap: 16px;
}

.thinking-dots {
  display: flex;
  gap: 6px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse 1.4s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes pulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-muted);
  font-size: 14px;
}

.section {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border);
}

.section:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.section-header {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.section-body {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.section-body :deep(strong) {
  color: var(--text-primary);
  font-weight: 600;
}

.section-body :deep(.inline-code) {
  background: var(--code-bg);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--primary);
}

.section-body :deep(li) {
  margin-bottom: 4px;
  padding-left: 4px;
}

.section-body :deep(ul) {
  padding-left: 18px;
  margin: 6px 0;
}
</style>
