<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useEvalStore } from '../stores/evaluation'
import { useCaseStore } from '../stores/case'
import type { EvalTask, CaseResultItem } from '../api/evaluation'

const evalStore = useEvalStore()
const caseStore = useCaseStore()

// ==================== 发起评测弹窗 ====================
const runDialogVisible = ref(false)
const submitting = ref(false)
const ECOMMERCE_AGENT_ENDPOINT = 'http://127.0.0.1:8087/agent/order/ask'
const runForm = reactive({
  taskName: '',
  agentEndpoint: ECOMMERCE_AGENT_ENDPOINT,
  selectedCaseIds: [] as number[],
})

const openRunDialog = () => {
  runForm.taskName = ''
  runForm.agentEndpoint = ECOMMERCE_AGENT_ENDPOINT
  runForm.selectedCaseIds = []
  runDialogVisible.value = true
}

const onRun = async () => {
  if (runForm.selectedCaseIds.length === 0) { ElMessage.warning('请至少选择一条用例'); return }
  submitting.value = true
  try {
    const createdTask = await evalStore.run(
      runForm.selectedCaseIds,
      runForm.taskName || undefined,
      runForm.agentEndpoint || undefined,
    )
    runDialogVisible.value = false
    ElMessage.success('评测任务已创建，正在后台执行')
    await evalStore.fetchTasks()
    const task = evalStore.tasks.find(item => item.id === createdTask.id) || createdTask
    await openDetail(task)
    scheduleTaskPolling()
  } catch (e: any) {
    ElMessage.error(e?.message || '任务创建失败')
  } finally {
    submitting.value = false
  }
}

// ==================== 任务详情 ====================
const detailPanel = ref(false)
const currentTask = ref<EvalTask | null>(null)
const detailItems = ref<CaseResultItem[]>([])
const detailLoading = ref(false)
const collectingTrace = ref(false)

