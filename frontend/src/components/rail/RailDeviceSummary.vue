<template>
  <div class="rail-device">
    <!-- 设备详情页：当前设备摘要 + 快捷操作 -->
    <template v-if="isDetail">
      <section class="rail-device__section">
        <h4 class="rail-device__heading">当前设备</h4>
        <ul class="rail-device__list">
          <li class="rail-device__row">
            <span>名称</span>
            <strong class="rail-device__ellipsis">{{ device.deviceName || '-' }}</strong>
          </li>
          <li class="rail-device__row">
            <span>标识</span>
            <strong class="rail-device__ellipsis">{{ device.deviceKey || '-' }}</strong>
          </li>
          <li class="rail-device__row">
            <span>状态</span>
            <span :class="['rail-device__status', `is-${statusKey}`]">{{ statusText }}</span>
          </li>
          <li class="rail-device__row">
            <span>最后上报</span>
            <strong>{{ lastSeenText }}</strong>
          </li>
        </ul>
      </section>

      <section class="rail-device__section">
        <h4 class="rail-device__heading">Topic</h4>
        <div class="rail-device__topic">
          <span class="rail-device__topic-label">数据上报</span>
          <code class="rail-device__topic-value">{{ device.topic || '-' }}</code>
        </div>
        <div v-if="commandTopic" class="rail-device__topic">
          <span class="rail-device__topic-label">指令下发</span>
          <code class="rail-device__topic-value">{{ commandTopic }}</code>
        </div>
        <el-button class="rail-device__copy" size="small" @click="copyTopic">
          <svg-icon name="documentation" :size="14" />
          复制上报 Topic
        </el-button>
      </section>

      <section v-if="device.metadata" class="rail-device__section">
        <h4 class="rail-device__heading">设备元数据</h4>
        <pre class="rail-device__meta">{{ metadataText }}</pre>
      </section>

      <section class="rail-device__section">
        <h4 class="rail-device__heading">快捷操作</h4>
        <div class="rail-device__actions">
          <el-button size="small" @click="focusCommand">发送指令</el-button>
          <el-button size="small" @click="goHistory">查询历史数据</el-button>
          <el-button size="small" @click="goMessages">查看实时消息</el-button>
        </div>
      </section>
    </template>

    <!-- 设备列表页：全局设备概览 + 快捷筛选 -->
    <template v-else>
      <section class="rail-device__section">
        <h4 class="rail-device__heading">设备概览</h4>
        <ul class="rail-device__list">
          <li class="rail-device__row">
            <span>设备总数</span>
            <strong>{{ ui.totalDevices }}</strong>
          </li>
          <li class="rail-device__row">
            <span>在线设备</span>
            <strong class="is-online">{{ ui.onlineDevices }}</strong>
          </li>
          <li class="rail-device__row">
            <span>离线设备</span>
            <strong>{{ offlineCount }}</strong>
          </li>
        </ul>
      </section>

      <section class="rail-device__section">
        <h4 class="rail-device__heading">快捷筛选</h4>
        <div class="rail-device__filters">
          <router-link class="rail-device__filter" :to="{ path: '/workbench/devices' }">全部设备</router-link>
          <router-link class="rail-device__filter" :to="{ path: '/workbench/devices', query: { status: 'ONLINE' } }">仅在线</router-link>
          <router-link class="rail-device__filter" :to="{ path: '/workbench/devices', query: { status: 'OFFLINE' } }">仅离线</router-link>
        </div>
      </section>

      <section class="rail-device__section">
        <h4 class="rail-device__heading">快捷操作</h4>
        <div class="rail-device__filters">
          <router-link class="rail-device__filter" :to="{ path: '/workbench/messages' }">查看实时消息</router-link>
          <router-link class="rail-device__filter" :to="{ path: '/workbench/history' }">查询历史数据</router-link>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUiStore } from '@/stores/ui'
import SvgIcon from '@/components/Icon.vue'

const router = useRouter()
const ui = useUiStore()

const isDetail = computed(() => ui.railName === 'DeviceDetail')
const device = computed(() => ui.railContext.device || {})
const offlineCount = computed(() => Math.max(0, ui.totalDevices - ui.onlineDevices))

const STATUS_MAP = { ONLINE: '在线', OFFLINE: '离线', INACTIVE: '未激活' }
const statusKey = computed(() => String(device.value.status || '').toLowerCase())
const statusText = computed(() => STATUS_MAP[device.value.status] || device.value.status || '-')

const lastSeenText = computed(() =>
  device.value.lastSeen ? new Date(device.value.lastSeen).toLocaleString('zh-CN') : '暂无'
)

const commandTopic = computed(() => {
  const topic = device.value.topic
  if (!topic) return ''
  return topic.replace('/data', '/command').replace('/heartbeat', '/command')
})

const metadataText = computed(() => {
  const raw = device.value.metadata
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})

async function copyTopic() {
  if (!device.value.topic) return
  try {
    await navigator.clipboard.writeText(device.value.topic)
    ElMessage.success('已复制上报 Topic')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

function focusCommand() {
  document.getElementById('device-command-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function goHistory() {
  router.push({ path: '/workbench/history', query: { deviceId: device.value.id } })
}

function goMessages() {
  router.push('/workbench/messages')
}
</script>

<style scoped>
.rail-device__section + .rail-device__section {
  margin-top: var(--spacing-2xl);
}

.rail-device__heading {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.rail-device__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.rail-device__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.rail-device__row strong {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

.rail-device__row strong.is-online {
  color: var(--color-success);
}

.rail-device__ellipsis {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-device__status {
  font-weight: var(--font-weight-medium);
}

.rail-device__status.is-online {
  color: var(--color-success);
}

.rail-device__status.is-offline {
  color: var(--color-danger);
}

.rail-device__status.is-inactive {
  color: var(--color-text-tertiary);
}

.rail-device__topic {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
}

.rail-device__topic + .rail-device__topic {
  margin-top: var(--spacing-sm);
}

.rail-device__topic-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.rail-device__topic-value {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--border-radius-sm);
  word-break: break-all;
}

.rail-device__copy {
  width: 100%;
  margin-top: var(--spacing-md);
}

.rail-device__meta {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  color: var(--color-text-primary);
  background: var(--color-bg);
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius-sm);
  padding: var(--spacing-md);
  overflow-x: auto;
  max-height: 220px;
}

.rail-device__actions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.rail-device__actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
}

.rail-device__filters {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.rail-device__filter {
  display: block;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  text-decoration: none;
  transition: all var(--transition-fast);
}

.rail-device__filter:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
</style>