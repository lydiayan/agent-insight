<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getOnlineQualityReport } from '../api/onlineQuality'
import type { OnlineQualityReport } from '../api/onlineQuality'

const quickRange = ref('24h')
const dateRange = ref<[Date, Date]>(createRange(24))
const loading = ref(false)
const report = ref<OnlineQualityReport | null>(null)
const currentPage = ref(1)
const pageSize = 20

const quickOptions = [
  { label: '最近1小时', value: '1h', hours: 1 },
  { label: '最近6小时', value: '6h', hours: 6 },
  { label: '最近24小时', value: '24h', hours: 24 },
  { label: '最近7天', value: '7d', hours: 24 * 7 },
]

const cards = computed(() => {
  const data = report.value
  if (!data) return []
  return [
    { label: '请求数', value: formatInteger(data.requests.total), suffix: '', tone: 'blue' },
    { label: '请求成功率', value: formatRate(data.requests.successRate), suffix: '%', tone: 'green' },
    { label: 'Tool 调用', value: formatInteger(data.toolCalls.total), suffix: '', tone: 'violet' },
    { label: 'Tool 成功率', value: formatRate(data.toolCalls.successRate), suffix: '%', tone: 'green' },
    { label: 'RAG 调用', value: formatInteger(data.ragCalls.total), suffix: '', tone: 'amber' },
    { label: 'RAG 成功率', value: formatRate(data.ragCalls.successRate), suffix: '%', tone: 'green' },
  ]
})

const operationRows = computed(() => {
  if (!report.value) return []
  return [
    { name: '请求', metric: report.value.requests, detail: '-' },
    { name: 'Tool', metric: report.value.toolCalls, detail: '-' },
    {
      name: 'RAG',
      metric: report.value.ragCalls,
      detail: `命中 ${report.value.ragHitCount} 次 / ${formatRate(report.value.ragHitRate)}%`,
    },
  ]
})

function createRange(hours: number): [Date, Date] {
  const end = dayjs()
  return [end.subtract(hours, 'hour').toDate(), end.toDate()]
}

function onQuickRangeChange(value: string) {
  const option = quickOptions.find(item => item.value === value)
  if (option) dateRange.value = createRange(option.hours)
}

function onCustomRangeChange() {
  quickRange.value = ''
}

