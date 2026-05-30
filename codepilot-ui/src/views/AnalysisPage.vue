<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAnalysisStore } from '@/stores/analysis'
import PrSummary from '@/components/PrSummary.vue'
import RiskScoreCard from '@/components/RiskScoreCard.vue'
import RuleFindings from '@/components/RuleFindings.vue'
import AiReviewPanel from '@/components/AiReviewPanel.vue'
import FileAnalysisList from '@/components/FileAnalysisList.vue'
import AiThinkingTimeline from '@/components/AiThinkingTimeline.vue'
import AgentStatusBar from '@/components/AgentStatusBar.vue'
import ConfidenceGauge from '@/components/ConfidenceGauge.vue'
import RiskHeatMap from '@/components/RiskHeatMap.vue'

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
    <!-- Top bar with agent status -->
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
        {{ store.statusMessage || store.currentStage || store.status }}
      </div>
    </header>

    <!-- Agent status bar — shows during active analysis -->
    <div class="agent-bar-wrapper" v-if="(store.status && store.status !== 'COMPLETED' && store.status !== 'FAILED') || store.tokenUsage">
      <AgentStatusBar
        :active-agents="store.activeAgents"
        :token-usage="store.tokenUsage"
        :status="store.status"
      />
    </div>

    <main class="main-grid">
      <!-- Left sidebar: PR info + Agent workflow + File list -->
      <aside class="left-sidebar">
        <PrSummary :result="store.result" />

        <!-- Agent workflow timeline — replaces old StreamingTimeline -->
        <AiThinkingTimeline
          v-if="store.agentEvents.length > 0 || store.loading"
          :agent-events="store.agentEvents"
          :active-agents="store.activeAgents"
          :current-stage="store.currentStage"
          :status="store.status as any"
        />

        <!-- Legacy timeline fallback -->
        <div v-else-if="store.sseStatusHistory.length > 0 && store.agentEvents.length === 0" class="legacy-timeline-card">
          <div class="timeline-card-header">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
            </svg>
            <span>分析进度</span>
          </div>
          <div
            v-for="(step, i) in store.sseStatusHistory"
            :key="i"
            class="legacy-step"
          >
            <span class="legacy-step-dot" :class="{ active: i === store.sseStatusHistory.length - 1 && store.status !== 'COMPLETED' }"></span>
            <span class="legacy-step-msg">{{ step.message || step.status }}</span>
          </div>
        </div>

        <!-- Loading skeleton for file list -->
        <div v-if="store.loading && !store.result?.fileAnalysis" class="skeleton-card">
          <div class="skeleton-line skeleton-title"></div>
          <div class="skeleton-line" v-for="i in 4" :key="i"></div>
        </div>

        <!-- Risk heatmap — shows after analysis completes -->
        <RiskHeatMap
          v-if="store.result?.fileAnalysis"
          :files="store.result.fileAnalysis"
        />

        <!-- File analysis list (condensed below heatmap) -->
        <FileAnalysisList
          v-if="store.result?.fileAnalysis"
          :files="store.result.fileAnalysis"
        />
      </aside>

      <!-- Center: AI Review output -->
      <section class="center-content">
        <AiReviewPanel
          :content="store.aiStream || store.result?.aiRawOutput || ''"
          :loading="store.loading"
        />
      </section>

      <!-- Right sidebar: Risk score + Confidence + Rule findings -->
      <aside class="right-sidebar">
        <RiskScoreCard
          v-if="store.result?.riskScore"
          :score="store.result.riskScore"
        />
        <div v-else-if="store.loading" class="skeleton-card skeleton-score">
          <div class="skeleton-ring"></div>
          <div class="skeleton-line" v-for="i in 5" :key="i"></div>
        </div>

        <!-- AI confidence gauge -->
        <ConfidenceGauge
          :confidence="store.confidence"
        />

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
  backdrop-filter: blur(12px);
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
  box-shadow: var(--shadow-glow-sm);
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

/* Agent status bar wrapper */
.agent-bar-wrapper {
  padding: 10px 20px 0;
  max-width: 1600px;
  margin: 0 auto;
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

/* Legacy timeline fallback */
.legacy-timeline-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 14px;
}

.timeline-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 10px;
}

.timeline-card-header svg { color: var(--primary); }

.legacy-step {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--text-muted);
}

.legacy-step-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--border-light);
  flex-shrink: 0;
}

.legacy-step-dot.active {
  background: var(--primary);
  box-shadow: 0 0 6px var(--primary-glow);
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

  .agent-bar-wrapper {
    padding: 8px 14px 0;
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
