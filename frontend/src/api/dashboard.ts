import api from './index'

export interface MetricCard {
  value: number
  unit: string
  changePercent: number | null
  trend: 'up' | 'down' | null
}

export interface DashboardSummary {
  startMs: number
  endMs: number
  requestCount: MetricCard
  avgLatency: MetricCard
  toolSuccessRate: MetricCard
  ragHitRate: MetricCard
  avgTokens: MetricCard
  evalPassRate: MetricCard
}

export interface TrendData {
  labels: string[]
  requestVolumes: number[]
  operationSuccessRates: Record<string, number[]>
  operationMeta: Record<string, { label: string; color: string }>
  avgLatencies: number[]
}

export interface AgentRankingItem {
  rank: number
  agentName: string
  requestCount: number
  successRate: number
  avgLatency: number
  avgScore: number
}

export function getSummary(range: string = '24h') {
  return api.get<any, { code: number; data: DashboardSummary }>(
    '/dashboard/summary',
    { params: { range } }
  )
}

export function getTrends(range: string = '7d', granularity: string = '1d') {
  return api.get<any, { code: number; data: TrendData }>(
    '/dashboard/trends',
    { params: { range, granularity } }
  )
}

export function getAgentRanking(range: string = '7d', sortBy: string = 'requestCount', limit: number = 10) {
  return api.get<any, { code: number; data: AgentRankingItem[] }>(
    '/dashboard/agent-ranking',
    { params: { range, sortBy, limit } }
  )
}
