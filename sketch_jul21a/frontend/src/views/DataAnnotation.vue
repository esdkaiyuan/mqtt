<template>
  <div class="data-annotation">
    <h1 class="page-title">数据标注</h1>

    <!-- Control Bar -->
    <el-card class="control-card" shadow="hover">
      <div class="control-bar">
        <div class="control-left">
          <el-button type="primary" @click="startSelection" :disabled="isSelecting">
            <el-icon><Select /></el-icon>
            选择数据
          </el-button>
          <el-button @click="clearSelection" :disabled="!hasSelection">
            <el-icon><Close /></el-icon>
            清除选择
          </el-button>
          <el-button type="success" @click="submitAnnotation" :disabled="!hasSelection" :loading="isSaving">
            <el-icon><Check /></el-icon>
            提交标注
          </el-button>
          <el-select
            v-model="batchType"
            placeholder="批量类型"
            style="width: 140px;"
            clearable
          >
            <el-option
              v-for="option in annotationTypes"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-button
            type="warning"
            @click="batchAnnotate"
            :disabled="selectedRows.length === 0 || !batchType"
          >
            <el-icon><Document /></el-icon>
            批量修改
          </el-button>
        </div>
        <div class="control-right">
          <el-select v-model="filterType" placeholder="筛选类型" style="width: 150px;" clearable>
            <el-option
              v-for="option in annotationTypes"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 340px;"
          />
          <el-button @click="loadAnnotations" :loading="isLoading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Selection Info -->
    <el-card v-if="hasSelection" shadow="hover" class="selection-card" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><InfoFilled /></el-icon>
          <span>已选择数据范围</span>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="info-item">
            <span class="info-label">起始时间</span>
            <span class="info-value">{{ formatTime(selectionStartAt) }}</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <span class="info-label">结束时间</span>
            <span class="info-value">{{ formatTime(selectionEndAt) }}</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <span class="info-label">数据点数</span>
            <span class="info-value">{{ selectionCount }}</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <span class="info-label">持续时间</span>
            <span class="info-value">{{ selectionDuration }} ms</span>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- Annotation Form -->
    <el-card v-if="hasSelection" shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><Edit /></el-icon>
          <span>标注信息</span>
        </div>
      </template>
      <el-form :model="annotationForm" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="摔倒类型" required>
              <el-select v-model="annotationForm.type" placeholder="请选择摔倒类型" style="width: 100%;">
                <el-option
                  v-for="option in annotationTypes"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="置信度">
              <el-slider v-model="annotationForm.confidence" :min="0" :max="100" :step="5" show-input />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="annotationForm.notes" type="textarea" :rows="3" placeholder="添加备注信息..." />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标签">
              <el-select v-model="annotationForm.tags" multiple placeholder="添加标签" style="width: 100%;">
                <el-option label="测试数据" value="test" />
                <el-option label="真实摔倒" value="real_fall" />
                <el-option label="误报" value="false_alarm" />
                <el-option label="边界情况" value="edge_case" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据质量">
              <el-rate v-model="annotationForm.quality" :max="5" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- Chart with Selection -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><TrendCharts /></el-icon>
          <span>数据波形</span>
          <el-tag v-if="isSelecting" type="warning" size="small" style="margin-left: 12px;">
            在图表上拖动选择数据范围
          </el-tag>
          <el-tag v-else-if="!store.isConnected" type="info" size="small" style="margin-left: 12px;">
            未连接设备，暂无实时数据
          </el-tag>
        </div>
      </template>
      <div class="chart-container">
        <canvas ref="annotationChart" @mousedown="startDrag"></canvas>
        <div v-if="selectionBox" class="selection-overlay" :style="selectionBoxStyle"></div>
      </div>
    </el-card>

    <!-- Annotations List -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><List /></el-icon>
          <span>标注记录</span>
          <el-badge :value="rows.length" :max="99" class="badge" />
        </div>
      </template>
      <DataTable
        :data="rows"
        :columns="columns"
        :actions="tableActions"
        :selectable="true"
        :loading="isLoading"
        @action="handleAction"
        @selection-change="handleSelectionChange"
      >
        <template #startTime="{ row }">{{ formatTime(row.start_time) }}</template>
        <template #endTime="{ row }">{{ formatTime(row.end_time) }}</template>
        <template #type="{ row }">
          <el-tag size="small" effect="plain">{{ typeLabel(row.type) }}</el-tag>
        </template>
        <template #confidence="{ row }">
          {{ row.confidence == null ? '—' : `${row.confidence}%` }}
        </template>
        <template #createdAt="{ row }">{{ formatTime(row.created_at) }}</template>
      </DataTable>
    </el-card>

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑标注" width="520px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="摔倒类型" required>
          <el-select v-model="editForm.type" placeholder="请选择摔倒类型" style="width: 100%;">
            <el-option
              v-for="option in annotationTypes"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="置信度">
          <el-slider v-model="editForm.confidence" :min="0" :max="100" :step="5" show-input />
        </el-form-item>
        <el-form-item label="数据质量">
          <el-rate v-model="editForm.quality" :max="5" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="editForm.tags" multiple placeholder="添加标签" style="width: 100%;">
            <el-option label="测试数据" value="test" />
            <el-option label="真实摔倒" value="real_fall" />
            <el-option label="误报" value="false_alarm" />
            <el-option label="边界情况" value="edge_case" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.notes" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSaving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { Chart, registerables } from 'chart.js'
