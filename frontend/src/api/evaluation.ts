import api from './index'

const EVALUATION_SUBMIT_TIMEOUT_MS = 10_000

export interface EvalTask {
  id?: number
  taskName: string
  caseCount: number
  completed: number
  passedCount: number
  failedCount: number
  avgScore: number | null
  status: string
  errorMsg?: string
  createdAt?: string
  updatedAt?: string
}

export interface CaseResultItem {
  caseId: number
  caseCode: string
  caseName: string
  expectedToolCalled: boolean
  expectedToolName?: string
  expectedRagCalled: boolean
  expectedHumanConfirmation: boolean
  actualToolCalled?: boolean | null
  actualRagCalled?: boolean | null
  scoreTool: number
  scoreRag: number
  scoreAnswer: number
  scoreTotal: number
  scoreMax: number
  passed: boolean
  failureReasons: string
  agentOutput?: string
  agentTraceId?: string
  planStrategy?: string
  conversationId?: string
  interrupted?: boolean
  httpStatus?: number
  rawResponse?: string
  collectionStatus?: 'trace_pending' | 'trace_incomplete' | 'scored' | 'call_failed'
  collectionMessage?: string
  invokedAt?: string
  collectedAt?: string
  evalTime: string
}

export interface EvalTaskDetail {
  taskId: number
  taskName: string
  status: string
  caseCount: number
  completed: number
  passedCount: number
  failedCount: number
  avgScore: number | null
  items: CaseResultItem[]
}

export function runEvaluation(caseIds: number[], taskName?: string, agentEndpoint?: string) {
  return api.post<any, { code: number; data: EvalTask }>('/evaluation/run', {
    caseIds,
    taskName,
    agentEndpoint,
  }, {
    timeout: EVALUATION_SUBMIT_TIMEOUT_MS,
  })
}

export function listEvalTasks() {
  return api.get<any, { code: number; data: EvalTask[] }>('/evaluation/task/list')
}

export function getEvalTaskDetail(taskId: number) {
  return api.get<any, { code: number; data: EvalTaskDetail }>(`/evaluation/task/${taskId}`)
}

export function collectTraceAndScore(taskId: number) {
  return api.post<any, { code: number; data: EvalTaskDetail }>(
    `/evaluation/task/${taskId}/collect-trace`,
  )
}
