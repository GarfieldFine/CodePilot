<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

defineProps<{ content: string; loading: boolean }>()
</script>

<template>
  <div class="ai-review">
    <h3 class="section-title">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
      AI 评审结果
    </h3>

    <MarkdownRenderer :content="content" :loading="loading" />
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
</style>
