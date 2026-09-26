<template>
  <div class="data-export">
    <h1 class="page-title">数据导出</h1>

    <!-- Export Configuration -->
    <el-card class="config-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><Setting /></el-icon>
          <span>导出配置</span>
        </div>
      </template>

      <el-form :model="exportForm" label-width="110px">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="设备ID">
              <el-input v-model="exportForm.deviceId" placeholder="留空则导出全部设备" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="exportForm.dateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="数据范围">
              <el-checkbox v-model="exportForm.fallsOnly">仅导出摔倒记录</el-checkbox>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文件格式">
              <el-radio-group v-model="exportForm.format" disabled>
                <el-radio label="csv">CSV</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- Export Preview -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><View /></el-icon>
          <span>数据预览</span>
          <el-tag type="info" size="small" style="margin-left: 12px;">
            匹配 {{ previewTotal }} 条，展示前 {{ previewRows.length }} 条
          </el-tag>
          <el-button type="primary" link class="ml-auto" :loading="isPreviewing" @click="loadPreview">
            <el-icon><Refresh /></el-icon>
            刷新预览
          </el-button>
        </div>
      </template>

      <el-table :data="previewRows" v-loading="isPreviewing" style="width: 100%" border stripe max-height="320">
        <el-table-column label="时间戳" width="180">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
        <el-table-column prop="device_id" label="设备" width="130" />
        <el-table-column prop="ax" label="加速度X" width="100" />
        <el-table-column prop="ay" label="加速度Y" width="100" />
        <el-table-column prop="az" label="加速度Z" width="100" />
        <el-table-column prop="gx" label="陀螺仪X" width="100" />
        <el-table-column prop="gy" label="陀螺仪Y" width="100" />
        <el-table-column prop="gz" label="陀螺仪Z" width="100" />
        <el-table-column label="是否摔倒" width="100">
          <template #default="{ row }">
            <el-tag :type="row.is_fall ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.is_fall ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fall_type" label="摔倒类型" width="120" />
      </el-table>
    </el-card>

    <!-- Export Actions -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <div class="export-actions">
        <div class="action-info">
          <el-icon :size="24" color="#409eff"><Document /></el-icon>
          <div>
            <h4>准备导出</h4>
            <p>将导出 {{ previewTotal }} 条数据记录（CSV 格式）</p>
          </div>
        </div>
        <div class="action-buttons">
          <el-button type="primary" @click="handleExport" :loading="isExporting">
            <el-icon><Download /></el-icon>
            {{ isExporting ? '导出中...' : '导出数据' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Export History -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <el-icon><Clock /></el-icon>
          <span>本次会话导出记录</span>
        </div>
      </template>

      <el-table :data="exportHistory" style="width: 100%" border>
        <el-table-column prop="filename" label="文件名" />
        <el-table-column prop="date" label="导出时间" width="180" />
        <el-table-column prop="size" label="文件大小" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="downloadHistory(row)">
              <el-icon><Download /></el-icon>
              下载
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="empty-hint">本次会话尚未导出任何文件</span>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useMotionStore } from '../stores/motion'
import { dataApi } from '../services/api'
import { wsService, DEFAULT_DEVICE_ID } from '../services/websocket'
import { ElMessage } from 'element-plus'

const store = useMotionStore()

const isPreviewing = ref(false)
const isExporting = ref(false)
const previewRows = ref([])
const previewTotal = ref(0)
const exportHistory = ref([])

const exportForm = ref({
  deviceId: '',
  dateRange: [],
  fallsOnly: false,
  format: 'csv'
})

function formatTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** Translate the form state into the query params the backend understands. */
function buildQueryParams() {
  const params = {}
  const deviceId = exportForm.value.deviceId.trim()
  if (deviceId) params.device_id = deviceId

  if (exportForm.value.dateRange?.length === 2) {
    params.start_time = new Date(exportForm.value.dateRange[0]).toISOString()
    params.end_time = new Date(exportForm.value.dateRange[1]).toISOString()
  }
  return params
}

const loadPreview = async () => {
  isPreviewing.value = true
  try {
    const params = { ...buildQueryParams(), limit: 20 }
    if (exportForm.value.fallsOnly) params.is_fall = true

    const response = await dataApi.getData(params)
    previewRows.value = response.data || []
    previewTotal.value = response.total ?? 0
  } catch (error) {
    ElMessage.error('加载预览数据失败')
    console.error(error)
    previewRows.value = []
    previewTotal.value = 0
  } finally {
    isPreviewing.value = false
  }
}

const handleExport = async () => {
  isExporting.value = true
  try {
    const params = buildQueryParams()
    if (exportForm.value.fallsOnly) params.include_falls_only = true

    const { blob, filename } = await dataApi.exportData(params)
    const resolvedName = filename || `motion_data_${timestampSlug()}.csv`

    triggerDownload(blob, resolvedName)
    exportHistory.value.unshift({
      id: Date.now(),
      filename: resolvedName,
      date: new Date().toLocaleString(),
      size: formatSize(blob.size),
      blob
    })
    ElMessage.success('数据导出成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.detail || '数据导出失败')
    console.error(error)
  } finally {
    isExporting.value = false
  }
}

const triggerDownload = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Revoke on the next tick so the click has already started the download.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

const downloadHistory = (item) => {
  triggerDownload(item.blob, item.filename)
}

function timestampSlug() {
  return new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
}

onMounted(() => {
  exportForm.value.deviceId = store.deviceInfo.deviceId || wsService.deviceId || DEFAULT_DEVICE_ID
  loadPreview()
})
</script>

<style scoped>
.data-export {
  padding: 0;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 24px;
}

.config-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.ml-auto {
  margin-left: auto;
}

.export-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.action-info h4 {
  margin: 0 0 4px 0;
  font-size: 16px;
  color: #1a1a1a;
}

.action-info p {
  margin: 0;
  font-size: 13px;
  color: #666;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.empty-hint {
  color: #909399;
  font-size: 13px;
}
</style>