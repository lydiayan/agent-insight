<script setup lang="ts">
import { ref, onMounted, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCaseStore } from '../stores/case'
import type { EvalCase, CaseQuery } from '../api/case'

const store = useCaseStore()

// ==================== 筛选 ====================
const filters = reactive<CaseQuery>({ page: 1, pageSize: 15 })
const categoryOptions = ref<string[]>([])
const loadCategories = async () => {
  categoryOptions.value = ['HR知识', '研发知识', '销售场景', '客户场景', '订单查询', '知识库问答', '敏感订单操作']
}

const personaOptions = [
  { value: 'HR001', label: 'HR001 · 林悦（HRBP）' },
  { value: 'HR002', label: 'HR002 · 陈晨（招聘专员）' },
  { value: 'DEV001', label: 'DEV001 · 周航（后端工程师）' },
  { value: 'DEV002', label: 'DEV002 · 赵宁（平台工程师）' },
  { value: 'SALES001', label: 'SALES001 · 王磊（华东区销售）' },
  { value: 'SALES002', label: 'SALES002 · 刘婷（大客户销售）' },
  { value: 'USER1001', label: 'USER1001 · 张伟（客户）' },
  { value: 'USER1002', label: 'USER1002 · 李娜（客户）' },
]

// ==================== 表格 ====================
const selectedIds = ref<number[]>([])

const onSearch = () => {
  filters.page = 1
  store.fetchList(filters)
}
const onReset = () => {
  filters.keyword = ''
  filters.category = undefined
  filters.agentName = undefined
  filters.tag = undefined
  filters.difficulty = undefined
  filters.page = 1
  store.fetchList(filters)
}
const onPageChange = (page: number) => {
  filters.page = page
  store.fetchList(filters)
}
const onSizeChange = (size: number) => {
  filters.pageSize = size
  filters.page = 1
  store.fetchList(filters)
}
const onSelect = (rows: EvalCase[]) => {
  selectedIds.value = rows.map(r => r.id!).filter(Boolean)
}

// ==================== 新增 / 编辑弹窗 ====================
const dialogVisible = ref(false)
const dialogTitle = ref('新增测试用例')
const editingId = ref<number | null>(null)

const defaultForm = (): EvalCase => ({
  name: '', category: '', agentName: '', agentVersion: '', agentEndpoint: '', actorUserId: 'USER1001', inputQuery: '',
  expectedToolName: '', expectedRequiredParams: '[]',
  expectedParamRules: '', expectedForbiddenParams: '',
  expectedChunk: '', expectedAnswer: '',
  scoreTool: 30, scoreRag: 20, scoreAnswer: 50, passThreshold: 60,
  difficulty: 'simple', remark: '', enabled: 1,
  tags: [],
})
const form = reactive<EvalCase>(defaultForm())

const tagInput = ref('')
const addTag = () => {
  const v = tagInput.value.trim()
  if (v && !form.tags.includes(v)) {
    form.tags.push(v)
    tagInput.value = ''
  }
}
const removeTag = (t: string) => {
  form.tags = form.tags.filter(x => x !== t)
}

