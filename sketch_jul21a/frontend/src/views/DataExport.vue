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

      <el-form :model="exportForm" label-width="120px">
        <el-row :gutter="24">
          <!-- Time Range -->
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

          <!-- Data Type -->
          <el-col :span="12">
            <el-form-item label="数据类型">
              <el-select v-model="exportForm.dataType" placeholder="选择数据类型" style="width: 100%;">
                <el-option label="全部数据" value="all" />
                <el-option label="仅加速度" value="acceleration" />
                <el-option label="仅陀螺仪" value="gyroscope" />
                <el-option label="摔倒事件" value="fall_events" />
                <el-option label="标注数据" value="annotations" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <!-- Fall Type Filter -->
          <el-col :span="12">
            <el-form-item label="摔倒类型">
              <el-select v-model="exportForm.fallType" placeholder="选择摔倒类型" style="width: 100%;" clearable multiple>
                <el-option label="前倒" value="forward_fall" />
                <el-option label="后倒" value="backward_fall" />
                <el-option label="侧倒" value="side_fall" />
                <el-option label="坐下" value="sit_down" />
                <el-option label="蹲下" value="squat" />
                <el-option label="正常行走" value="normal_walk" />
              </el-select>
            </el-form-item>
          </el-col>

          <!-- Sampling Rate -->
          <el-col :span="12">
            <el-form-item label="采样率">
              <el-select v-model="exportForm.samplingRate" placeholder="选择采样率" style="width: 100%;">
                <el-option label="原始采样率 (50Hz)" value="original" />
                <el-option label="降采样 (25Hz)" value="25" />
                <el-option label="降采样 (10Hz)" value="10" />
                <el-option label="降采样 (5Hz)" value="5" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <!-- File Format -->
          <el-col :span="12">
            <el-form-item label="文件格式">
              <el-radio-group v-model="exportForm.format">
                <el-radio label="csv">CSV</el-radio>
                <el-radio label="json" disabled>JSON (开发中)</el-radio>
                <el-radio label="xlsx" disabled>Excel (开发中)</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <!-- Include Headers -->
          <el-col :span="12">
            <el-form-item label="选项">
              <el-checkbox v-model="exportForm.includeHeaders">包含表头</el-checkbox>
              <el-checkbox v-model="exportForm.includeTimestamp">包含时间戳</el-checkbox>
              <el-checkbox v-model="exportForm.includeMetadata">包含元数据</el-checkbox>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="24">
            <el-form-item label="列选择">
              <el-checkbox-group v-model="exportForm.columns">
                <el-checkbox label="timestamp">时间戳</el-checkbox>
                <el-checkbox label="ax">加速度 X</el-checkbox>
                <el-checkbox label="ay">加速度 Y</el-checkbox>
                <el-checkbox label="az">加速度 Z</el-checkbox>
                <el-checkbox label="gx">陀螺仪 X</el-checkbox>
                <el-checkbox label="gy">陀螺仪 Y</el-checkbox>
                <el-checkbox label="gz">陀螺仪 Z</el-checkbox>
                <el-checkbox label="fall_type">摔倒类型</el-checkbox>
                <el-checkbox label="confidence">置信度</el-checkbox>
              </el-checkbox-group>
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
            {{ previewData.length }} 条记录
          </el-tag>
        </div>
      </template>

      <el-table :data="previewData" style="width: 100%" border stripe max-height="300">
        <el-table-column v-if="exportForm.columns.includes('timestamp')" prop="timestamp" label="时间戳" width="180" />
        <el-table-column v-if="exportForm.columns.includes('ax')" prop="ax" label="加速度X" width="100" />
        <el-table-column v-if="exportForm.columns.includes('ay')" prop="ay" label="加速度Y" width="100" />
        <el-table-column v-if="exportForm.columns.includes('az')" prop="az" label="加速度Z" width="100" />
        <el-table-column v-if="exportForm.columns.includes('gx')" prop="gx" label="陀螺仪X" width="100" />
        <el-table-column v-if="exportForm.columns.includes('gy')" prop="gy" label="陀螺仪Y" width="100" />
        <el-table-column v-if="exportForm.columns.includes('gz')" prop="gz" label="陀螺仪Z" width="100" />
        <el-table-column v-if="exportForm.columns.includes('fall_type')" prop="fall_type" label="摔倒类型" width="120" />
        <el-table-column v-if="exportForm.columns.includes('confidence')" prop="confidence" label="置信度" width="100" />
      </el-table>
    </el-card>

    <!-- Export Actions -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <div class="export-actions">
        <div class="action-info">
          <el-icon :size="24" color="#409eff"><Document /></el-icon>
          <div>
            <h4>准备导出</h4>
            <p>将导出 {{ previewData.length }} 条数据记录，文件大小约 {{ estimatedSize }}</p>
          </div>
        </div>
        <div class="action-buttons">
          <el-button @click="previewExport">
            <el-icon><View /></el-icon>
            预览数据
          </el-button>
          <el-button type="primary" @click="exportData" :loading="isExporting">
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
          <span>导出历史</span>
        </div>
      </template>

      <el-table :data="exportHistory" style="width: 100%" border>
        <el-table-column prop="filename" label="文件名" />
        <el-table-column prop="date" label="导出时间" width="180" />
        <el-table-column prop="records" label="记录数" width="100" />
        <el-table-column prop="size" label="文件大小" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="downloadHistory(row)">
              <el-icon><Download /></el-icon>
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useMotionStore } from '../stores/motion'
import { ElMessage, ElMessageBox } from 'element-plus'

