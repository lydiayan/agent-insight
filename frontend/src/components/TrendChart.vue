<script setup lang="ts">
import { computed, watch, ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import type { TrendData } from '../api/dashboard'

const props = defineProps<{ data: TrendData | null }>()

// 三个图表容器
const chart1Ref = ref<HTMLDivElement>()
const chart2Ref = ref<HTMLDivElement>()
const chart3Ref = ref<HTMLDivElement>()
let c1: echarts.ECharts | null = null
let c2: echarts.ECharts | null = null
let c3: echarts.ECharts | null = null

const baseGrid = { left: 48, right: 24, top: 12, bottom: 28 }
const baseX = (labels: string[]) => ({
  type: 'category' as const,
  data: labels,
  axisLine: { lineStyle: { color: '#e5e7eb' } },
  axisLabel: { color: '#9ca3af', fontSize: 11 },
})

// ============ 图表 1: 流量 & 耗时 ============
const opt1 = computed(() => {
  if (!props.data) return {}
  const d = props.data
  return {
    tooltip: { trigger: 'axis' as const },
    grid: baseGrid,
    xAxis: baseX(d.labels),
    yAxis: [
      { type: 'value' as const, name: '请求量', nameTextStyle: { color: '#9ca3af', fontSize: 11 }, splitLine: { lineStyle: { type: 'dashed' as const, color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      { type: 'value' as const, name: 'ms', nameTextStyle: { color: '#9ca3af', fontSize: 11 }, splitLine: { show: false }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
    ],
    series: [
      {
        name: '请求量', type: 'bar' as const, barWidth: '40%', data: d.requestVolumes,
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#818cf8' }, { offset: 1, color: '#c7d2fe' }]), borderRadius: [4, 4, 0, 0] },
      },
      {
        name: '平均耗时', type: 'line' as const, yAxisIndex: 1, smooth: true, data: d.avgLatencies,
        itemStyle: { color: '#6366f1' }, lineStyle: { width: 2, type: 'dashed' as const }, symbol: 'circle', symbolSize: 6,
      },
    ],
  }
})

// ============ 图表 2: 检索链路成功率 (Milvus/Retrieve/Rerank) ============
const opt2 = computed(() => {
  if (!props.data) return {}
  const d = props.data
  const meta = d.operationMeta || {}
  const rates = d.operationSuccessRates || {}
  const ops = ['milvus', 'retrieve', 'rerank']
  return {
    tooltip: { trigger: 'axis' as const, valueFormatter: (v: number) => v + '%' },
    grid: baseGrid,
    xAxis: baseX(d.labels),
    yAxis: { type: 'value' as const, name: '%', min: 0, max: 100, nameTextStyle: { color: '#9ca3af', fontSize: 11 }, splitLine: { lineStyle: { type: 'dashed' as const, color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
    series: ops.map(op => ({
      name: meta[op]?.label || op,
      type: 'line' as const, smooth: true, data: rates[op] || [],
      itemStyle: { color: meta[op]?.color || '#9ca3af' }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 5,
    })),
  }
})

// ============ 图表 3: Agent 执行成功率 (Prompt/LLM/Tool) ============
const opt3 = computed(() => {
  if (!props.data) return {}
  const d = props.data
  const meta = d.operationMeta || {}
  const rates = d.operationSuccessRates || {}
  const ops = ['prompt_build', 'llm', 'tool']
  return {
    tooltip: { trigger: 'axis' as const, valueFormatter: (v: number) => v + '%' },
    grid: baseGrid,
    xAxis: baseX(d.labels),
    yAxis: { type: 'value' as const, name: '%', min: 0, max: 100, nameTextStyle: { color: '#9ca3af', fontSize: 11 }, splitLine: { lineStyle: { type: 'dashed' as const, color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
    series: ops.map(op => ({
      name: meta[op]?.label || op,
      type: 'line' as const, smooth: true, data: rates[op] || [],
      itemStyle: { color: meta[op]?.color || '#9ca3af' }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 5,
    })),
  }
})

function init() {
  if (chart1Ref.value) { c1 = echarts.init(chart1Ref.value); c1.setOption(opt1.value) }
  if (chart2Ref.value) { c2 = echarts.init(chart2Ref.value); c2.setOption(opt2.value) }
  if (chart3Ref.value) { c3 = echarts.init(chart3Ref.value); c3.setOption(opt3.value) }
}

watch(opt1, v => c1?.setOption(v, true), { deep: true })
watch(opt2, v => c2?.setOption(v, true), { deep: true })
watch(opt3, v => c3?.setOption(v, true), { deep: true })

onMounted(init)
onUnmounted(() => { c1?.dispose(); c2?.dispose(); c3?.dispose() })
const onResize = () => { c1?.resize(); c2?.resize(); c3?.resize() }
window.addEventListener('resize', onResize)
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="charts-row">
    <div class="card chart-box">
      <h3 class="chart-title">流量 & 耗时</h3>
      <div ref="chart1Ref" class="chart-canvas"></div>
    </div>
    <div class="card chart-box">
      <h3 class="chart-title">检索链路成功率</h3>
      <div ref="chart2Ref" class="chart-canvas"></div>
    </div>
    <div class="card chart-box">
      <h3 class="chart-title">Agent 执行成功率</h3>
      <div ref="chart3Ref" class="chart-canvas"></div>
    </div>
  </div>
</template>

<style scoped>
.charts-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;
}
@media (max-width: 1200px) {
  .charts-row { grid-template-columns: 1fr; }
}
.chart-box {
  padding: 16px 20px;
}
.chart-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}
.chart-canvas {
  width: 100%;
  height: 280px;
}
</style>
