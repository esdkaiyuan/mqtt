<template>
  <div class="message-monitor-page page-container">
    <div class="page-header">
      <h2>实时消息监控</h2>
      <div class="header-right">
        <div class="online-status">
          <svg-icon name="online" :size="16" color="#52C41A" />
          <span>在线设备：{{ onlineCount }}</span>
        </div>
        <button class="btn-secondary" @click="refreshOnlineCount">
          <svg-icon name="refresh" :size="14" />
          刷新
        </button>
      </div>
    </div>

    <div class="message-stream-section card">
      <div class="section-header">
        <h3>实时消息流</h3>
        <div class="stream-controls">
          <span class="message-count">共 {{ messages.length }} 条消息</span>
          <button class="btn-text" @click="clearMessages">清空</button>
        </div>
      </div>

      <div :class="['connection-status', mqttConnected ? 'connected' : 'disconnected']">
        <span class="status-dot"></span>
        <span>{{ mqttConnected ? '实时数据流已连接' : '实时数据流未连接' }}</span>
      </div>

      <div ref="messageContainer" class="message-list">
        <div v-if="messages.length === 0" class="empty-state-small">
          等待消息...
        </div>

        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['message-item', msg.direction?.toLowerCase()]"
        >
          <div class="message-header">
            <span :class="['direction-badge', msg.direction?.toLowerCase()]">
              {{ msg.direction === 'PUBLISH' ? '发送' : '接收' }}
            </span>
            <span class="message-topic">{{ msg.topic }}</span>
            <span class="message-time">{{ formatTime(msg.receivedAt) }}</span>
          </div>
          <div class="message-body">
            <pre>{{ formatPayload(msg.payload) }}</pre>
          </div>
          <div class="message-footer">
            <span class="message-qos">QoS {{ msg.qos }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="publish-section card">
      <h3 class="section-title">发布消息</h3>
      <form @submit.prevent="handlePublish" class="publish-form">
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">Topic</label>
            <input
              v-model="publishForm.topic"
              placeholder="device/sensor-001/command"
              class="form-input"
              required
            />
          </div>
          <div class="form-group">
            <label class="form-label">QoS</label>
            <select v-model="publishForm.qos" class="form-input">
              <option value="0">QoS 0 - 最多一次</option>
              <option value="1">QoS 1 - 至少一次</option>
              <option value="2">QoS 2 - 恰好一次</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Payload</label>
          <textarea
            v-model="publishForm.payload"
            placeholder='{"action": "restart"}'
            class="form-input form-textarea"
            rows="4"
            required
          ></textarea>
        </div>
        <button type="submit" class="btn-primary" :disabled="publishLoading">
          {{ publishLoading ? '发布中...' : '发布消息' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { subscribeRealtime } from '@/api/realtime'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/axios'
import SvgIcon from '@/components/Icon.vue'

const authStore = useAuthStore()

const mqttConnected = ref(false)
let realtimeClient = null

const messages = ref([])
const messageContainer = ref(null)
const onlineCount = ref(0)

const publishForm = ref({
  topic: '',
  qos: 0,
  payload: ''
})
const publishLoading = ref(false)

// 实时数据由后端按当前用户权限过滤后经 SSE 推送，前端不再直连 Broker。
function initMqtt() {
  realtimeClient = subscribeRealtime({
    onOpen: () => {
      mqttConnected.value = true
    },
    onEvent: (event) => {
      messages.value.push({
        id: Date.now() + Math.random(),
        topic: event.topic,
        payload: event.payload,
        deviceKey: event.deviceKey,
        direction: 'SUBSCRIBE',
        qos: 0,
        receivedAt: event.ts || new Date().toISOString()
      })

      if (messages.value.length > 200) {
        messages.value.shift()
      }

      scrollToBottom()
    },
    onError: (error) => {
      console.error('实时通道错误:', error)
      mqttConnected.value = false
    }
  })
}

function scrollToBottom() {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    }
  })
}

async function handlePublish() {
  publishLoading.value = true
  try {
    const response = await api.post('/messages/publish', {
      topic: publishForm.value.topic,
      payload: publishForm.value.payload,
      qos: parseInt(publishForm.value.qos)
    })

    messages.value.push({
      id: Date.now(),
      topic: publishForm.value.topic,
      payload: publishForm.value.payload,
      direction: 'PUBLISH',
      qos: parseInt(publishForm.value.qos),
      receivedAt: new Date().toISOString()
    })

    ElMessage.success('消息发布成功')
    scrollToBottom()

    publishForm.value.payload = ''
  } catch (error) {
  } finally {
    publishLoading.value = false
  }
}

function clearMessages() {
  messages.value = []
}

async function refreshOnlineCount() {
  try {
    const response = await api.get('/devices/online')
    onlineCount.value = response.data?.length || 0
  } catch (error) {
    console.error('获取在线设备失败:', error)
  }
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date

  if (diff < 86400000 && date.getDate() === now.getDate()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  }
  return date.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatPayload(payload) {
  try {
    const obj = JSON.parse(payload)
    return JSON.stringify(obj, null, 2)
  } catch {
    return payload
  }
}

onMounted(() => {
  initMqtt()
  refreshOnlineCount()
})

onUnmounted(() => {
  if (realtimeClient) {
    realtimeClient.close()
    realtimeClient = null
  }
})
</script>

<style scoped>
.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.online-status {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
}

.message-stream-section {
  margin-bottom: var(--spacing-lg);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.section-header h3 {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-gray-dark);
}

.stream-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.message-count {
  font-size: var(--font-size-xs);
  color: var(--color-gray-text);
}

.btn-text {
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.connection-status {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 4px 12px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  margin-bottom: var(--spacing-md);
}

.connection-status.connected {
  background: var(--color-success-light);
  color: var(--color-success);
}

.connection-status.disconnected {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.message-list {
  max-height: 500px;
  overflow-y: auto;
  padding: var(--spacing-sm);
  background: var(--color-gray-light);
  border-radius: var(--border-radius-sm);
}

.message-item {
  padding: 12px;
  margin-bottom: var(--spacing-sm);
  background: var(--color-white);
  border-radius: var(--border-radius-sm);
  border-left: 3px solid var(--color-gray-border);
  transition: border-color 0.2s;
}

.message-item.publish {
  border-left-color: var(--color-primary);
}

.message-item.subscribe {
  border-left-color: var(--color-gray-text);
}

.message-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: 8px;
}

.direction-badge {
  display: inline-flex;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.direction-badge.publish {
  background: #E6F7FF;
  color: var(--color-primary);
}

.direction-badge.subscribe {
  background: var(--color-gray-light);
  color: var(--color-gray-text);
}

.message-topic {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
  font-family: 'Courier New', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-time {
  font-size: var(--font-size-xs);
  color: var(--color-gray-text);
  flex-shrink: 0;
}

.message-body {
  padding: 8px 0;
}

.message-body pre {
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Courier New', monospace;
  line-height: 1.5;
}

.message-footer {
  display: flex;
  justify-content: flex-end;
}

.message-qos {
  font-size: var(--font-size-xs);
  color: var(--color-gray-text);
}

.publish-section {
  padding: var(--spacing-lg);
}

.section-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-gray-dark);
  margin-bottom: var(--spacing-md);
}

.publish-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--spacing-md);
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
  font-family: 'Courier New', monospace;
}

.empty-state-small {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--color-gray-text);
  font-size: var(--font-size-sm);
}
</style>
