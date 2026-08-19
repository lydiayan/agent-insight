import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as evalApi from '../api/evaluation'
import type { EvalTask, EvalTaskDetail } from '../api/evaluation'

export const useEvalStore = defineStore('evaluation', () => {
  const tasks = ref<EvalTask[]>([])
  const taskDetail = ref<EvalTaskDetail | null>(null)
  const loading = ref(false)

  async function fetchTasks() {
    loading.value = true
    try {
      const res = await evalApi.listEvalTasks()
      tasks.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function run(caseIds: number[], taskName?: string, agentEndpoint?: string) {
    const res = await evalApi.runEvaluation(caseIds, taskName, agentEndpoint)
    tasks.value = [res.data, ...tasks.value.filter(task => task.id !== res.data.id)]
    return res.data
  }

  async function fetchTaskDetail(taskId: number) {
    loading.value = true
    try {
      const res = await evalApi.getEvalTaskDetail(taskId)
      taskDetail.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function collectTraceAndScore(taskId: number) {
    const res = await evalApi.collectTraceAndScore(taskId)
    taskDetail.value = res.data
    return res.data
  }

  return { tasks, taskDetail, loading, fetchTasks, run, fetchTaskDetail, collectTraceAndScore }
})
