import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AnalysisResult, SseEvent, AgentEvent, TokenUsage, ConfidenceScores, AnalysisStatus } from '@/types'
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

  // Phase 8: Agent workflow state
  const agentEvents = ref<Array<AgentEvent & { time: number }>>([])
  const activeAgents = ref<string[]>([])
  const tokenUsage = ref<TokenUsage | null>(null)
  const confidence = ref<ConfidenceScores | null>(null)

  // Phase 8: Computed agent workflow progress
  const completedAgents = computed(() =>
    agentEvents.value.filter(e => e.type === 'agent_complete').map(e => e.agentName)
  )
  const failedAgents = computed(() =>
    agentEvents.value.filter(e => e.type === 'agent_error').map(e => e.agentName)
  )
  const currentStage = computed(() => {
    const stages = agentEvents.value.filter(e => e.type === 'pipeline_stage')
    return stages.length > 0 ? stages[stages.length - 1].message : ''
  })

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
    agentEvents.value = []
    activeAgents.value = []
    tokenUsage.value = null
    confidence.value = null
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
        break

      case 'token':
      case 'ai_token':
        if (event.content) {
          aiStream.value += event.content
        }
        break

      // Phase 8: Agent lifecycle events
      case 'agent_start':
        if (event.agentName && !activeAgents.value.includes(event.agentName)) {
          activeAgents.value.push(event.agentName)
        }
        agentEvents.value.push({
          type: 'agent_start',
          agentName: event.agentName || '',
          message: event.message || '',
          time: Date.now()
        })
        break

      case 'agent_complete':
        activeAgents.value = activeAgents.value.filter(a => a !== event.agentName)
        agentEvents.value.push({
          type: 'agent_complete',
          agentName: event.agentName || '',
          message: event.message || '',
          time: Date.now()
        })
        break

      case 'agent_error':
        activeAgents.value = activeAgents.value.filter(a => a !== event.agentName)
        agentEvents.value.push({
          type: 'agent_error',
          agentName: event.agentName || '',
          message: event.message || '',
          time: Date.now()
        })
        break

      case 'agent_skip':
        agentEvents.value.push({
          type: 'agent_skip',
          agentName: event.agentName || '',
          message: event.message || '',
          time: Date.now()
        })
        break

      case 'pipeline_stage':
        agentEvents.value.push({
          type: 'pipeline_stage',
          agentName: event.agentName || '',
          message: event.message || '',
          time: Date.now()
        })
        break

      case 'complete':
        if (event.data) {
          result.value = event.data
          // Extract Phase 8 metadata from result
          if (event.data.tokenUsage) {
            tokenUsage.value = event.data.tokenUsage
          }
          if (event.data.confidenceScores) {
            confidence.value = event.data.confidenceScores
          }
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
    sseStatusHistory, agentEvents, activeAgents, tokenUsage, confidence,
    completedAgents, failedAgents, currentStage,
    reset, startStreaming, handleSseEvent
  }
})
