import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AnalysisResult, SseEvent, AnalysisStatus } from '@/types'

export const useAnalysisStore = defineStore('analysis', () => {
  const result = ref<AnalysisResult | null>(null)
  const status = ref<AnalysisStatus | ''>('')
  const statusMessage = ref('')
  const aiStream = ref('')
  const loading = ref(false)
  const error = ref('')
  const sseStatusHistory = ref<Array<{ status: string; message: string; time: number }>>([])

  function reset() {
    result.value = null
    status.value = ''
    statusMessage.value = ''
    aiStream.value = ''
    loading.value = false
    error.value = ''
    sseStatusHistory.value = []
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

  return { result, status, statusMessage, aiStream, loading, error, sseStatusHistory, reset, handleSseEvent }
})