import { useMotionStore } from '../stores/motion'
import { annotationApi } from '../services/api'
import { wsService, DEFAULT_DEVICE_ID } from '../services/websocket'
import DataTable from '../components/DataTable.vue'

Chart.register(...registerables)

const store = useMotionStore()

const annotationTypes = [
  { value: 'forward_fall', label: '前倒' },
  { value: 'backward_fall', label: '后倒' },
  { value: 'side_fall', label: '侧倒' },
  { value: 'sit_down', label: '坐下' },
  { value: 'squat', label: '蹲下' },
  { value: 'normal_walk', label: '正常行走' },
  { value: 'other', label: '其他' }
]

const annotationChart = ref(null)
let chartInstance = null
let chartTimer = null

// Selection state
const isSelecting = ref(false)
const hasSelection = ref(false)
const selectionStartAt = ref(null)
const selectionEndAt = ref(null)
const selectionCount = ref(0)
const selectionDuration = ref(0)
const selectionBox = ref(null)
const dragStart = ref(null)

// List state
const isLoading = ref(false)
const isSaving = ref(false)
const filterType = ref('')
const dateRange = ref([])
const selectedRows = ref([])
const batchType = ref('')

// Create form
const annotationForm = ref(createEmptyForm())

// Edit dialog
const editDialogVisible = ref(false)
const editingId = ref(null)
const editForm = ref(createEmptyForm())

const columns = [
  { prop: 'id', label: 'ID', width: '80' },
  { prop: 'start_time', label: '起始时间', width: '180', sortable: true, slot: 'startTime' },
  { prop: 'end_time', label: '结束时间', width: '180', slot: 'endTime' },
  { prop: 'type', label: '类型', width: '110', slot: 'type' },
  { prop: 'confidence', label: '置信度', width: '100', slot: 'confidence' },
  { prop: 'data_count', label: '数据点数', width: '100' },
  { prop: 'notes', label: '备注', width: '200' },
  { prop: 'created_at', label: '创建时间', width: '180', slot: 'createdAt' }
]

const tableActions = [
  { key: 'edit', label: '编辑', type: 'primary' },
  { key: 'delete', label: '删除', type: 'danger' }
]

const rows = computed(() => store.annotations)

function createEmptyForm() {
  return {
    type: '',
    confidence: 80,
    quality: 3,
    tags: [],
    notes: ''
  }
}

function typeLabel(value) {
  return annotationTypes.find(item => item.value === value)?.label || value || '—'
}

function formatTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

const selectionBoxStyle = computed(() => {
  if (!selectionBox.value) return {}
  return {
    left: selectionBox.value.left + 'px',
    top: selectionBox.value.top + 'px',
    width: selectionBox.value.width + 'px',
    height: selectionBox.value.height + 'px'
  }
})

// ---------------------------------------------------------------------------
// Chart
// ---------------------------------------------------------------------------

