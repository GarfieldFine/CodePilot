<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAnalysisStore } from '@/stores/analysis'
import PrSummary from '@/components/PrSummary.vue'
import RiskScoreCard from '@/components/RiskScoreCard.vue'
import RuleFindings from '@/components/RuleFindings.vue'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import FileAnalysisList from '@/components/FileAnalysisList.vue'
import StreamingTimeline from '@/components/StreamingTimeline.vue'

const router = useRouter()
const store = useAnalysisStore()

onMounted(() => {
  if (!store.loading && !store.result && !store.prUrl) {
    router.push('/')
  }
})

onUnmounted(() => {
  // Don't reset on unmount — keep results if user navigates back
})

function handleBack() {
  store.reset()
  router.push('/')
}
</script>

<template>
  <div class="analysis-page">
    <header class="top-bar">
      <button class="back-btn" @click="handleBack">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        返回
      </button>
      <div class="top-title">
        <span class="logo-text">CodePilot</span>
        <span class="logo-dot">AI Review</span>
      </div>
      <div class="top-status" v-if="store.status && store.status !== 'COMPLETED' && store.status !== 'FAILED'">
        <span class="status-dot"></span>
        {{ store.statusMessage || store.status }}
      </div>
    </header>

    <main class="main-grid">
      <aside class="left-sidebar">
        <PrSummary :result="store.result" />

        <!-- Show timeline during streaming even without full result -->
        <StreamingTimeline
          v-if="store.sseStatusHistory.length > 0"
          :history="store.sseStatusHistory"
          :current-status="store.status as any"
        />

        <!-- Show loading skeleton while waiting for file analysis -->
        <div v-if="store.loading && !store.result?.fileAnalysis" class="skeleton-card">
          <div class="skeleton-line skeleton-title"></div>
          <div class="skeleton-line" v-for="i in 4" :key="i"></div>
        </div>

        <FileAnalysisList
          v-if="store.result?.fileAnalysis"
          :files="store.result.fileAnalysis"
        />
      </aside>

      <section class="center-content">
        <AiReviewPanel
          :content="store.aiStream || store.result?.aiRawOutput || ''"
          :loading="store.loading"
        />
      </section>

      <aside class="right-sidebar">
        <RiskScoreCard
          v-if="store.result?.riskScore"
          :score="store.result.riskScore"
        />
        <div v-else-if="store.loading" class="skeleton-card skeleton-score">
          <div class="skeleton-ring"></div>
          <div class="skeleton-line" v-for="i in 5" :key="i"></div>
        </div>

        <RuleFindings
          v-if="store.result?.ruleResults"
          :findings="store.result.ruleResults"
        />
      </aside>
    </main>
  </div>
</template>

<style scoped>
.analysis-page {
  min-height: 100vh;
  background: var(--bg-root);
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 100;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 13px;
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: var(--font-sans);
}

.back-btn:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.top-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-weight: 800;
  font-size: 18px;
  background: var(--gradient-hero);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-dot {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.top-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--primary);
  font-weight: 500;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse-dot 1.5s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.main-grid {
  display: grid;
  grid-template-columns: 300px 1fr 340px;
  gap: 20px;
  padding: 20px;
  max-width: 1600px;
  margin: 0 auto;
  height: calc(100vh - 62px);
}

.left-sidebar,
.right-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 4px;
}

.center-content {
  overflow-y: auto;
}

@media (max-width: 1200px) {
  .main-grid {
    grid-template-columns: 260px 1fr 300px;
    gap: 14px;
    padding: 14px;
  }
}

@media (max-width: 900px) {
  .main-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .left-sidebar,
  .right-sidebar {
    overflow-y: visible;
  }
}

.skeleton-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
}

.skeleton-line {
  height: 12px;
  background: var(--bg-elevated);
  border-radius: 3px;
  margin-bottom: 10px;
  animation: shimmer 1.5s ease-in-out infinite;
}

.skeleton-title {
  width: 60%;
  height: 16px;
  margin-bottom: 16px;
}

.skeleton-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.skeleton-ring {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  border: 6px solid var(--bg-elevated);
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.8; }
}
</style>
