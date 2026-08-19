import api from './index'

export interface EvalCase {
  id?: number
  caseCode?: string
  name: string
  category: string
  agentName: string
  agentVersion?: string
  agentEndpoint?: string
  actorUserId?: string
  inputQuery: string
  expectedToolName?: string
  expectedRequiredParams?: string
  expectedParamRules?: string
  expectedForbiddenParams?: string
  expectedChunk?: string
  expectedAnswer?: string
  scoreTool: number
  scoreRag: number
  scoreAnswer: number
  passThreshold: number
  difficulty: string
  remark?: string
  enabled: number
  tags: string[]
  createdAt?: string
  updatedAt?: string
}

export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  records: T[]
}

export interface CaseQuery {
  page?: number
  pageSize?: number
  category?: string
  agentName?: string
  tag?: string
  difficulty?: string
  enabled?: number
  keyword?: string
}

export function listCases(params: CaseQuery) {
  return api.get<any, { code: number; data: PageResult<EvalCase> }>('/case/list', { params })
}

export function getCaseById(id: number) {
  return api.get<any, { code: number; data: EvalCase }>(`/case/${id}`)
}

export function createCase(data: EvalCase) {
  return api.post<any, { code: number; data: EvalCase }>('/case', data)
}

export function updateCase(id: number, data: EvalCase) {
  return api.put<any, { code: number; data: EvalCase }>(`/case/${id}`, data)
}

export function deleteCase(id: number) {
  return api.delete<any, { code: number }>(`/case/${id}`)
}

export function batchDeleteCases(ids: number[]) {
  return api.delete<any, { code: number }>('/case/batch', { data: ids })
}

export function batchImportCases(cases: EvalCase[]) {
  return api.post<any, { code: number; data: number }>('/case/batchImport', cases)
}
