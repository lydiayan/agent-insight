import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as dashboardApi from '../api/dashboard'
import type { DashboardSummary, TrendData, AgentRankingItem } from '../api/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const summary = ref<DashboardSummary | null>(null)
  const trends = ref<TrendData | null>(null)
  const ranking = ref<AgentRankingItem[]>([])
  const loading = ref(false)
  const range = ref('24h')

  async function fetchSummary(r: string) {
    range.value = r
    loading.value = true
    try {
      const res = await dashboardApi.getSummary(r)
      summary.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function fetchTrends(r: string = '7d', granularity: string = '1d') {
    try {
      const res = await dashboardApi.getTrends(r, granularity)
      trends.value = res.data
    } catch (e) {
      console.error('[Dashboard] fetchTrends failed', e)
    }
  }

  async function fetchRanking(r: string = '7d') {
    try {
      const res = await dashboardApi.getAgentRanking(r)
      ranking.value = res.data
    } catch (e) {
      console.error('[Dashboard] fetchRanking failed', e)
    }
  }

  return { summary, trends, ranking, loading, range, fetchSummary, fetchTrends, fetchRanking }
})
