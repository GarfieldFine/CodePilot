<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAnalysisStore } from '@/stores/analysis'

const router = useRouter()
const store = useAnalysisStore()

const prUrl = ref('')
const loading = ref(false)
const error = ref('')

const examplePRs = [
  { label: 'Spring Boot PR', url: 'https://github.com/spring-projects/spring-boot/pull/40000' },
  { label: 'Vue.js PR', url: 'https://github.com/vuejs/core/pull/10000' },
  { label: 'Redis PR', url: 'https://github.com/redis/redis/pull/12000' },
]

function fillExample(url: string) {
  prUrl.value = url
}

function startAnalysis() {
  error.value = ''
  if (!prUrl.value.trim()) {
    error.value = '请输入 GitHub PR 链接'
    return
  }
  if (!prUrl.value.includes('github.com') || !prUrl.value.includes('/pull/')) {
    error.value = '请输入有效的 GitHub PR 链接'
    return
  }
  loading.value = true
  const url = prUrl.value.trim()
  // Navigate first so the analysis page renders immediately
  router.push('/analysis')
  // Start SSE streaming — AnalysisPage will pick up store.prUrl
  store.startStreaming(url)
  // loading will be set to false by store when streaming completes or errors
  setTimeout(() => { loading.value = false }, 500)
}
</script>

<template>
  <div class="home">
    <header class="hero">
      <div class="hero-badge">AI-Powered Code Review</div>
      <h1 class="hero-title">
        <span class="gradient-text">CodePilot</span>
      </h1>
      <p class="hero-subtitle">
        企业级 AI 代码评审系统 — 输入 GitHub PR 链接,智能分析代码变更,识别潜在风险
      </p>
    </header>

    <section class="input-section">
      <div class="input-card">
        <div class="input-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"/>
          </svg>
        </div>
        <input
          v-model="prUrl"
          type="text"
          class="pr-input"
          placeholder="https://github.com/owner/repo/pull/123"
          @keyup.enter="startAnalysis"
        />
        <button
          class="analyze-btn"
          :class="{ loading }"
          :disabled="loading"
          @click="startAnalysis"
        >
          <span v-if="!loading" class="btn-content">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polygon points="5 3 19 12 5 21 5 3"/>
            </svg>
            开始分析
          </span>
          <span v-else class="btn-content">
            <span class="spinner"></span>
            分析中...
          </span>
        </button>
        <p v-if="error" class="input-error">{{ error }}</p>
      </div>

      <div class="examples">
        <span class="examples-label">示例 PR:</span>
        <button
          v-for="example in examplePRs"
          :key="example.url"
          class="example-link"
          @click="fillExample(example.url)"
        >
          {{ example.label }}
        </button>
      </div>
    </section>

    <section class="features">
      <div class="feature-card" v-for="(feat, i) in features" :key="i">
        <div class="feature-icon" v-html="feat.icon"></div>
        <h3 class="feature-title">{{ feat.title }}</h3>
        <p class="feature-desc">{{ feat.desc }}</p>
      </div>
    </section>

    <footer class="footer">
      <span>CodePilot AI PR Review Assistant</span>
      <span class="footer-dot"></span>
      <span>v1.0.0</span>
    </footer>
  </div>
</template>

<script lang="ts">
const features = [
  {
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>',
    title: '智能 Diff 分析',
    desc: '自动获取 PR Diff，增强代码上下文，理解完整变更意图'
  },
  {
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
    title: '风险规则引擎',
    desc: '10+ 条静态规则检测 NPE、SQL 注入、并发问题、资源泄漏等'
  },
  {
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
    title: '多模型 AI 评审',
    desc: '支持 OpenAI / DeepSeek / Claude / Qwen，模拟资深 Tech Lead 视角'
  },
  {
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>',
    title: '流式实时输出',
    desc: 'SSE 流式推送分析进度，类似 Cursor/Copilot 的交互体验'
  }
]
</script>

<style scoped>
.home {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 24px 40px;
  background:
    radial-gradient(ellipse 80% 50% at 50% -20%, rgba(99, 102, 241, 0.08), transparent),
    radial-gradient(ellipse 50% 60% at 80% 80%, rgba(167, 139, 250, 0.05), transparent),
    var(--bg-root);
}

.hero {
  text-align: center;
  margin-bottom: 48px;
}

.hero-badge {
  display: inline-block;
  padding: 5px 16px;
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 100px;
  font-size: 13px;
  color: var(--primary);
  letter-spacing: 0.5px;
  margin-bottom: 24px;
}

.hero-title {
  font-size: clamp(36px, 6vw, 56px);
  font-weight: 800;
  letter-spacing: -1.5px;
  margin-bottom: 16px;
  line-height: 1.1;
}

.gradient-text {
  background: var(--gradient-hero);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
  max-width: 520px;
  margin: 0 auto;
  line-height: 1.7;
}

.input-section {
  width: 100%;
  max-width: 640px;
  margin-bottom: 64px;
}

.input-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 8px;
  transition: border-color var(--transition-smooth), box-shadow var(--transition-smooth);
}

.input-card:focus-within {
  border-color: var(--primary-dark);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.input-icon {
  color: var(--text-muted);
  display: flex;
  align-items: center;
  padding: 0 4px 0 8px;
  flex-shrink: 0;
}

.pr-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 15px;
  font-family: var(--font-mono);
  padding: 10px 4px;
  min-width: 0;
}

.pr-input::placeholder {
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: 14px;
}

.analyze-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 22px;
  background: var(--gradient-hero);
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity var(--transition-fast), transform var(--transition-fast);
  flex-shrink: 0;
}

.analyze-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}

.analyze-btn:active:not(:disabled) {
  transform: translateY(0);
}

.analyze-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.input-error {
  color: var(--danger);
  font-size: 13px;
  margin-top: 10px;
  padding-left: 4px;
}

.examples {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.examples-label {
  font-size: 13px;
  color: var(--text-muted);
}

.example-link {
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 13px;
  padding: 5px 14px;
  border-radius: 100px;
  cursor: pointer;
  font-family: var(--font-sans);
  transition: all var(--transition-fast);
}

.example-link:hover {
  border-color: var(--primary-dark);
  color: var(--primary);
  background: rgba(99, 102, 241, 0.08);
}

.features {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  width: 100%;
  max-width: 900px;
  margin-bottom: 64px;
}

.feature-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 28px;
  transition: border-color var(--transition-smooth), transform var(--transition-smooth);
}

.feature-card:hover {
  border-color: var(--border-light);
  transform: translateY(-2px);
}

.feature-icon {
  color: var(--primary);
  margin-bottom: 16px;
  display: flex;
}

.feature-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.feature-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.footer {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text-muted);
}

.footer-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-muted);
}
</style>
