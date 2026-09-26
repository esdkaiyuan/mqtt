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
        <span>{{ mqttConnected ? '已连接' : '未连接' }}</span>
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
import mqtt from 'mqtt'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/axios'
import SvgIcon from '@/components/Icon.vue'
import { ElMessage } from 'element-plus'

const authStore = useAuthStore()

const mqttConnected = ref(false)
let mqttClient = null

const messages = ref([])
const messageContainer = ref(null)
const onlineCount = ref(0)

const publishForm = ref({
  topic: '',
  qos: 0,
  payload: ''
})
const publishLoading = ref(false)

// MQTT配置：优先使用环境变量，回退到开发环境默认值
const MQTT_URL = import.meta.env.VITE_MQTT_URL || 'ws://localhost:8083/mqtt'
const MQTT_USERNAME = import.meta.env.VITE_MQTT_USERNAME || 'admin'
const MQTT_PASSWORD = import.meta.env.VITE_MQTT_PASSWORD || 'public'
const MQTT_OPTIONS = {
  clientId: 'frontend_' + Date.now(),
  username: MQTT_USERNAME,
  password: MQTT_PASSWORD,
  clean: true,
  connectTimeout: 4000,
  reconnectPeriod: 5000
}

function initMqtt() {
  try {
    mqttClient = mqtt.connect(MQTT_URL, MQTT_OPTIONS)

    mqttClient.on('connect', () => {
      mqttConnected.value = true
      console.log('MQTT连接成功')

      mqttClient.subscribe('device/+/data', { qos: 0 }, (err) => {
        if (err) {
          console.error('订阅失败:', err)
        } else {
          console.log('订阅成功: device/+/data')
        }
      })

      mqttClient.subscribe('device/+/status', { qos: 0 })
    })

    mqttClient.on('message', (topic, message) => {
      const payload = message.toString()
      console.log('收到消息:', topic, payload)

      const deviceKey = topic.split('/')[1]

      messages.value.push({
        id: Date.now() + Math.random(),
        topic,
        payload,
        direction: 'SUBSCRIBE',
        qos: 0,
        receivedAt: new Date().toISOString()
      })

      if (messages.value.length > 200) {
        messages.value.shift()
      }

      scrollToBottom()
    })

    mqttClient.on('error', (err) => {
      console.error('MQTT错误:', err)
      mqttConnected.value = false
    })

    mqttClient.on('offline', () => {
      mqttConnected.value = false
    })

    mqttClient.on('reconnect', () => {
      console.log('MQTT重连中...')
      mqttConnected.value = false
    })

  } catch (error) {
    console.error('MQTT连接失败:', error)
    mqttConnected.value = false
  }
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
  if (mqttClient) {
    mqttClient.end()
    mqttClient = null
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