const openDetail = async (task: EvalTask) => {
  currentTask.value = task
  detailPanel.value = true
  detailLoading.value = true
  try {
    await evalStore.fetchTaskDetail(task.id!)
    detailItems.value = evalStore.taskDetail?.items || []
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => { detailPanel.value = false }

const ACTIVE_TASK_STATUSES = new Set(['pending', 'running'])
const pollingTasks = ref(false)
let taskPollTimer: number | undefined

const isTaskActive = (status?: string) => !!status && ACTIVE_TASK_STATUSES.has(status)
const taskProgress = (task: EvalTask) => {
  if (!task.caseCount) return 0
  return Math.min(100, Math.round((task.completed || 0) * 100 / task.caseCount))
}

const stopTaskPolling = () => {
  if (taskPollTimer !== undefined) {
    window.clearTimeout(taskPollTimer)
    taskPollTimer = undefined
  }
}

const scheduleTaskPolling = () => {
  stopTaskPolling()
  if (!evalStore.tasks.some(task => isTaskActive(task.status))) return
  taskPollTimer = window.setTimeout(refreshRunningTasks, 2500)
}

async function refreshRunningTasks() {
  if (pollingTasks.value) return
  pollingTasks.value = true
  const previousStatus = currentTask.value?.status
  try {
    await evalStore.fetchTasks()
    const refreshed = evalStore.tasks.find(task => task.id === currentTask.value?.id)
    if (refreshed) {
      currentTask.value = refreshed
      if (detailPanel.value && (isTaskActive(previousStatus) || isTaskActive(refreshed.status))) {
        await evalStore.fetchTaskDetail(refreshed.id!)
        detailItems.value = evalStore.taskDetail?.items || []
      }
      if (isTaskActive(previousStatus) && !isTaskActive(refreshed.status)) {
        if (refreshed.status === 'failed') {
          ElMessage.error(refreshed.errorMsg || '评测任务执行失败')
        } else {
          ElMessage.success('Agent 调用完成，可以获取 Trace 并评分')
        }
      }
    }
  } catch (error) {
    console.error('[Evaluation polling]', error)
  } finally {
    pollingTasks.value = false
    scheduleTaskPolling()
  }
}

const canCollectTrace = computed(() => detailItems.value.some(item =>
  item.collectionStatus === 'trace_pending' || item.collectionStatus === 'trace_incomplete',
))

const onCollectTrace = async () => {
  if (!currentTask.value?.id || !canCollectTrace.value) return
  collectingTrace.value = true
  try {
    const detail = await evalStore.collectTraceAndScore(currentTask.value.id)
    detailItems.value = detail.items || []
    await evalStore.fetchTasks()
    const refreshed = evalStore.tasks.find(task => task.id === currentTask.value?.id)
    if (refreshed) currentTask.value = refreshed

    const waitingCount = detail.items.filter(item =>
      item.collectionStatus === 'trace_pending' || item.collectionStatus === 'trace_incomplete',
    ).length
    if (waitingCount > 0) {
      ElMessage.warning(`${waitingCount} 条用例的 Trace 尚未完整，可稍后再次获取`)
    } else {
      ElMessage.success('Trace 获取完成，评分结果已更新')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '获取 Trace 并评分失败')
  } finally {
    collectingTrace.value = false
  }
}

const statusTag = (s: string) =>
  ({ pending: 'info', running: 'warning', trace_pending: 'warning', completed: 'success', failed: 'danger' } as Record<string, string>)[s] || 'info'
const statusLabel = (s: string) =>
  ({ pending: '等待中', running: '运行中', trace_pending: '等待 Trace', completed: '已完成', failed: '失败' } as Record<string, string>)[s] || s

const collectionLabel = (s?: CaseResultItem['collectionStatus']) =>
  ({
    trace_pending: '待获取 Trace',
    trace_incomplete: 'Trace 未完整',
    scored: '已评分',
    call_failed: 'Agent 调用失败',
  } as Record<string, string>)[s || 'scored'] || s

const collectionTag = (s?: CaseResultItem['collectionStatus']) =>
  ({
    trace_pending: 'info',
    trace_incomplete: 'warning',
    scored: 'success',
    call_failed: 'danger',
  } as Record<string, string>)[s || 'scored'] || 'info'

const isScored = (item: CaseResultItem) => !item.collectionStatus || item.collectionStatus === 'scored'

type SignalKind = 'tool' | 'rag' | 'human'

const expectedSignal = (item: CaseResultItem, kind: SignalKind) => {
  if (kind === 'tool') return item.expectedToolCalled
  if (kind === 'rag') return item.expectedRagCalled
  return item.expectedHumanConfirmation
}

const actualSignal = (item: CaseResultItem, kind: SignalKind) => {
  if (kind === 'tool') return item.actualToolCalled
  if (kind === 'rag') return item.actualRagCalled
  return item.interrupted
}

const signalName = (kind: SignalKind) =>
  ({ tool: 'Tool', rag: 'RAG', human: '人工确认' } as const)[kind]

const expectedSignalLabel = (item: CaseResultItem, kind: SignalKind) => {
  const expected = expectedSignal(item, kind)
  if (kind === 'tool' && expected && item.expectedToolName) {
    return `Tool 需要 · ${item.expectedToolName}`
  }
  return `${signalName(kind)} ${expected ? '需要' : '不需要'}`
}

const actualSignalLabel = (item: CaseResultItem, kind: SignalKind) => {
  const actual = actualSignal(item, kind)
  if (actual == null) {
    const emptyLabel = kind === 'human'
      ? '未记录'
      : item.collectionStatus === 'call_failed' ? '无法判断' : '待采集'
    return `${signalName(kind)} ${emptyLabel}`
  }
  if (kind === 'human') return `人工确认 ${actual ? '已中断' : '未中断'}`
  return `${signalName(kind)} ${actual ? '已调用' : '未调用'}`
}

const actualSignalTag = (item: CaseResultItem, kind: SignalKind) => {
  const actual = actualSignal(item, kind)
  if (actual == null) return 'info'
  return actual === expectedSignal(item, kind) ? 'success' : 'danger'
}

const scoreColor = (s: number, max: number) => {
  const ratio = max > 0 ? s / max : 0
  if (ratio >= 0.8) return '#10b981'
  if (ratio >= 0.5) return '#f59e0b'
  return '#ef4444'
}

// 所有用例列表（供选择）
const allCases = computed(() => caseStore.pageResult?.records || [])

onMounted(async () => {
  await Promise.all([
    evalStore.fetchTasks(),
    caseStore.fetchList({ page: 1, pageSize: 100 }),
  ])
  scheduleTaskPolling()
})

onBeforeUnmount(stopTaskPolling)
</script>

<template>
  <div class="eval-page">
    <!-- 左侧：任务列表 + 发起评测 -->
    <aside class="eval-sidebar">
      <div class="sidebar-header">
        <h3>评测任务</h3>
        <el-button type="primary" size="small" @click="openRunDialog">执行评测</el-button>
      </div>

      <div class="task-list">
        <div
          v-for="task in evalStore.tasks"
          :key="task.id"
          :class="['task-card', { active: currentTask?.id === task.id }]"
          @click="openDetail(task)"
        >
          <div class="task-name">{{ task.taskName }}</div>
          <div class="task-meta">
            <el-tag :type="statusTag(task.status)" size="small">{{ statusLabel(task.status) }}</el-tag>
            <span class="task-stats">
              <span class="stat good">{{ task.passedCount }}</span>
              <span>/</span>
              <span class="stat bad">{{ task.failedCount }}</span>
              <span>/</span>
              <span>{{ task.caseCount }}</span>
              <span style="color:#9ca3af;margin-left:4px">通过/失败/总数</span>
            </span>
          </div>
          <div v-if="task.avgScore != null" class="task-score">
            平均 {{ task.avgScore }} 分
          </div>
          <div v-if="isTaskActive(task.status)" class="task-progress">
            <el-progress :percentage="taskProgress(task)" :stroke-width="5" :show-text="false" />
            <span>{{ task.status === 'pending' ? '等待执行' : `${task.completed || 0}/${task.caseCount} 已执行` }}</span>
          </div>
        </div>

        <div v-if="evalStore.tasks.length === 0" class="empty-hint">
          暂无评测任务，点击上方按钮执行评测
        </div>
      </div>
    </aside>

    <!-- 右侧：任务详情 -->
    <section :class="['eval-main', { 'has-detail': detailPanel }]">
      <template v-if="detailPanel && currentTask">
        <div class="detail-header">
          <div>
            <h2>{{ currentTask.taskName }}</h2>
            <el-tag :type="statusTag(currentTask.status)" size="small">{{ statusLabel(currentTask.status) }}</el-tag>
            <span style="margin-left:12px;font-size:13px;color:#6b7280">
              通过 {{ currentTask.passedCount }} / 失败 {{ currentTask.failedCount }} / 总计 {{ currentTask.caseCount }}
            </span>
          </div>
          <div class="detail-actions">
            <el-button
              v-if="canCollectTrace"
              type="primary"
              size="small"
              :loading="collectingTrace"
              @click="onCollectTrace"
            >
              获取 Trace 并评分
            </el-button>
            <el-button size="small" @click="closeDetail">关闭</el-button>
          </div>
        </div>

        <div v-loading="detailLoading" class="detail-body">
          <div v-if="isTaskActive(currentTask.status)" class="running-banner">
            <div class="running-title">
              {{ currentTask.status === 'pending' ? '任务正在排队' : '正在调用 Agent' }}
              <span>{{ currentTask.completed || 0 }} / {{ currentTask.caseCount }}</span>
            </div>
            <el-progress :percentage="taskProgress(currentTask)" :stroke-width="7" />
          </div>
          <div v-else-if="currentTask.status === 'failed' && currentTask.errorMsg" class="task-error">
            {{ currentTask.errorMsg }}
          </div>
          <div v-if="detailItems.length === 0 && isTaskActive(currentTask.status)" class="running-empty">
            等待首条用例执行结果
          </div>
          <div v-for="item in detailItems" :key="item.caseId" class="result-row">
            <div class="result-left">
              <span v-if="isScored(item)" :style="{color: item.passed ? '#10b981' : '#ef4444', fontWeight:700}">
                {{ item.passed ? '✓' : '✗' }}
              </span>
              <span v-else-if="item.collectionStatus === 'call_failed'" class="call-failed-indicator">✗</span>
              <span v-else class="pending-indicator"></span>
              <span class="result-code">{{ item.caseCode }}</span>
              <span class="result-name">{{ item.caseName }}</span>
              <el-tag :type="collectionTag(item.collectionStatus)" size="small">
                {{ collectionLabel(item.collectionStatus) }}
              </el-tag>
            </div>
            <div class="signal-comparison">
              <div class="signal-row">
                <span class="signal-row-label">期望</span>
                <el-tag
                  v-for="kind in (['tool', 'rag', 'human'] as SignalKind[])"
                  :key="`expected-${kind}`"
                  :type="expectedSignal(item, kind) ? 'primary' : 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ expectedSignalLabel(item, kind) }}
                </el-tag>
              </div>
              <div class="signal-row">
                <span class="signal-row-label">实际</span>
                <el-tag
                  v-for="kind in (['tool', 'rag', 'human'] as SignalKind[])"
                  :key="`actual-${kind}`"
                  :type="actualSignalTag(item, kind)"
                  size="small"
                >
                  {{ actualSignalLabel(item, kind) }}
                </el-tag>
              </div>
            </div>
            <div v-if="isScored(item)" class="result-scores">
              <span class="score-item">Tool <b :style="{color:scoreColor(item.scoreTool,30)}">{{ item.scoreTool }}</b>/30</span>
              <span class="score-item">RAG <b :style="{color:scoreColor(item.scoreRag,20)}">{{ item.scoreRag }}</b>/20</span>
              <span class="score-item">Answer <b :style="{color:scoreColor(item.scoreAnswer,50)}">{{ item.scoreAnswer }}</b>/50</span>
              <span class="score-total" :style="{color:scoreColor(item.scoreTotal,item.scoreMax)}">
                {{ item.scoreTotal }}/{{ item.scoreMax }}
              </span>
            </div>
            <div v-else-if="item.collectionStatus !== 'call_failed'" class="collection-waiting">
              评分尚未执行。系统只会在点击按钮时读取一次 ES Trace。
            </div>
            <div v-if="item.collectionMessage" class="collection-message">
              {{ item.collectionMessage }}
            </div>
            <div v-if="item.failureReasons" class="result-reasons">
              {{ item.failureReasons }}
            </div>
            <details class="agent-output">
              <summary>查看 Agent 输出详情</summary>
              <div v-if="item.agentOutput" class="agent-answer">{{ item.agentOutput }}</div>
              <div v-else class="agent-output-empty">该历史评测未保存 Agent 输出</div>
              <div class="agent-meta">
                <span v-if="item.planStrategy">策略：{{ item.planStrategy }}</span>
                <span v-if="item.httpStatus">HTTP：{{ item.httpStatus }}</span>
                <span v-if="item.interrupted">等待人工确认</span>
              </div>
              <div v-if="item.agentTraceId" class="trace-id">Trace ID：{{ item.agentTraceId }}</div>
              <div v-if="item.conversationId" class="trace-id">会话 ID：{{ item.conversationId }}</div>
              <details v-if="item.rawResponse" class="raw-response">
                <summary>查看原始响应</summary>
                <pre>{{ item.rawResponse }}</pre>
              </details>
            </details>
          </div>
        </div>
      </template>

      <div v-else class="no-detail">
        点击左侧任务查看评测结果
      </div>
    </section>

    <!-- 发起评测弹窗 -->
    <el-dialog
      v-model="runDialogVisible"
      title="执行评测"
      width="600px"
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <div class="run-form">
        <div class="run-field">
          <label>任务名称</label>
          <el-input v-model="runForm.taskName" placeholder="不填则自动生成" size="default" />
        </div>
        <div class="run-field">
          <label>Agent 接口</label>
          <el-input v-model="runForm.agentEndpoint" size="default" />
          <div class="field-hint">
            默认调用 EcommSpringBot 的 mall-order-agent /agent/order/ask 接口
          </div>
        </div>
        <div class="run-field">
          <label>选择测试用例 <span style="color:#ef4444">*</span></label>
          <div class="case-checklist">
            <el-checkbox
              v-for="c in allCases"
              :key="c.id"
              :model-value="runForm.selectedCaseIds.includes(c.id!)"
              :label="`${c.caseCode || ''} ${c.name}`"
              size="default"
              @change="(val: boolean) => {
                if (val) runForm.selectedCaseIds.push(c.id!)
                else runForm.selectedCaseIds = runForm.selectedCaseIds.filter(id => id !== c.id)
              }"
            />
          </div>
          <div style="margin-top:6px;font-size:12px;color:#9ca3af">
            已选 {{ runForm.selectedCaseIds.length }} 条
          </div>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="submitting" @click="runDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onRun">
          {{ submitting ? '正在创建任务' : '开始评测' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.eval-page { display: flex; gap: 20px; height: calc(100vh - 104px); }

/* 左侧 */
.eval-sidebar {
  width: 320px; flex-shrink: 0;
  background: #fff; border-radius: 10px; border: 1px solid #e5e7eb;
  display: flex; flex-direction: column;
}
.sidebar-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 16px 12px; border-bottom: 1px solid #f3f4f6;
}
.sidebar-header h3 { font-size: 15px; font-weight: 700; margin:0; }

.task-list { flex:1; overflow-y: auto; padding: 8px; }
.task-card {
  padding: 12px; border-radius: 8px; cursor: pointer; transition: background .15s;
  margin-bottom: 4px;
}
.task-card:hover { background: #f9fafb; }
.task-card.active { background: #eef2ff; }

.task-name { font-size: 14px; font-weight: 600; margin-bottom: 6px; }
.task-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #6b7280; }
.task-stats { display: flex; align-items: center; gap: 2px; }
.stat { font-weight: 600; }
.stat.good { color: #10b981; }
.stat.bad { color: #ef4444; }
.task-score { font-size: 12px; color: #6366f1; font-weight: 600; margin-top: 4px; }
.task-progress { margin-top: 9px; display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 8px; }
.task-progress span { color: #64748b; font-size: 11px; white-space: nowrap; }
.empty-hint { text-align: center; color: #9ca3af; font-size: 13px; padding: 40px 0; }

/* 右侧 */
.eval-main {
  flex: 1; min-width: 0;
  background: #fff; border-radius: 10px; border: 1px solid #e5e7eb;
  display: flex; flex-direction: column;
}
.no-detail {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: #9ca3af; font-size: 14px;
}

.detail-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid #f3f4f6;
}
.detail-header h2 { font-size: 16px; margin: 0 0 6px 0; }
.detail-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

.detail-body { flex: 1; overflow-y: auto; padding: 12px 20px; }
.running-banner {
  margin: 2px 0 12px; padding: 12px 14px; border-left: 3px solid #2563eb;
  background: #eff6ff;
}
.running-title {
  display: flex; justify-content: space-between; margin-bottom: 9px;
  color: #1e3a8a; font-size: 13px; font-weight: 600;
}
.running-title span { color: #475569; font-weight: 500; }
.running-empty { padding: 36px 0; text-align: center; color: #94a3b8; font-size: 13px; }
.task-error {
  margin: 2px 0 12px; padding: 11px 13px; border-left: 3px solid #dc2626;
  background: #fef2f2; color: #991b1b; font-size: 13px;
}
.result-row {
  padding: 12px; border-bottom: 1px solid #f3f4f6;
}
.result-left { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.result-code { font-size: 12px; color: #9ca3af; }
.result-name { font-size: 14px; font-weight: 500; }
.pending-indicator {
  width: 9px; height: 9px; border-radius: 50%; flex: 0 0 9px;
  background: #f59e0b; box-shadow: 0 0 0 3px #fef3c7;
}
.call-failed-indicator { color: #ef4444; font-weight: 700; }
.signal-comparison {
  display: grid; gap: 6px; margin: 4px 0 10px; padding: 9px 10px;
  border: 1px solid #e5e7eb; border-radius: 7px; background: #f8fafc;
}
.signal-row { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; min-width: 0; }
.signal-row-label {
  width: 32px; flex: 0 0 32px; color: #64748b; font-size: 12px; font-weight: 600;
}
.result-scores { display: flex; align-items: center; gap: 16px; font-size: 13px; }
.score-item b { font-weight: 700; }
.score-total { font-weight: 700; font-size: 15px; margin-left: auto; }
.collection-waiting {
  padding: 8px 10px; border-left: 3px solid #f59e0b;
  background: #fffbeb; color: #92400e; font-size: 12px; line-height: 1.5;
}
.collection-message { margin-top: 6px; color: #b45309; font-size: 12px; }
.result-reasons { margin-top: 6px; font-size: 12px; color: #ef4444; }
.agent-output {
  margin-top: 10px; border-radius: 7px; background: #f8fafc;
  border: 1px solid #e5e7eb; padding: 9px 11px;
}
.agent-output > summary, .raw-response > summary {
  cursor: pointer; color: #4f46e5; font-size: 12px; font-weight: 600;
}
.agent-answer {
  margin-top: 10px; color: #374151; font-size: 13px; line-height: 1.65;
  white-space: pre-wrap; word-break: break-word;
}
.agent-output-empty { margin-top: 9px; color: #9ca3af; font-size: 12px; }
.agent-meta { display: flex; gap: 12px; margin-top: 9px; color: #64748b; font-size: 12px; }
.trace-id { margin-top: 5px; color: #94a3b8; font: 11px/1.5 monospace; word-break: break-all; }
.raw-response { margin-top: 8px; }
.raw-response pre {
  max-height: 220px; overflow: auto; margin: 8px 0 0; padding: 9px;
  border-radius: 6px; background: #111827; color: #d1fae5;
  font: 11px/1.5 monospace; white-space: pre-wrap; word-break: break-word;
}

/* 弹窗 */
.run-field { margin-bottom: 16px; }
.run-field label { display: block; font-size: 13px; font-weight: 600; color: #374151; margin-bottom: 4px; }
.field-hint { margin-top: 5px; font-size: 12px; color: #9ca3af; }
.case-checklist {
  max-height: 300px; overflow-y: auto; border: 1px solid #e5e7eb; border-radius: 8px;
  padding: 8px; display: flex; flex-direction: column; gap: 6px;
}

@media (max-width: 900px) {
  .eval-page {
    height: auto; min-height: calc(100vh - 84px); flex-direction: column; gap: 12px;
  }
  .eval-sidebar { width: 100%; max-height: 260px; min-height: 220px; }
  .eval-main { min-height: 520px; }
  .detail-header { align-items: flex-start; gap: 12px; }
  .result-scores { flex-wrap: wrap; row-gap: 8px; }
  .score-total { margin-left: 0; }
}

@media (max-width: 560px) {
  .detail-header { padding: 14px 12px; flex-direction: column; }
  .detail-actions { width: 100%; }
  .detail-body { padding: 10px 12px; }
  .result-row { padding: 12px 4px; }
  .result-left { flex-wrap: wrap; }
  .signal-row-label { width: 100%; flex-basis: 100%; }
  .agent-meta { flex-wrap: wrap; }
}
</style>
