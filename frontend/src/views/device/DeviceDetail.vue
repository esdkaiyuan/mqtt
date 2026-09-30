<template>
  <div class="device-detail-page page-container">
    <div class="page-header">
      <div class="header-left">
        <button class="btn-back" @click="goBack">
          <svg-icon name="device" :size="16" />
          返回
        </button>
        <h2>{{ device.deviceName }}</h2>
      </div>
      <div class="header-actions">
        <button class="btn-secondary" @click="editDevice">
          <svg-icon name="edit" :size="14" />
          编辑
        </button>
        <button class="btn-danger" @click="deleteDevice">
          <svg-icon name="delete" :size="14" />
          删除
        </button>
      </div>
    </div>

    <div class="detail-layout">
      <!-- Left: Info Card -->
      <div class="detail-main">
        <div class="info-card card">
          <h3 class="card-title">基本信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">设备标识</span>
              <span class="info-value">{{ device.deviceKey }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">设备类型</span>
              <span :class="['device-type-badge', device.deviceType]">{{ formatDeviceType(device.deviceType) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">MQTT Topic</span>
              <span class="info-value topic-text">{{ device.topic }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">状态</span>
              <span :class="['status-badge', device.status?.toLowerCase()]">
                {{ formatStatus(device.status) }}
              </span>
            </div>
            <div class="info-item">
              <span class="info-label">最后上报</span>
              <span class="info-value">{{ device.lastSeen ? formatTime(device.lastSeen) : '暂无' }}</span>
            </div>
            <div class="info-item" v-if="device.description">
              <span class="info-label">描述</span>
              <span class="info-value">{{ device.description }}</span>
            </div>
          </div>
        </div>

        <!-- Command Section -->
        <div class="info-card card">
          <h3 class="card-title">发送指令</h3>
          <p class="card-desc">通过MQTT向设备发送控制指令</p>
          <form @submit.prevent="handleSendCommand" class="command-form">
            <div class="form-group">
              <label class="form-label">指令Topic</label>
              <input v-model="commandForm.topic" class="form-input" placeholder="自动生成，或手动输入" />
            </div>
            <div class="form-group">
              <label class="form-label">指令内容 (JSON)</label>
              <textarea
v-model="commandForm.payload" class="form-input form-textarea" rows="4"
                placeholder="{&quot;action&quot;: &quot;restart&quot;}"></textarea>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">QoS</label>
                <select v-model="commandForm.qos" class="form-input">
                  <option :value="0">QoS 0 - 最多一次</option>
                  <option :value="1">QoS 1 - 至少一次</option>
                  <option :value="2">QoS 2 - 恰好一次</option>
                </select>
              </div>
            </div>
            <button type="submit" class="btn-primary" :disabled="commandLoading">
              {{ commandLoading ? '发送中...' : '发送指令' }}
            </button>
          </form>
        </div>
      </div>

      <!-- Right: Status History -->
      <div class="detail-side">
        <div class="info-card card">
          <h3 class="card-title">在线状态</h3>
          <div class="status-indicator">
            <span :class="['status-dot', device.status?.toLowerCase()]"></span>
            <span class="status-text">{{ formatStatus(device.status) }}</span>
          </div>
          <div class="meta-info">
            <div class="meta-item">
              <span class="meta-label">最后上报</span>
              <span class="meta-value">{{ device.lastSeen ? formatTime(device.lastSeen) : '暂无' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">所属用户</span>
              <span class="meta-value">{{ device.ownerUsername || '当前用户' }}</span>
            </div>
          </div>
        </div>

        <div class="info-card card">
          <h3 class="card-title">Topic路径</h3>
          <div class="topic-info">
            <div class="topic-item">
              <span class="topic-label">数据上报</span>
              <code class="topic-value">{{ device.topic }}</code>
            </div>
            <div class="topic-item" v-if="commandTopic">
              <span class="topic-label">指令下发</span>
              <code class="topic-value">{{ commandTopic }}</code>
            </div>
          </div>
        </div>

        <div class="info-card card" v-if="device.metadata">
          <h3 class="card-title">设备元数据</h3>
          <pre class="metadata-json">{{ formatMetadata(device.metadata) }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import SvgIcon from '@/components/Icon.vue'
import api from '@/api/axios'

const route = useRoute()
const router = useRouter()
const deviceStore = useDeviceStore()

const device = ref({})
const statusHistory = ref([])
const commandLoading = ref(false)

const commandForm = reactive({
  topic: '',
  payload: '',
  qos: 1
})

const commandTopic = computed(() => {
  if (!device.value.topic) return ''
  return device.value.topic.replace('/data', '/command').replace('/heartbeat', '/command')
})

onMounted(async () => {
  const deviceId = route.params.id
  device.value = await deviceStore.fetchDeviceById(deviceId)
  statusHistory.value = await deviceStore.fetchDeviceStatusHistory(deviceId)
  commandForm.topic = commandTopic.value
})

function goBack() {
  router.push('/devices')
}

function editDevice() {
  router.push(`/devices/${device.value.id}/edit`)
}

async function deleteDevice() {
  await deviceStore.deleteDevice(device.value.id)
  router.push('/devices')
}

async function handleSendCommand() {
  if (!commandForm.payload) {
    ElMessage.warning('请输入指令内容')
    return
  }
  commandLoading.value = true
  try {
    await api.post('/messages/publish', {
      topic: commandForm.topic,
      payload: commandForm.payload,
      qos: commandForm.qos
    })
    ElMessage.success('指令发送成功')
    commandForm.payload = ''
  } catch (error) {
    ElMessage.error('发送指令失败')
  } finally {
    commandLoading.value = false
  }
}

function formatStatus(status) {
  const map = { 'ONLINE': '在线', 'OFFLINE': '离线', 'INACTIVE': '未激活' }
  return map[status] || status
}

function formatDeviceType(type) {
  const map = { 'sensor': '传感器', 'gateway': '网关', 'actuator': '执行器' }
  return map[type] || type
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN')
}

function formatMetadata(metadata) {
  try {
    return JSON.stringify(JSON.parse(metadata), null, 2)
  } catch {
    return metadata
  }
}
</script>

<style scoped>
.btn-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
  margin-right: var(--spacing-md);
}

.header-left {
  display: flex;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.detail-layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: var(--spacing-lg);
  align-items: start;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.detail-side {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-md);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md) var(--spacing-lg);
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.full-width {
  grid-column: 1 / -1;
}

.info-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.info-value {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  word-break: break-all;
}

.device-type-badge {
  display: inline-flex;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.device-type-badge.sensor {
  background: #E8F3FF;
  color: #165DFF;
}

.device-type-badge.gateway {
  background: #E8FFEA;
  color: #00B42A;
}

.device-type-badge.actuator {
  background: #FFF3E8;
  color: #FF7D00;
}

.topic-text {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  background: var(--color-bg);
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.status-dot.online {
  background: #00B42A;
  box-shadow: 0 0 0 3px rgba(0, 180, 42, 0.15);
}

.status-dot.offline {
  background: #F53F3F;
  box-shadow: 0 0 0 3px rgba(245, 63, 63, 0.15);
}

.status-dot.inactive {
  background: #86909C;
  box-shadow: 0 0 0 3px rgba(134, 144, 156, 0.15);
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.status-text {
  font-size: var(--font-size-md);
  font-weight: 500;
}

.meta-info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.meta-item {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-size-sm);
}

.meta-label {
  color: var(--color-text-tertiary);
}

.meta-value {
  color: var(--color-text-primary);
}

.topic-info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.topic-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.topic-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.topic-value {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 4px 8px;
  border-radius: var(--border-radius-sm);
  word-break: break-all;
}

.metadata-json {
  background: var(--color-bg);
  padding: var(--spacing-md);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  overflow-x: auto;
  margin: 0;
}

.command-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
  font-family: var(--font-family-mono);
}

@media (max-width: 1024px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
