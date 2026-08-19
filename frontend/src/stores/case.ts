import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as caseApi from '../api/case'
import type { EvalCase, CaseQuery, PageResult } from '../api/case'

export const useCaseStore = defineStore('case', () => {
  const pageResult = ref<PageResult<EvalCase> | null>(null)
  const loading = ref(false)
  const query = ref<CaseQuery>({ page: 1, pageSize: 15 })

  async function fetchList(q?: CaseQuery) {
    if (q) query.value = q
    loading.value = true
    try {
      const res = await caseApi.listCases(query.value)
      pageResult.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function create(data: EvalCase) {
    const res = await caseApi.createCase(data)
    return res.data
  }

  async function update(id: number, data: EvalCase) {
    const res = await caseApi.updateCase(id, data)
    return res.data
  }

  async function remove(id: number) {
    await caseApi.deleteCase(id)
  }

  async function batchRemove(ids: number[]) {
    await caseApi.batchDeleteCases(ids)
  }

  async function batchImport(cases: EvalCase[]) {
    const res = await caseApi.batchImportCases(cases)
    return res.data
  }

  return { pageResult, loading, query, fetchList, create, update, remove, batchRemove, batchImport }
})
