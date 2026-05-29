import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AnalysisResult, SseEvent, AnalysisStatus } from '@/types'
import { analyzePRStream } from '@/api'

export const useAnalysisStore = defineStore('analysis', () => {
  const result = ref<AnalysisResult | null>(null)
  const status = ref<AnalysisStatus | ''>('')
  const statusMessage = ref('')
  const aiStream = ref('')
  const loading = ref(false)
  const error = ref('')
  const prUrl = ref('')
  const sseStatusHistory = ref<Array<{ status: string; message: string; time: number }>>([])

  let abortController: AbortController | null = null

  function reset() {
    abortController?.abort()
    abortController = null
    result.value = null
    status.value = ''
    statusMessage.value = ''
    aiStream.value = ''
    loading.value = false
    error.value = ''
    prUrl.value = ''
    sseStatusHistory.value = []
  }

  function startStreaming(url: string, provider?: string) {
    reset()
    prUrl.value = url
    loading.value = true
    status.value = 'PENDING'

    abortController = analyzePRStream(
      { prUrl: url, provider },
      (event: SseEvent) => handleSseEvent(event),
      (err: Error) => {
        error.value = err.message || '连接失败'
        status.value = 'FAILED'
        loading.value = false
      },
      () => {
        loading.value = false
      }
    )
  }

  function handleSseEvent(event: SseEvent) {
    switch (event.type) {
      case 'status':
        if (event.status) {
          status.value = event.status as AnalysisStatus
          sseStatusHistory.value.push({
            status: event.status,
            message: event.message || '',
            time: Date.now()
          })
        }
        if (event.message) {
          statusMessage.value = event.message
        }
        // Update rule findings count from status event
        if (event.count !== undefined && event.status === 'RULES_COMPLETE') {
          // count will be shown in the timeline message
        }
        break
      case 'token':
      case 'ai_token':
        if (event.content) {
          aiStream.value += event.content
        }
        break
      case 'complete':
        if (event.data) {
          result.value = event.data
        }
        status.value = 'COMPLETED'
        loading.value = false
        break
      case 'error':
        error.value = event.message || 'Unknown error'
        status.value = 'FAILED'
        loading.value = false
        break
    }
  }

  return {
    result, status, statusMessage, aiStream, loading, error, prUrl,
    sseStatusHistory, reset, startStreaming, handleSseEvent
  }
})
