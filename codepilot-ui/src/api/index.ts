import axios from 'axios'
import type { ApiResponse, AnalysisResult, PrAnalyzeRequest, PrDetail } from '@/types'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' }
})

http.interceptors.response.use(
  res => res,
  err => {
    const msg = err.response?.data?.message || err.message || 'Network error'
    return Promise.reject(new Error(msg))
  }
)

export async function analyzePR(data: PrAnalyzeRequest): Promise<AnalysisResult> {
  const res = await http.post<ApiResponse<AnalysisResult>>('/pr/analyze', data)
  return res.data.data
}

export function analyzePRStream(
  data: PrAnalyzeRequest,
  onEvent: (event: any) => void,
  onError: (err: Error) => void,
  onComplete: () => void
): AbortController {
  const controller = new AbortController()

  fetch('/api/v1/pr/analyze/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    const reader = response.body?.getReader()
    if (!reader) throw new Error('No response body')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          try {
            const json = JSON.parse(line.substring(5).trim())
            onEvent(json)
          } catch {
            // skip parse errors on partial chunks
          }
        }
      }
    }

    // Process remaining buffer
    if (buffer.startsWith('data:')) {
      try {
        const json = JSON.parse(buffer.substring(5).trim())
        onEvent(json)
      } catch { /* skip */ }
    }

    onComplete()
  }).catch(err => {
    if (err.name !== 'AbortError') {
      onError(err)
    }
  })

  return controller
}

export async function getPRDetail(url: string): Promise<PrDetail> {
  const res = await http.get<ApiResponse<PrDetail>>('/pr/detail', { params: { url } })
  return res.data.data
}

export async function getAvailableProviders(): Promise<string[]> {
  const res = await http.get<ApiResponse<string[]>>('/ai/providers')
  return res.data.data
}

export async function getCachedResult(owner: string, repo: string, prNumber: number): Promise<AnalysisResult | null> {
  const res = await http.get<ApiResponse<AnalysisResult>>(`/pr/cache/${owner}/${repo}/${prNumber}`)
  return res.data.data
}