const store = useMotionStore()

const isExporting = ref(false)

const exportForm = ref({
  dateRange: [],
  dataType: 'all',
  fallType: [],
  samplingRate: 'original',
  format: 'csv',
  includeHeaders: true,
  includeTimestamp: true,
  includeMetadata: false,
  columns: ['timestamp', 'ax', 'ay', 'az', 'gx', 'gy', 'gz']
})

const exportHistory = ref([
  {
    id: 1,
    filename: 'fall_data_20231015_143022.csv',
    date: '2023-10-15 14:30:22',
    records: 1250,
    size: '45 KB'
  },
  {
    id: 2,
    filename: 'fall_events_20231014.csv',
    date: '2023-10-14 10:15:00',
    records: 56,
    size: '2.3 KB'
  }
])

const previewData = computed(() => {
  const data = store.realtimeData
  const maxPreview = 10
  const start = Math.max(0, data.timestamps.length - maxPreview)

  return data.timestamps.slice(start).map((timestamp, i) => ({
    timestamp: new Date(timestamp).toLocaleString(),
    ax: data.ax[start + i]?.toFixed(2) || '0.00',
    ay: data.ay[start + i]?.toFixed(2) || '0.00',
    az: data.az[start + i]?.toFixed(2) || '0.00',
    gx: data.gx[start + i]?.toFixed(2) || '0.00',
    gy: data.gy[start + i]?.toFixed(2) || '0.00',
    gz: data.gz[start + i]?.toFixed(2) || '0.00',
    fall_type: '-',
    confidence: '-'
  }))
})

const estimatedSize = computed(() => {
  const recordCount = store.realtimeData.timestamps.length
  const columnCount = exportForm.value.columns.length
  const avgRowSize = 50 // bytes per row
  const totalBytes = recordCount * columnCount * avgRowSize

  if (totalBytes < 1024) return `${totalBytes} B`
  if (totalBytes < 1024 * 1024) return `${(totalBytes / 1024).toFixed(1)} KB`
  return `${(totalBytes / (1024 * 1024)).toFixed(1)} MB`
})

const previewExport = () => {
  ElMessage.info('数据预览已更新')
}

const exportData = () => {
  if (store.realtimeData.timestamps.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }

  ElMessageBox.confirm('确定要导出数据吗？', '确认导出', {
    confirmButtonText: '确定导出',
    cancelButtonText: '取消',
    type: 'info'
  }).then(() => {
    isExporting.value = true

    // Simulate export
    setTimeout(() => {
      // Generate CSV
      const data = store.realtimeData
      let csv = ''

      if (exportForm.value.includeHeaders) {
        const headers = exportForm.value.columns.map(col => {
          const headerMap = {
            timestamp: '时间戳',
            ax: '加速度X',
            ay: '加速度Y',
            az: '加速度Z',
            gx: '陀螺仪X',
            gy: '陀螺仪Y',
            gz: '陀螺仪Z',
            fall_type: '摔倒类型',
            confidence: '置信度'
          }
          return headerMap[col] || col
        })
        csv += headers.join(',') + '\n'
      }

      for (let i = 0; i < data.timestamps.length; i++) {
        const row = exportForm.value.columns.map(col => {
          if (col === 'timestamp') {
            return exportForm.value.includeTimestamp
              ? new Date(data.timestamps[i]).toISOString()
              : ''
          }
          return data[col]?.[i]?.toFixed(4) || ''
        })
        csv += row.join(',') + '\n'
      }

      // Download
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `fall_data_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.csv`
      link.click()
      URL.revokeObjectURL(link.href)

      // Add to history
      exportHistory.value.unshift({
        id: Date.now(),
        filename: link.download,
        date: new Date().toLocaleString(),
        records: data.timestamps.length,
        size: estimatedSize.value
      })

      isExporting.value = false
      ElMessage.success('数据导出成功')
    }, 1500)
  }).catch(() => {})
}

const downloadHistory = (item) => {
  ElMessage.info(`重新下载 ${item.filename}`)
}
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
</style>
