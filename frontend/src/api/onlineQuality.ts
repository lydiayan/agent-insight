import api from './index'

export interface OperationMetric {
  total: number
  success: number
  failed: number
  successRate: number
}

export interface RequestTrace {
  traceId: string
  serviceName: string
  status: string
  timestampMs: number
  durationMs: number | null
  planStrategy: string | null
  interrupted: boolean | null
  grounded: boolean | null
}

export interface OnlineQualityReport {
  startMs: number
  endMs: number
  requests: OperationMetric
  toolCalls: OperationMetric
  ragCalls: OperationMetric
  ragHitCount: number
  ragHitRate: number
  avgRequestLatencyMs: number
  p95RequestLatencyMs: number
  errorCount: number
  timeoutCount: number
  traces: {
    total: number
    page: number
    pageSize: number
    records: RequestTrace[]
  }
}

export function getOnlineQualityReport(params: {
  startMs: number
  endMs: number
  page?: number
  pageSize?: number
}) {
  return api.get<any, { code: number; message: string; data: OnlineQualityReport }>(
    '/online-quality/report',
    { params },
  )
}