async function loadReport(resetPage = false) {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ElMessage.warning('请选择完整的时间范围')
    return
  }
  if (resetPage) currentPage.value = 1
  loading.value = true
  try {
    const response = await getOnlineQualityReport({
      startMs: dateRange.value[0].getTime(),
      endMs: dateRange.value[1].getTime(),
      page: currentPage.value,
      pageSize,
    })
    if (response.code !== 200) throw new Error(response.message || '查询失败')
    report.value = response.data
  } catch (error: any) {
    ElMessage.error(error?.message || '线上质量数据查询失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  loadReport()
}

function formatInteger(value: number) {
  return Number(value || 0).toLocaleString()
}

function formatRate(value: number) {
  const number = Number(value || 0)
  return Number.isInteger(number) ? number.toFixed(0) : number.toFixed(1)
}

function formatDuration(value: number | null | undefined) {
  const ms = Number(value || 0)
  if (ms >= 1000) return `${(ms / 1000).toFixed(ms >= 10000 ? 1 : 2)} s`
  return `${Math.round(ms)} ms`
}

function formatTime(value: number) {
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

function statusType(status: string) {
  if (status === 'OK') return 'success'
  if (status === 'TIMEOUT') return 'warning'
  return 'danger'
}

function strategyLabel(value: string | null) {
  const labels: Record<string, string> = {
    RAG_QA: 'RAG 问答',
    DANGEROUS_ORDER_OP: '敏感操作',
    TOOL_CALL: 'Tool 调用',
  }
  return value ? labels[value] || value : '-'
}

onMounted(() => loadReport(true))
</script>

<template>
  <div class="online-quality">
    <section class="page-heading">
      <div>
        <h1>线上质量</h1>
        <span v-if="report" class="window-label">
          {{ formatTime(report.startMs) }} 至 {{ formatTime(report.endMs) }}
        </span>
      </div>
    </section>

    <section class="query-bar">
      <el-radio-group v-model="quickRange" @change="onQuickRangeChange">
        <el-radio-button v-for="option in quickOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="dateRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        format="YYYY-MM-DD HH:mm"
        :clearable="false"
        :disabled-date="(date: Date) => date.getTime() > Date.now()"
        @change="onCustomRangeChange"
      />
      <el-button type="primary" :loading="loading" @click="loadReport(true)">
        查询
      </el-button>
    </section>

    <section v-loading="loading" class="summary-grid">
      <article v-for="card in cards" :key="card.label" :class="['summary-card', card.tone]">
        <span class="summary-label">{{ card.label }}</span>
        <div class="summary-value">{{ card.value }}<small>{{ card.suffix }}</small></div>
      </article>
    </section>

    <section v-if="report" class="performance-band">
      <div>
        <span>平均耗时</span>
        <strong>{{ formatDuration(report.avgRequestLatencyMs) }}</strong>
      </div>
      <div>
        <span>P95 耗时</span>
        <strong>{{ formatDuration(report.p95RequestLatencyMs) }}</strong>
      </div>
      <div>
        <span>错误请求</span>
        <strong>{{ formatInteger(report.errorCount) }}</strong>
      </div>
      <div>
        <span>超时请求</span>
        <strong>{{ formatInteger(report.timeoutCount) }}</strong>
      </div>
    </section>

    <section class="data-panel">
      <header class="panel-header">
        <h2>链路统计</h2>
      </header>
      <el-table :data="operationRows" empty-text="当前时间段暂无链路数据">
        <el-table-column prop="name" label="链路" min-width="120" />
        <el-table-column label="调用次数" min-width="120">
          <template #default="scope">{{ formatInteger(scope.row.metric.total) }}</template>
        </el-table-column>
        <el-table-column label="成功" min-width="100">
          <template #default="scope">{{ formatInteger(scope.row.metric.success) }}</template>
        </el-table-column>
        <el-table-column label="失败" min-width="100">
          <template #default="scope">{{ formatInteger(scope.row.metric.failed) }}</template>
        </el-table-column>
        <el-table-column label="成功率" min-width="150">
          <template #default="scope">
            <div class="rate-cell">
              <el-progress :percentage="scope.row.metric.successRate" :stroke-width="6" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="RAG 命中" min-width="180" />
      </el-table>
    </section>

    <section class="data-panel trace-panel">
      <header class="panel-header">
        <h2>最近请求</h2>
        <span>{{ formatInteger(report?.traces.total || 0) }} 条</span>
      </header>
      <el-table :data="report?.traces.records || []" empty-text="当前时间段暂无请求">
        <el-table-column label="时间" min-width="170">
          <template #default="scope">{{ formatTime(scope.row.timestampMs) }}</template>
        </el-table-column>
        <el-table-column prop="traceId" label="Trace ID" min-width="230" show-overflow-tooltip />
        <el-table-column prop="serviceName" label="服务" min-width="170" show-overflow-tooltip />
        <el-table-column label="策略" min-width="140">
          <template #default="scope">{{ strategyLabel(scope.row.planStrategy) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)" effect="plain" size="small">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="110" align="right">
          <template #default="scope">{{ formatDuration(scope.row.durationMs) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="(report?.traces.total || 0) > pageSize" class="pagination-row">
        <el-pagination
          layout="prev, pager, next"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="report?.traces.total || 0"
          @current-change="onPageChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.online-quality {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  min-height: 44px;
}
.page-heading h1 {
  font-size: 22px;
  line-height: 1.25;
  letter-spacing: 0;
  color: #111827;
}
.window-label {
  display: block;
  margin-top: 5px;
  color: #6b7280;
  font-size: 13px;
}
.query-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.summary-grid {
  min-height: 112px;
  display: grid;
  grid-template-columns: repeat(6, minmax(140px, 1fr));
  gap: 12px;
}
.summary-card {
  min-width: 0;
  min-height: 112px;
  padding: 18px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-top: 3px solid #3b82f6;
  border-radius: 8px;
}
.summary-card.green { border-top-color: #16a34a; }
.summary-card.violet { border-top-color: #7c3aed; }
.summary-card.amber { border-top-color: #d97706; }
.summary-label {
  color: #6b7280;
  font-size: 13px;
}
.summary-value {
  margin-top: 14px;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  white-space: nowrap;
}
.summary-value small {
  margin-left: 3px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
}
.performance-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr));
  border-top: 1px solid #d1d5db;
  border-bottom: 1px solid #d1d5db;
  background: #f8fafc;
}
.performance-band > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-right: 1px solid #e5e7eb;
}
.performance-band > div:last-child { border-right: 0; }
.performance-band span { color: #64748b; font-size: 13px; }
.performance-band strong { color: #111827; font-size: 16px; }
.data-panel {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.panel-header {
  min-height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-bottom: 1px solid #e5e7eb;
}
.panel-header h2 {
  font-size: 15px;
  letter-spacing: 0;
  color: #111827;
}
.panel-header span { color: #6b7280; font-size: 13px; }
.rate-cell { width: 130px; }
.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: 14px 18px;
  border-top: 1px solid #e5e7eb;
}
@media (max-width: 1280px) {
  .summary-grid { grid-template-columns: repeat(3, minmax(150px, 1fr)); }
}
@media (max-width: 760px) {
  .query-bar { align-items: stretch; }
  .query-bar :deep(.el-date-editor) { width: 100%; }
  .summary-grid { grid-template-columns: repeat(2, minmax(130px, 1fr)); }
  .performance-band { grid-template-columns: repeat(2, minmax(130px, 1fr)); }
  .performance-band > div:nth-child(2) { border-right: 0; }
  .performance-band > div:nth-child(-n + 2) { border-bottom: 1px solid #e5e7eb; }
}
@media (max-width: 460px) {
  .summary-grid { grid-template-columns: 1fr; }
  .performance-band { grid-template-columns: 1fr; }
  .performance-band > div { border-right: 0; border-bottom: 1px solid #e5e7eb; }
  .performance-band > div:last-child { border-bottom: 0; }
}
</style>
