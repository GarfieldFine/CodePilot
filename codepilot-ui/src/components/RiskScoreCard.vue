<script setup lang="ts">
import { computed } from 'vue'
import type { RiskScore, RiskLevel } from '@/types'

const props = defineProps<{ score: RiskScore }>()

const riskConfig = computed(() => {
  const map: Record<RiskLevel, { label: string; color: string; bg: string }> = {
    LOW: { label: '低风险', color: 'var(--risk-low)', bg: 'rgba(52,211,153,0.1)' },
    MEDIUM: { label: '中风险', color: 'var(--risk-medium)', bg: 'rgba(251,191,36,0.1)' },
    HIGH: { label: '高风险', color: 'var(--risk-high)', bg: 'rgba(249,115,22,0.1)' },
    CRITICAL: { label: '严重风险', color: 'var(--risk-critical)', bg: 'rgba(239,68,68,0.1)' }
  }
  return map[props.score.riskLevel] || map.LOW
})

const percent = computed(() =>
  Math.round((props.score.totalScore / props.score.maxScore) * 100)
)

const scoreFactors = computed(() => [
  { label: '文件数量', value: props.score.fileCountScore, max: 20 },
  { label: '规则命中', value: props.score.ruleFindingsScore, max: 35 },
  { label: '模块风险', value: props.score.moduleScore, max: 15 },
  { label: '变更规模', value: props.score.volumeScore, max: 15 },
  { label: 'AI评估', value: props.score.aiScore, max: 15 }
])
</script>

<template>
  <div class="score-card">
    <div class="score-ring-section">
      <div class="score-ring" :style="{ '--score-color': riskConfig.color, '--score-bg': riskConfig.bg }">
        <svg viewBox="0 0 100 100">
          <circle class="ring-bg" cx="50" cy="50" r="42" />
          <circle
            class="ring-fill"
            cx="50" cy="50" r="42"
            :style="{
              stroke: riskConfig.color,
              strokeDasharray: `${2 * Math.PI * 42}`,
              strokeDashoffset: `${2 * Math.PI * 42 * (1 - percent / 100)}`
            }"
          />
        </svg>
        <div class="ring-text">
          <span class="ring-value" :style="{ color: riskConfig.color }">{{ score.totalScore }}</span>
          <span class="ring-label">{{ riskConfig.label }}</span>
        </div>
      </div>
    </div>

    <div class="score-factors">
      <div v-for="factor in scoreFactors" :key="factor.label" class="factor">
        <div class="factor-header">
          <span class="factor-label">{{ factor.label }}</span>
          <span class="factor-value">{{ factor.value }}/{{ factor.max }}</span>
        </div>
        <div class="factor-bar">
          <div
            class="factor-fill"
            :style="{ width: (factor.value / factor.max * 100) + '%' }"
          ></div>
        </div>
      </div>
    </div>

    <div v-if="score.ruleBreakdown" class="rule-breakdown">
      <div
        v-for="(count, level) in score.ruleBreakdown"
        :key="String(level)"
        class="breakdown-tag"
        :class="'tag-' + String(level).toLowerCase()"
      >
        {{ level }}: {{ count }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.score-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
}

.score-ring-section {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.score-ring {
  width: 140px;
  height: 140px;
  position: relative;
}

.score-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: var(--border);
  stroke-width: 6;
}

.ring-fill {
  fill: none;
  stroke-width: 6;
  stroke-linecap: round;
  transition: stroke-dashoffset 1s cubic-bezier(0.4, 0, 0.2, 1);
}

.ring-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-value {
  font-size: 32px;
  font-weight: 800;
  font-family: var(--font-mono);
  line-height: 1;
}

.ring-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.score-factors {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.factor-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.factor-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.factor-value {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.factor-bar {
  height: 4px;
  background: var(--bg-elevated);
  border-radius: 2px;
  overflow: hidden;
}

.factor-fill {
  height: 100%;
  background: var(--primary);
  border-radius: 2px;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

.rule-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
}

.breakdown-tag {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 100px;
  font-family: var(--font-mono);
}

.tag-critical { background: rgba(239,68,68,0.15); color: var(--risk-critical); }
.tag-high { background: rgba(249,115,22,0.12); color: var(--risk-high); }
.tag-medium { background: rgba(251,191,36,0.12); color: var(--risk-medium); }
.tag-low { background: rgba(52,211,153,0.12); color: var(--risk-low); }
</style>
