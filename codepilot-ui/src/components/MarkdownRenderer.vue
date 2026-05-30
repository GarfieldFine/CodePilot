<script setup lang="ts">
import { computed } from 'vue'
import { marked, Renderer } from 'marked'
import hljs from 'highlight.js'

// Custom renderer with highlight.js integration for marked v12+
const renderer = new Renderer()
renderer.code = function(code: string, infostring: string | undefined, _escaped: boolean): string {
  const lang = infostring || ''
  let highlighted: string
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(code, { language: lang }).value
    } catch {
      highlighted = hljs.highlightAuto(code).value
    }
  } else {
    highlighted = hljs.highlightAuto(code).value
  }
  return `<pre><code class="hljs${lang ? ` language-${lang}` : ''}">${highlighted}</code></pre>\n`
}

marked.setOptions({
  breaks: true,
  gfm: true,
  renderer
})

const props = defineProps<{ content: string; loading: boolean }>()

const sections = computed(() => {
  const raw = props.content || ''
  const parts: { type: string; title: string; html: string }[] = []

  const headers = ['### PR Summary', '### Risk Analysis', '### Review Suggestions', '### Overall Assessment']

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
        html: marked.parse(sectionContent) as string
      })
    }
  }

  // Fallback: render entire content as single section
  if (parts.length === 0 && raw) {
    parts.push({
      type: 'raw',
      title: 'AI Analysis',
      html: marked.parse(raw) as string
    })
  }

  return parts
})
</script>

<template>
  <div class="markdown-renderer">
    <div v-if="loading && !content" class="ai-thinking">
      <div class="thinking-particles">
        <span v-for="i in 12" :key="i" class="particle" :style="{ '--i': i }"></span>
      </div>
      <p>AI 正在分析代码变更...</p>
    </div>

    <div v-if="content" class="sections">
      <div v-for="section in sections" :key="section.type" class="section">
        <h4 class="section-header">{{ section.title }}</h4>
        <div class="section-body" v-html="section.html"></div>
      </div>
    </div>

    <div v-if="!loading && !content" class="empty">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3">
        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
      </svg>
      <p>等待 AI 评审结果...</p>
    </div>
  </div>
</template>

<style scoped>
.markdown-renderer {
  min-height: 300px;
}

/* AI Thinking with particles */
.ai-thinking {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-secondary);
  font-size: 14px;
  gap: 24px;
  position: relative;
}

.thinking-particles {
  position: relative;
  width: 60px;
  height: 60px;
}

.particle {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--primary);
  animation: particle-orbit 2.4s ease-in-out infinite;
  animation-delay: calc(var(--i) * 0.2s);
  opacity: 0;
}

@keyframes particle-orbit {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(calc(var(--i) * 30deg)) translateX(24px) scale(0);
  }
  15% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
    transform: translate(-50%, -50%) rotate(calc(var(--i) * 30deg + 180deg)) translateX(24px) scale(1.2);
  }
  85% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(calc(var(--i) * 30deg + 360deg)) translateX(24px) scale(0);
  }
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-muted);
  font-size: 14px;
  gap: 12px;
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
  margin-bottom: 12px;
}

.section-body {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
}

/* Markdown element styles */
.section-body :deep(strong) {
  color: var(--text-primary);
  font-weight: 600;
}

.section-body :deep(em) {
  color: var(--accent);
}

.section-body :deep(code) {
  background: var(--code-bg);
  padding: 1px 6px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--primary);
}

.section-body :deep(pre) {
  background: var(--code-bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 14px 16px;
  overflow-x: auto;
  margin: 10px 0;
  font-size: 12px;
  line-height: 1.5;
}

.section-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: var(--text-primary);
  font-size: 12px;
}

.section-body :deep(ul), .section-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.section-body :deep(li) {
  margin-bottom: 4px;
}

.section-body :deep(p) {
  margin-bottom: 8px;
}

.section-body :deep(h1), .section-body :deep(h2), .section-body :deep(h3) {
  color: var(--text-primary);
  margin: 16px 0 8px;
  font-size: 14px;
}

.section-body :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding: 4px 12px;
  margin: 8px 0;
  color: var(--text-muted);
  background: rgba(99, 102, 241, 0.04);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.section-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12px;
}

.section-body :deep(th), .section-body :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}

.section-body :deep(th) {
  background: var(--bg-elevated);
  font-weight: 600;
  color: var(--text-primary);
}

.section-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 16px 0;
}

.section-body :deep(a) {
  color: var(--primary);
  text-decoration: none;
}

.section-body :deep(a:hover) {
  text-decoration: underline;
}
</style>
