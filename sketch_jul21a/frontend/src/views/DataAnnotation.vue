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
          <el-button type="success" @click="submitAnnotation" :disabled="!hasSelection">
            <el-icon><Check /></el-icon>
            提交标注
          </el-button>
          <el-button type="warning" @click="batchAnnotate" :disabled="selectedRows.length === 0">
            <el-icon><Document /></el-icon>
            批量标注
          </el-button>
        </div>
        <div class="control-right">
          <el-select v-model="filterType" placeholder="筛选类型" style="width: 150px;" clearable>
            <el-option label="摔倒" value="fall" />
            <el-option label="疑似摔倒" value="suspected_fall" />
            <el-option label="正常" value="normal" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px;"
          />
          <el-button @click="loadData">
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
            <span class="info-value">{{ selectionStart }}</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <span class="info-label">结束时间</span>
            <span class="info-value">{{ selectionEnd }}</span>
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
                <el-option label="前倒" value="forward_fall" />
                <el-option label="后倒" value="backward_fall" />
                <el-option label="侧倒" value="side_fall" />
                <el-option label="坐下" value="sit_down" />
                <el-option label="蹲下" value="squat" />
                <el-option label="正常行走" value="normal_walk" />
                <el-option label="其他" value="other" />
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
            点击图表选择数据范围
          </el-tag>
        </div>
      </template>
      <div class="chart-container">
        <canvas ref="annotationChart" @mousedown="startDrag" @mousemove="onDrag" @mouseup="endDrag"></canvas>
        <div v-if="selectionBox" class="selection-overlay" :style="selectionBoxStyle"></div>
      </div>
    </el-card>

    <!-- Annotations List -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><List /></el-icon>
          <span>标注记录</span>
          <el-badge :value="annotations.length" :max="99" class="badge" />
        </div>
      </template>
      <DataTable
        :data="filteredAnnotations"
        :columns="columns"
        :actions="tableActions"
        :selectable="true"
        @action="handleAction"
        @selection-change="handleSelectionChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Chart, registerables } from 'chart.js'
import { useMotionStore } from '../stores/motion'
import DataTable from '../components/DataTable.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

Chart.register(...registerables)

const store = useMotionStore()

const annotationChart = ref(null)
let chartInstance = null

// Selection state
const isSelecting = ref(false)
const hasSelection = ref(false)
const selectionStart = ref('')
const selectionEnd = ref('')
const selectionCount = ref(0)
const selectionDuration = ref(0)
const selectionBox = ref(null)
const dragStart = ref(null)

// Filter state
const filterType = ref('')
const dateRange = ref([])

// Form
const annotationForm = ref({
  type: '',
  confidence: 80,
  notes: '',
  tags: [],
  quality: 3
})

// Selected rows for batch operations
const selectedRows = ref([])

// Annotations
const annotations = ref([])

const columns = [
  { prop: 'id', label: 'ID', width: '80' },
  { prop: 'timestamp', label: '时间', width: '180', sortable: true },
  { prop: 'type', label: '摔倒类型', width: '120' },
  { prop: 'confidence', label: '置信度', width: '100' },
  { prop: 'notes', label: '备注', width: '200', showOverflowTooltip: true },
  { prop: 'createdAt', label: '创建时间', width: '180' }
]

const tableActions = [
  { key: 'edit', label: '编辑', type: 'primary' },
  { key: 'delete', label: '删除', type: 'danger' }
]

const filteredAnnotations = computed(() => {
  if (!filterType.value) return annotations.value
  return annotations.value.filter(a => a.type === filterType.value)
})

const selectionBoxStyle = computed(() => {
  if (!selectionBox.value) return {}
  return {
    left: selectionBox.value.left + 'px',
    top: selectionBox.value.top + 'px',
    width: selectionBox.value.width + 'px',
    height: selectionBox.value.height + 'px'
  }
})

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

const startSelection = () => {
  isSelecting.value = true
  ElMessage.info('请在图表上点击并拖动选择数据范围')
}

const clearSelection = () => {
  isSelecting.value = false
  hasSelection.value = false
  selectionBox.value = null
  selectionStart.value = ''
  selectionEnd.value = ''
  selectionCount.value = 0
  selectionDuration.value = 0
}

const startDrag = (e) => {
  if (!isSelecting.value) return

  const rect = annotationChart.value.getBoundingClientRect()
  dragStart.value = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
}

const onDrag = (e) => {
  if (!dragStart.value) return

  const rect = annotationChart.value.getBoundingClientRect()
  const currentX = e.clientX - rect.left
  const currentY = e.clientY - rect.top

  selectionBox.value = {
    left: Math.min(dragStart.value.x, currentX),
    top: 0,
    width: Math.abs(currentX - dragStart.value.x),
    height: rect.height
  }
}

const endDrag = () => {
  if (!dragStart.value || !selectionBox.value) return

  // Calculate selection range
  const chartArea = annotationChart.value.getBoundingClientRect()
  const startPercent = selectionBox.value.left / chartArea.width
  const endPercent = (selectionBox.value.left + selectionBox.value.width) / chartArea.width

  const data = store.realtimeData
  const startIndex = Math.floor(startPercent * data.timestamps.length)
  const endIndex = Math.ceil(endPercent * data.timestamps.length)

  if (startIndex < endIndex && endIndex <= data.timestamps.length) {
    selectionStart.value = new Date(data.timestamps[startIndex]).toLocaleString()
    selectionEnd.value = new Date(data.timestamps[endIndex - 1]).toLocaleString()
    selectionCount.value = endIndex - startIndex
    selectionDuration.value = data.timestamps[endIndex - 1] - data.timestamps[startIndex]
    hasSelection.value = true
    isSelecting.value = false
    ElMessage.success(`已选择 ${selectionCount.value} 个数据点`)
  }

  dragStart.value = null
}

const submitAnnotation = async () => {
  if (!annotationForm.value.type) {
    ElMessage.warning('请选择摔倒类型')
    return
  }

  const annotation = {
    ...annotationForm.value,
    startTime: selectionStart.value,
    endTime: selectionEnd.value,
    dataCount: selectionCount.value,
    timestamp: new Date().toISOString()
  }

  annotations.value.push({
    id: Date.now(),
    ...annotation,
    createdAt: new Date().toISOString()
  })

  store.addAnnotation(annotation)

  ElMessage.success('标注已保存')

  // Reset form
  annotationForm.value = {
    type: '',
    confidence: 80,
    notes: '',
    tags: [],
    quality: 3
  }
  clearSelection()
}

const batchAnnotate = () => {
  ElMessageBox.confirm(`确定要批量标注 ${selectedRows.value.length} 条记录吗？`, '确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    ElMessage.success(`已批量标注 ${selectedRows.value.length} 条记录`)
  }).catch(() => {})
}

const handleAction = ({ key, row }) => {
  if (key === 'edit') {
    ElMessage.info('编辑功能开发中')
  } else if (key === 'delete') {
    ElMessageBox.confirm('确定要删除这条标注吗？', '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      annotations.value = annotations.value.filter(a => a.id !== row.id)
      ElMessage.success('已删除')
    }).catch(() => {})
  }
}

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

const loadData = () => {
  ElMessage.info('数据已刷新')
}

onMounted(() => {
  initChart()
  // Update chart periodically
  setInterval(updateChart, 100)
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
  gap: 12px;
}

.control-right {
  display: flex;
  align-items: center;
  gap: 16px;
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
