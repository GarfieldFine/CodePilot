<script setup lang="ts">
import { computed } from 'vue'
import type { ConfidenceScores } from '@/types'

const props = defineProps<{
  confidence: ConfidenceScores | null
}>()

const percent = computed(() => {
  if (!props.confidence) return 0
  return Math.round(props.confidence.overallConfidence)
})

const levelClass = computed(() => {
  if (!props.confidence) return ''
  return props.confidence.confidenceLevel.toLowerCase()
})

const factors = computed(() => {
  if (!props.confidence?.factors) return []
  const labels: Record<string, string> = {
    ruleCoverage: '规则覆盖',
    chunkSuccess: 'Chunk 成功率',
    crossChunkConsistency: 'Chunk 一致性',
    complexityFactor: '复杂度因子',
    repoKnowledge: '仓库知识',
    tokenCoverage: 'Token 覆盖'
  }
  return Object.entries(props.confidence.factors).map(([key, value]) => ({
    key,
    label: labels[key] || key,
    value: Math.round(value * 100)
  }))
})

// SVG ring parameters
const size = 120
const strokeWidth = 6
const radius = (size - strokeWidth) / 2
const circumference = 2 * Math.PI * radius
const offset = computed(() => circumference - (percent.value / 100) * circumference)

const gradientId = 'confidence-gradient'
</script>

<template>
  <div class="confidence-gauge">
    <div class="gauge-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
      </svg>
      <span>AI 置信度评估</span>
    </div>

    <div v-if="confidence" class="gauge-body">
      <!-- Ring chart -->
      <div class="ring-container">
        <svg :width="size" :height="size" viewBox="0 0 120 120">
          <defs>
            <linearGradient :id="gradientId" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="var(--danger)" />
              <stop offset="50%" stop-color="var(--warning)" />
              <stop offset="100%" stop-color="var(--success)" />
            </linearGradient>
          </defs>
          <circle
            cx="60" cy="60" :r="radius"
            fill="none"
            stroke="var(--bg-elevated)"
            :stroke-width="strokeWidth"
          />
          <circle
            cx="60" cy="60" :r="radius"
            fill="none"
            :stroke="`url(#${gradientId})`"
            :stroke-width="strokeWidth"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="offset"
            stroke-linecap="round"
            transform="rotate(-90 60 60)"
            class="ring-progress"
          />
          <text x="60" y="55" text-anchor="middle" class="ring-value">{{ percent }}%</text>
          <text x="60" y="72" text-anchor="middle" class="ring-label">{{ confidence.confidenceLevel }}</text>
        </svg>
      </div>

      <!-- Factor bars -->
      <div class="factors">
        <div v-for="factor in factors" :key="factor.key" class="factor">
          <div class="factor-header">
            <span class="factor-label">{{ factor.label }}</span>
            <span class="factor-value">{{ factor.value }}%</span>
          </div>
          <div class="factor-bar">
            <div
              class="factor-fill"
              :style="{ width: factor.value + '%' }"
              :class="{ low: factor.value < 40, mid: factor.value >= 40 && factor.value < 70, high: factor.value >= 70 }"
            ></div>
          </div>
        </div>
      </div>

      <div v-if="confidence.summary" class="confidence-summary">
        {{ confidence.summary }}
      </div>
    </div>

    <!-- Empty / waiting state -->
    <div v-else class="gauge-empty">
      <div class="empty-ring"></div>
      <p>等待分析完成...</p>
    </div>
  </div>
</template>

<style scoped>
.confidence-gauge {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
}

.gauge-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 16px;
}

.gauge-header svg {
  color: var(--primary);
}

.gauge-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.ring-container {
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring-progress {
  transition: stroke-dashoffset 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

.ring-value {
  font-size: 22px;
  font-weight: 800;
  fill: var(--text-primary);
  font-family: var(--font-mono);
}

.ring-label {
  font-size: 10px;
  font-weight: 600;
  fill: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.factors {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.factor {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.factor-header {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
}

.factor-label {
  color: var(--text-secondary);
}

.factor-value {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.factor-bar {
  height: 4px;
  background: var(--bg-elevated);
  border-radius: 2px;
  overflow: hidden;
}

.factor-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

.factor-fill.low { background: var(--danger); }
.factor-fill.mid { background: var(--warning); }
.factor-fill.high { background: var(--success); }

.confidence-summary {
  width: 100%;
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
  padding: 8px 10px;
  background: var(--bg-secondary);
  border-radius: var(--radius-sm);
}

.gauge-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px;
  color: var(--text-muted);
  font-size: 12px;
}

.empty-ring {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: 4px solid var(--bg-elevated);
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.8; }
}
</style>
