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

export function analyzePRStreamUrl(data: PrAnalyzeRequest): string {
  return `/api/v1/pr/analyze/stream`
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