const openCreate = () => {
  dialogTitle.value = '新增测试用例'
  editingId.value = null
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

const openEdit = async (row: EvalCase) => {
  if (!row.id) return
  try {
    const { getCaseById } = await import('../api/case')
    const res = await getCaseById(row.id)
    Object.assign(form, res.data)
    editingId.value = row.id
    dialogTitle.value = '编辑测试用例'
    dialogVisible.value = true
  } catch (e) {
    ElMessage.error('获取用例详情失败')
  }
}

const onSave = async () => {
  if (!form.name || !form.category || !form.agentName || !form.inputQuery) {
    ElMessage.warning('请填写名称、分类、Agent 名称和用户输入')
    return
  }
  try {
    if (editingId.value) {
      await store.update(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await store.create({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    store.fetchList(filters)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

const onDelete = async (row: EvalCase) => {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定删除该用例？', '确认', { type: 'warning' })
    await store.remove(row.id)
    ElMessage.success('已删除')
    store.fetchList(filters)
  } catch { /* cancelled */ }
}

const onBatchDelete = async () => {
  if (selectedIds.value.length === 0) { ElMessage.warning('请先勾选用例'); return }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条用例？`, '确认', { type: 'warning' })
    await store.batchRemove(selectedIds.value)
    selectedIds.value = []
    ElMessage.success('已批量删除')
    store.fetchList(filters)
  } catch { /* cancelled */ }
}

const diffLabel = (v: string) => ({ simple: '简单', medium: '中等', hard: '复杂' }[v] || v)

onMounted(async () => {
  await loadCategories()
  store.fetchList(filters)
})

const total = computed(() => store.pageResult?.total ?? 0)
const records = computed(() => store.pageResult?.records ?? [])
</script>

<template>
  <div class="case-page">
    <!-- 左侧筛选 -->
    <aside class="filter-panel">
      <h3 class="panel-title">筛选条件</h3>

      <div class="filter-item">
        <label>关键词</label>
        <el-input v-model="filters.keyword" placeholder="搜索名称/编号" size="default" clearable @keyup.enter="onSearch" />
      </div>
      <div class="filter-item">
        <label>分类</label>
        <el-select v-model="filters.category" placeholder="全部分类" size="default" clearable style="width:100%">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
      </div>
      <div class="filter-item">
        <label>Agent 名称</label>
        <el-input v-model="filters.agentName" placeholder="模糊匹配" size="default" clearable />
      </div>
      <div class="filter-item">
        <label>标签</label>
        <el-input v-model="filters.tag" placeholder="精确匹配" size="default" clearable />
      </div>
      <div class="filter-item">
        <label>难度</label>
        <el-select v-model="filters.difficulty" placeholder="全部难度" size="default" clearable style="width:100%">
          <el-option label="简单" value="simple" />
          <el-option label="中等" value="medium" />
          <el-option label="复杂" value="hard" />
        </el-select>
      </div>

      <div class="filter-actions">
        <el-button type="primary" size="default" @click="onSearch" style="width:100%">搜索</el-button>
        <el-button size="default" @click="onReset" style="width:100%;margin-top:6px">重置</el-button>
      </div>
    </aside>

    <!-- 右侧主内容 -->
    <section class="main-content">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" size="default" @click="openCreate">新增用例</el-button>
          <el-button size="default" @click="onBatchDelete" :disabled="selectedIds.length === 0">
            批量删除 ({{ selectedIds.length }})
          </el-button>
        </div>
        <div class="toolbar-right">
          <span class="total-hint">共 {{ total }} 条</span>
        </div>
      </div>

      <el-table
        :data="records"
        v-loading="store.loading"
        stripe
        size="default"
        style="width:100%"
        @selection-change="onSelect"
        row-key="id"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column prop="caseCode" label="编号" width="100" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90" />
        <el-table-column prop="actorUserId" label="演示身份" width="105" />
        <el-table-column prop="agentName" label="Agent" min-width="140" show-overflow-tooltip />
        <el-table-column label="难度" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.difficulty === 'simple' ? 'success' : row.difficulty === 'hard' ? 'danger' : 'warning'">
              {{ diffLabel(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" width="140">
          <template #default="{ row }">
            <el-tag v-for="t in (row.tags || [])" :key="t" size="small" style="margin:1px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="60">
          <template #default="{ row }">
            <span :style="{ color: row.enabled ? '#10b981' : '#ef4444' }">{{ row.enabled ? '是' : '否' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="155" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.pageSize"
          :page-sizes="[10, 15, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </section>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" destroy-on-close top="4vh">
      <div class="form-scroll">
        <!-- 基本信息 -->
        <fieldset class="fs"><legend>基本信息</legend>
          <div class="form-grid">
            <div class="fg">
              <label>用例名称 <span class="req">*</span></label>
              <el-input v-model="form.name" placeholder="如：股价查询-基础场景" size="default" />
            </div>
            <div class="fg">
              <label>分类 <span class="req">*</span></label>
              <el-select v-model="form.category" placeholder="选择分类" size="default" style="width:100%">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </div>
            <div class="fg">
              <label>Agent 名称 <span class="req">*</span></label>
              <el-input v-model="form.agentName" placeholder="如：金融理财助手-%" size="default" />
            </div>
            <div class="fg">
              <label>Agent 版本</label>
              <el-input v-model="form.agentVersion" placeholder="留空表示所有版本" size="default" />
            </div>
            <div class="fg" style="grid-column:1/-1">
              <label>Agent API 入口</label>
              <el-input v-model="form.agentEndpoint" placeholder="如 http://localhost:8081/api/agent/chat" size="default" />
            </div>
            <div class="fg" style="grid-column:1/-1">
              <label>演示身份</label>
              <el-select v-model="form.actorUserId" placeholder="选择实际调用 Agent 的身份" size="default" style="width:100%" filterable>
                <el-option v-for="p in personaOptions" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </div>
            <div class="fg">
              <label>难度</label>
              <el-select v-model="form.difficulty" size="default" style="width:100%">
                <el-option label="简单" value="simple" />
                <el-option label="中等" value="medium" />
                <el-option label="复杂" value="hard" />
              </el-select>
            </div>
            <div class="fg">
              <label>启用状态</label>
              <el-select v-model="form.enabled" size="default" style="width:100%">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </div>
          </div>
          <div class="fg" style="margin-top:8px">
            <label>标签</label>
            <div class="tag-editor">
              <el-tag v-for="t in form.tags" :key="t" closable size="small" @close="removeTag(t)" style="margin:2px">{{ t }}</el-tag>
              <el-input v-model="tagInput" placeholder="输入标签后回车" size="small" style="width:130px" @keyup.enter="addTag" v-if="form.tags.length < 8" />
            </div>
          </div>
          <div class="fg" style="margin-top:8px">
            <label>备注</label>
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
          </div>
        </fieldset>

        <!-- 输入与预期 -->
        <fieldset class="fs"><legend>输入与预期</legend>
          <div class="fg">
            <label>用户输入 <span class="req">*</span></label>
            <el-input v-model="form.inputQuery" type="textarea" :rows="2" placeholder="如：帮我查一下 AAPL 当前股价" />
          </div>
          <div class="fg" style="margin-top:8px">
            <label>预期调用工具</label>
            <el-input v-model="form.expectedToolName" placeholder="如：stock_price" size="default" />
          </div>
          <div class="form-grid" style="margin-top:8px">
            <div class="fg">
              <label>必要参数 (JSON 数组)</label>
              <el-input v-model="form.expectedRequiredParams" placeholder='["symbol"]' size="default" />
            </div>
            <div class="fg">
              <label>参数校验规则 (JSON)</label>
              <el-input v-model="form.expectedParamRules" placeholder='{"symbol":{"type":"string"}}' size="default" />
            </div>
            <div class="fg">
              <label>禁止参数 (JSON 数组)</label>
              <el-input v-model="form.expectedForbiddenParams" placeholder='["source"]' size="default" />
            </div>
          </div>
          <div class="fg" style="margin-top:8px">
            <label>预期 RAG 召回片段</label>
            <el-input v-model="form.expectedChunk" type="textarea" :rows="2" placeholder="预期检索返回的标准知识库片段" />
          </div>
          <div class="fg" style="margin-top:8px">
            <label>标准答案</label>
            <el-input v-model="form.expectedAnswer" type="textarea" :rows="2" placeholder="预期的正确输出答案" />
          </div>
        </fieldset>

        <!-- 评分权重 -->
        <fieldset class="fs"><legend>评分权重</legend>
          <div class="form-grid">
            <div class="fg">
              <label>Tool (满分)</label>
              <el-input-number v-model="form.scoreTool" :min="0" :max="100" size="default" controls-position="right" />
            </div>
            <div class="fg">
              <label>RAG (满分)</label>
              <el-input-number v-model="form.scoreRag" :min="0" :max="100" size="default" controls-position="right" />
            </div>
            <div class="fg">
              <label>Answer (满分)</label>
              <el-input-number v-model="form.scoreAnswer" :min="0" :max="100" size="default" controls-position="right" />
            </div>
            <div class="fg">
              <label>通过阈值</label>
              <el-input-number v-model="form.passThreshold" :min="0" :max="100" size="default" controls-position="right" />
            </div>
          </div>
        </fieldset>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.case-page {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

/* 左侧筛选 */
.filter-panel {
  width: 220px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  padding: 20px 16px;
}
.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 16px 0;
}
.filter-item {
  margin-bottom: 14px;
}
.filter-item label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
  font-weight: 500;
}
.filter-actions {
  margin-top: 20px;
}

/* 右侧 */
.main-content {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  padding: 16px 20px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar-left {
  display: flex;
  gap: 8px;
}
.total-hint {
  font-size: 13px;
  color: #9ca3af;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* 弹窗表单 */
.form-scroll {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
}
.fs {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px 16px 16px;
  margin-bottom: 14px;
}
.fs legend {
  font-size: 13px;
  font-weight: 700;
  color: #6366f1;
  padding: 0 6px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 14px;
}
.fg {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.fg label {
  font-size: 12px;
  color: #374151;
  font-weight: 500;
}
.req { color: #ef4444; }
.tag-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-height: 30px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 4px 8px;
}
</style>
