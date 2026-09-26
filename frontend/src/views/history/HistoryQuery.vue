<template>
  <div class="history-query-page page-container">
    <div class="page-header">
      <h2>历史数据查询</h2>
    </div>

    <div class="query-card card">
      <h3 class="card-title">查询条件</h3>
      <form @submit.prevent="handleQuery" class="query-form">
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">设备</label>
            <select v-model="queryForm.deviceId" class="form-input">
              <option value="">全部设备</option>
              <option
                v-for="device in deviceOptions"
                :key="device.id"
                :value="device.id"
              >
                {{ device.deviceName }} ({{ device.deviceKey }})
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Topic</label>
            <input
              v-model="queryForm.topic"
              placeholder="Topic关键词（选填）"
              class="form-input"
            />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">开始时间</label>
            <input
              v-model="queryForm.startTime"
              type="datetime-local"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label class="form-label">结束时间</label>
            <input
              v-model="queryForm.endTime"
              type="datetime-local"
              class="form-input"
            />
          </div>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn-primary" :disabled="queryLoading">
            {{ queryLoading ? '查询中...' : '查询' }}
          </button>
          <button type="button" class="btn-secondary" @click="handleReset">
            重置
          </button>
        </div>
      </form>
    </div>

    <div v-if="hasSearched" class="result-section">
      <div class="result-header">
        <h3 class="card-title">查询结果</h3>
        <span class="result-count">共 {{ totalRecords }} 条记录</span>
      </div>

      <div v-if="queryLoading" class="loading-overlay">
        查询中...
      </div>

      <div v-else-if="historyRecords.length === 0" class="empty-state">
        <svg-icon name="history" :size="48" color="#E0E0E0" />
        <p class="empty-state-text">暂无数据</p>
      </div>

      <div v-else class="history-table-wrapper card">
        <table class="history-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>设备</th>
              <th>Topic</th>
              <th>数据</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in historyRecords" :key="record.id">
              <td class="time-cell">{{ formatTime(record.timestamp) }}</td>
              <td class="device-cell">{{ record.deviceName || record.deviceId }}</td>
              <td class="topic-cell">{{ record.topic }}</td>
              <td class="data-cell">
                <pre class="data-payload">{{ formatPayload(record.payload) }}</pre>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="totalPages > 1" class="pagination">
        <button
          class="btn-page"
          :disabled="currentPage === 1"
          @click="goToPage(currentPage - 1)"
        >
          上一页
        </button>
        <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button
          class="btn-page"
          :disabled="currentPage === totalPages"
          @click="goToPage(currentPage + 1)"
        >
          下一页
        </button>
        <div class="page-size-selector">
          <label>每页</label>
          <select v-model="pageSize" class="form-input page-size-select" @change="handleQuery">
            <option value="10">10条</option>
            <option value="20">20条</option>
            <option value="50">50条</option>
          </select>
          <label>条</label>
        </div>
      </div>
    </div>

    <div v-else class="initial-state empty-state">
      <svg-icon name="history" :size="48" color="#E0E0E0" />
      <p class="empty-state-text">选择查询条件后点击"查询"</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useDeviceStore } from '@/stores/device'
import api from '@/api/axios'
import SvgIcon from '@/components/Icon.vue'
import { ElMessage } from 'element-plus'

const deviceStore = useDeviceStore()

const deviceOptions = computed(() => deviceStore.devices)

const queryForm = reactive({
  deviceId: '',
  topic: '',
  startTime: '',
  endTime: ''
})

const hasSearched = ref(false)
const queryLoading = ref(false)
const historyRecords = ref([])
const totalRecords = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const totalPages = computed(() => Math.ceil(totalRecords.value / pageSize.value))

onMounted(async () => {
  await deviceStore.fetchDevices({ pageNum: 1, pageSize: 100 })
})

async function handleQuery() {
  queryLoading.value = true
  hasSearched.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }

    if (queryForm.deviceId) params.deviceId = queryForm.deviceId
    if (queryForm.topic) params.topic = queryForm.topic
    if (queryForm.startTime) params.startTime = queryForm.startTime
    if (queryForm.endTime) params.endTime = queryForm.endTime

    const response = await api.get('/history', { params })
    historyRecords.value = response.data?.records || []
    totalRecords.value = response.data?.total || 0
  } catch (error) {
    ElMessage.error('查询失败')
  } finally {
    queryLoading.value = false
  }
}

function handleReset() {
  queryForm.deviceId = ''
  queryForm.topic = ''
  queryForm.startTime = ''
  queryForm.endTime = ''
  hasSearched.value = false
  historyRecords.value = []
  totalRecords.value = 0
  currentPage.value = 1
}

function goToPage(page) {
  currentPage.value = page
  handleQuery()
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN')
}

function formatPayload(payload) {
  if (!payload) return ''
  try {
    const obj = JSON.parse(payload)
    return JSON.stringify(obj, null, 2)
  } catch {
    return payload
  }
}
</script>

<style scoped>
.query-card {
  margin-bottom: var(--spacing-lg);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-gray-dark);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color-light);
}

.query-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
}

.form-actions {
  display: flex;
  gap: var(--spacing-sm);
  justify-content: flex-end;
}

.result-section {
  margin-top: var(--spacing-lg);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.result-count {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}

.history-table-wrapper {
  overflow-x: auto;
  padding: 0;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
}

.history-table th {
  background: var(--color-gray-light);
  padding: 12px 16px;
  text-align: left;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-gray-dark);
  border-bottom: 1px solid var(--color-border-color);
}

.history-table td {
  padding: 12px 16px;
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
  border-bottom: 1px solid var(--border-color-light);
}

.history-table tr:hover td {
  background: var(--color-gray-light);
}

.time-cell {
  white-space: nowrap;
  color: var(--color-gray-text);
}

.device-cell {
  white-space: nowrap;
}

.topic-cell {
  font-family: 'Courier New', monospace;
  font-size: var(--font-size-xs);
}

.data-cell {
  max-width: 400px;
}

.data-payload {
  font-size: var(--font-size-xs);
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow-y: auto;
  margin: 0;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-lg);
  padding: var(--spacing-md);
}

.btn-page {
  padding: 6px 16px;
  background: var(--color-white);
  color: var(--color-gray-dark);
  border: 1px solid var(--color-border-color);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.btn-page:hover:not(:disabled) {
  background: var(--color-gray-light);
}

.btn-page:disabled {
  color: var(--color-gray-text);
  cursor: not-allowed;
}

.page-info {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}

.page-size-selector {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}

.page-size-select {
  width: 70px;
  padding: 4px 8px;
}

.initial-state {
  margin-top: var(--spacing-xl);
}
</style>