const initChart = () => {
  if (!annotationChart.value) return

  const ctx = annotationChart.value.getContext('2d')
  chartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels: [],
      datasets: [
        { label: '加速度 X', data: [], borderColor: '#ef4444', borderWidth: 1.5, pointRadius: 0 },
        { label: '加速度 Y', data: [], borderColor: '#22c55e', borderWidth: 1.5, pointRadius: 0 },
        { label: '加速度 Z', data: [], borderColor: '#3b82f6', borderWidth: 1.5, pointRadius: 0 }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      scales: {
        x: { display: true, ticks: { maxTicksLimit: 20 } },
        y: { display: true }
      },
      plugins: {
        legend: { position: 'top' }
      }
    }
  })
}

const updateChart = () => {
  if (!chartInstance) return

  const data = store.realtimeData
  const maxPts = 500
  const start = Math.max(0, data.timestamps.length - maxPts)

  chartInstance.data.labels = data.timestamps.slice(start).map((t, i) => i * 20)
  chartInstance.data.datasets[0].data = data.ax.slice(start)
  chartInstance.data.datasets[1].data = data.ay.slice(start)
  chartInstance.data.datasets[2].data = data.az.slice(start)
  chartInstance.update('none')
}

// ---------------------------------------------------------------------------
// Range selection
// ---------------------------------------------------------------------------

const startSelection = () => {
  isSelecting.value = true
  ElMessage.info('请在图表上点击并拖动选择数据范围')
}

const clearSelection = () => {
  isSelecting.value = false
  hasSelection.value = false
  selectionBox.value = null
  selectionStartAt.value = null
  selectionEndAt.value = null
  selectionCount.value = 0
  selectionDuration.value = 0
  dragStart.value = null
}

const startDrag = (e) => {
  if (!isSelecting.value || !annotationChart.value) return

  const rect = annotationChart.value.getBoundingClientRect()
  dragStart.value = {
    x: e.clientX - rect.left,
    rect
  }

  // Track outside the canvas so releasing the button elsewhere still ends the drag.
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', endDrag)
}

const onDrag = (e) => {
  if (!dragStart.value) return

  const { rect } = dragStart.value
  const currentX = Math.min(Math.max(e.clientX - rect.left, 0), rect.width)

  selectionBox.value = {
    left: Math.min(dragStart.value.x, currentX),
    top: 0,
    width: Math.abs(currentX - dragStart.value.x),
    height: rect.height
  }
}

const endDrag = () => {
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', endDrag)

  if (!dragStart.value || !selectionBox.value) {
    dragStart.value = null
    return
  }

  const data = store.realtimeData
  const total = data.timestamps.length
  const width = dragStart.value.rect.width

  if (total === 0 || width === 0) {
    dragStart.value = null
    return
  }

  const startPercent = selectionBox.value.left / width
  const endPercent = (selectionBox.value.left + selectionBox.value.width) / width

  const startIndex = Math.min(Math.floor(startPercent * total), total - 1)
  const endIndex = Math.min(Math.ceil(endPercent * total), total)

  if (startIndex < endIndex) {
    selectionStartAt.value = data.timestamps[startIndex]
    selectionEndAt.value = data.timestamps[endIndex - 1]
    selectionCount.value = endIndex - startIndex
    selectionDuration.value = selectionEndAt.value - selectionStartAt.value
    hasSelection.value = true
    isSelecting.value = false
    ElMessage.success(`已选择 ${selectionCount.value} 个数据点`)
  } else {
    ElMessage.warning('选择范围过小，请重新拖动')
    selectionBox.value = null
  }

  dragStart.value = null
}

// ---------------------------------------------------------------------------
// Annotation CRUD
// ---------------------------------------------------------------------------

const loadAnnotations = async () => {
  isLoading.value = true
  try {
    const params = { limit: 200 }
    if (filterType.value) params.type = filterType.value
    if (dateRange.value?.length === 2) {
      params.start_time = new Date(dateRange.value[0]).toISOString()
      params.end_time = new Date(dateRange.value[1]).toISOString()
    }

    const response = await annotationApi.getAnnotations(params)
    store.setAnnotations(response.data || [])
  } catch (error) {
    ElMessage.error('加载标注记录失败')
    console.error(error)
  } finally {
    isLoading.value = false
  }
}

