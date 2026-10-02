<template>
  <div class="message-monitor-page page">
    <PageHeader title="实时消息" desc="订阅设备上报消息并下发控制指令">
      <template #actions>
        <span class="online-status">
          <span class="online-status__dot" />
          在线设备：{{ ui.onlineDevices }}
        </span>
        <el-button :loading="refreshingOnline" @click="refreshOnlineCount">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div class="message-stream-section card">
      <div class="section-header">
        <h3>实时消息流</h3>
        <div class="stream-controls">
          <span class="message-count">
            共 {{ filteredMessages.length }} 条消息<template v-if="activeFilter">（已按 Topic 过滤）</template>
          </span>
          <el-button link type="primary" @click="clearMessages">清空</el-button>
        </div>
      </div>

      <div :class="['connection-status', connectionState]">
        <span class="status-dot" />
        <span>{{ connectionStatusText }}</span>
      </div>

      <div ref="messageContainer" class="message-list">
        <div v-if="filteredMessages.length === 0" class="empty-state-small">
          {{ activeFilter ? '没有匹配该 Topic 的消息' : '等待消息...' }}
        </div>

        <div
          v-for="msg in filteredMessages"
          :key="msg.id"
          :class="['message-item', msg.direction.toLowerCase()]"
        >
          <div class="message-header">
            <span :class="['direction-badge', msg.direction.toLowerCase()]">
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
      <el-form label-position="top" class="publish-form" @submit.prevent>
        <div class="publish-form__row">
          <el-form-item label="Topic">
            <el-input v-model="publishForm.topic" placeholder="device/sensor-001/command" />
          </el-form-item>
          <el-form-item label="QoS">
            <el-select v-model="publishForm.qos">
              <el-option :value="0" label="QoS 0 - 最多一次" />
              <el-option :value="1" label="QoS 1 - 至少一次" />
              <el-option :value="2" label="QoS 2 - 恰好一次" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="Payload">
          <el-input
            v-model="publishForm.payload"
            type="textarea"
            :rows="4"
            :placeholder="PAYLOAD_PLACEHOLDER"
          />
        </el-form-item>
        <div class="publish-form__actions">
          <el-button type="primary" :loading="publishLoading" @click="handlePublish">
            发布消息
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import api from '@/api/axios'
import { useUiStore } from '@/stores/ui'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'

const ui = useUiStore()

// 连接状态四态：connecting / open / reconnecting / closed
const CONNECTION_STATUS_TEXT = {
  connecting: '实时数据流连接中...',
  open: '实时数据流已连接',
  reconnecting: '实时数据流重连中...',
  closed: '实时数据流已断开'
}
const connectionState = computed(() => ui.realtimeStatus)
const connectionStatusText = computed(
  () => CONNECTION_STATUS_TEXT[ui.realtimeStatus] || '实时数据流未连接'
)

// 本页仅展示当前会话内本地发布的消息；接收侧统一消费应用级事件流（ui.events）
const published = ref([])
const messageContainer = ref(null)
const refreshingOnline = ref(false)

const PAYLOAD_PLACEHOLDER = '{"action": "restart"}'

const publishForm = ref({
  topic: '',
  qos: 0,
  payload: ''
})
const publishLoading = ref(false)

const activeFilter = computed(() => ui.messageFilter.trim())

const messages = computed(() => {
  const incoming = ui.events.map((event) => ({
    id: `sse-${event.id}`,
    topic: event.topic,
    payload: event.payload,
    deviceKey: event.deviceKey,
    direction: 'SUBSCRIBE',
    qos: 0,
    receivedAt: event.ts || new Date().toISOString()
  }))
  return [...incoming, ...published.value].sort(
    (a, b) => new Date(a.receivedAt) - new Date(b.receivedAt)
  )
})

const filteredMessages = computed(() => {
  const keyword = activeFilter.value.toLowerCase()
  if (!keyword) return messages.value
  return messages.value.filter((msg) => String(msg.topic || '').toLowerCase().includes(keyword))
})

watch(
  () => filteredMessages.value.length,
  () => scrollToBottom()
)

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
    await api.post('/messages/publish', {
      topic: publishForm.value.topic,
      payload: publishForm.value.payload,
      qos: parseInt(publishForm.value.qos)
    })

    published.value.push({
      id: `local-${Date.now()}`,
      topic: publishForm.value.topic,
      payload: publishForm.value.payload,
      direction: 'PUBLISH',
      qos: parseInt(publishForm.value.qos),
      receivedAt: new Date().toISOString()
    })

    ElMessage.success('消息发布成功')
    publishForm.value.payload = ''
  } catch {
    // 发布失败的具体原因已由 axios 拦截器统一提示，此处只需结束 loading
  } finally {
    publishLoading.value = false
  }
}

function clearMessages() {
  published.value = []
  ui.clearEvents()
}

async function refreshOnlineCount() {
  refreshingOnline.value = true
  try {
    await ui.refreshHealth()
  } finally {
    refreshingOnline.value = false
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
</script>

<style scoped>
.online-status {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.online-status__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
}

.message-stream-section {
  margin-bottom: var(--grid-gutter);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.section-header h3 {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.stream-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.message-count {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.connection-status {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  margin-bottom: var(--spacing-md);
}

.connection-status.connecting,
.connection-status.reconnecting {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.connection-status.open {
  background: var(--color-success-light);
  color: var(--color-success);
}

.connection-status.closed {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

/* connecting / reconnecting 状态下让圆点呼吸，提示正在建立连接 */
.connection-status.connecting .status-dot,
.connection-status.reconnecting .status-dot {
  animation: status-pulse 1.2s ease-in-out infinite;
}

@keyframes status-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
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
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
}

.message-item {
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  background: var(--color-white);
  border-radius: var(--border-radius-sm);
  border-left: 3px solid var(--color-border);
  transition: border-color var(--transition-fast);
}

.message-item.publish {
  border-left-color: var(--color-primary);
}

.message-item.subscribe {
  border-left-color: var(--color-text-tertiary);
}

.message-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.direction-badge {
  display: inline-flex;
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}

.direction-badge.publish {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.direction-badge.subscribe {
  background: var(--color-bg);
  color: var(--color-text-regular);
}

.message-topic {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  font-family: var(--font-family-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.message-body {
  padding: var(--spacing-sm) 0;
}

.message-body pre {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-family-mono);
  line-height: 1.5;
}

.message-footer {
  display: flex;
  justify-content: flex-end;
}

.message-qos {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.publish-section {
  padding: var(--spacing-lg);
}

.section-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.publish-form__row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--spacing-md);
}

.publish-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.publish-form__actions {
  display: flex;
  justify-content: flex-end;
}

.empty-state-small {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>