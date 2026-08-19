<script setup lang="ts">
import { onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { useDashboardStore } from '../stores/dashboard'
import MetricCards from '../components/MetricCards.vue'
import TrendChart from '../components/TrendChart.vue'
import AgentRanking from '../components/AgentRanking.vue'

const props = defineProps<{ timeRange: string }>()
const store = useDashboardStore()

const loadAll = async (range: string) => {
  const granularity = range === '24h' ? '1h' : '1d'
  await Promise.all([
    store.fetchSummary(range),
    store.fetchTrends(range, granularity),
    store.fetchRanking(range),
  ])
}

const formatWindow = (value: number) => dayjs(value).format('YYYY-MM-DD HH:mm')

onMounted(() => loadAll(props.timeRange))
watch(() => props.timeRange, (r) => loadAll(r))
</script>

<template>
  <div class="dashboard">
    <div v-if="store.summary" class="range-caption">
      统计区间：{{ formatWindow(store.summary.startMs) }} 至 {{ formatWindow(store.summary.endMs) }}
    </div>
    <!-- 指标卡片 -->
    <MetricCards :summary="store.summary" />

    <!-- 三合一历史趋势图表 -->
    <TrendChart :data="store.trends" />

    <!-- Agent 排行 -->
    <div class="ranking-row">
      <AgentRanking :items="store.ranking" />
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.range-caption {
  margin-bottom: -12px;
  color: #64748b;
  font-size: 13px;
}

.ranking-row {
  max-width: 480px;
}
</style>