const submitAnnotation = async () => {
  if (!annotationForm.value.type) {
    ElMessage.warning('请选择摔倒类型')
    return
  }

  isSaving.value = true
  try {
    await annotationApi.saveAnnotation({
      type: annotationForm.value.type,
      start_time: new Date(selectionStartAt.value).toISOString(),
      end_time: new Date(selectionEndAt.value).toISOString(),
      device_id: store.deviceInfo.deviceId || wsService.deviceId || DEFAULT_DEVICE_ID,
      data_count: selectionCount.value,
      confidence: annotationForm.value.confidence,
      quality: annotationForm.value.quality,
      tags: annotationForm.value.tags,
      notes: annotationForm.value.notes || null
    })

    ElMessage.success('标注已保存')
    annotationForm.value = createEmptyForm()
    clearSelection()
    await loadAnnotations()
  } catch (error) {
    ElMessage.error(error.response?.data?.detail || '标注保存失败')
    console.error(error)
  } finally {
    isSaving.value = false
  }
}

const handleAction = ({ key, row }) => {
  if (key === 'edit') {
    editingId.value = row.id
    editForm.value = {
      type: row.type,
      confidence: row.confidence ?? 80,
      quality: row.quality ?? 3,
      tags: [...(row.tags || [])],
      notes: row.notes || ''
    }
    editDialogVisible.value = true
  } else if (key === 'delete') {
    ElMessageBox.confirm('确定要删除这条标注吗？', '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        await annotationApi.deleteAnnotation(row.id)
        ElMessage.success('已删除')
        await loadAnnotations()
      } catch (error) {
        ElMessage.error(error.response?.data?.detail || '删除失败')
      }
    }).catch(() => {})
  }
}

const saveEdit = async () => {
  if (!editForm.value.type) {
    ElMessage.warning('请选择摔倒类型')
    return
  }

  isSaving.value = true
  try {
    await annotationApi.updateAnnotation(editingId.value, {
      type: editForm.value.type,
      confidence: editForm.value.confidence,
      quality: editForm.value.quality,
      tags: editForm.value.tags,
      notes: editForm.value.notes || null
    })
    ElMessage.success('标注已更新')
    editDialogVisible.value = false
    await loadAnnotations()
  } catch (error) {
    ElMessage.error(error.response?.data?.detail || '更新失败')
    console.error(error)
  } finally {
    isSaving.value = false
  }
}

const batchAnnotate = async () => {
  const targets = selectedRows.value
  if (targets.length === 0 || !batchType.value) return

  try {
    await ElMessageBox.confirm(
      `确定要把选中的 ${targets.length} 条标注改为「${typeLabel(batchType.value)}」吗？`,
      '确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  isSaving.value = true
  try {
    await Promise.all(
      targets.map(row => annotationApi.updateAnnotation(row.id, { type: batchType.value }))
    )
    ElMessage.success(`已更新 ${targets.length} 条标注`)
    batchType.value = ''
    await loadAnnotations()
  } catch (error) {
    ElMessage.error(error.response?.data?.detail || '批量修改失败')
    console.error(error)
  } finally {
    isSaving.value = false
  }
}

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

watch([filterType, dateRange], loadAnnotations)

onMounted(() => {
  initChart()
  chartTimer = setInterval(updateChart, 100)
  loadAnnotations()
})

onUnmounted(() => {
  if (chartTimer) clearInterval(chartTimer)
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', endDrag)
  if (chartInstance) chartInstance.destroy()
})
</script>

<style scoped>
.data-annotation {
  padding: 0;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 24px;
}

.control-card {
  margin-bottom: 20px;
}

.control-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.control-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.control-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.badge {
  margin-left: auto;
}

.selection-card {
  background: #f0f9ff;
  border: 1px solid #bae6fd;
}

.info-item {
  text-align: center;
}

.info-label {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.info-value {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.chart-container {
  height: 300px;
  position: relative;
}

canvas {
  width: 100% !important;
  height: 100% !important;
  cursor: crosshair;
}

.selection-overlay {
  position: absolute;
  background: rgba(59, 130, 246, 0.2);
  border: 2px solid #3b82f6;
  pointer-events: none;
  z-index: 10;
}
</style>