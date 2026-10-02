<template>
  <div class="device-detail-page page">
    <PageHeader :title="device.deviceName">
      <template #title>
        <div class="detail-heading">
          <el-button link class="detail-heading__back" @click="goBack">
            <svg-icon name="device" :size="16" />
            返回
          </el-button>
          <span class="detail-heading__name">{{ device.deviceName }}</span>
        </div>
      </template>
      <template #actions>
        <el-button @click="editDevice">
          <svg-icon name="edit" :size="14" />
          编辑
        </el-button>
        <el-button type="danger" plain @click="deleteDevice">
          <svg-icon name="delete" :size="14" />
          删除
        </el-button>
      </template>
    </PageHeader>

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
          <div v-if="device.description" class="info-item info-item--full">
            <span class="info-label">描述</span>
            <span class="info-value">{{ device.description }}</span>
          </div>
        </div>
      </div>

      <div id="device-command-section" class="info-card card">
        <h3 class="card-title">发送指令</h3>
        <p class="card-desc">通过 MQTT 向设备发送控制指令，设备侧 Topic 与摘要见右侧上下文栏</p>
        <el-form label-position="top" class="command-form" @submit.prevent>
          <el-form-item label="指令 Topic">
            <el-input v-model="commandForm.topic" placeholder="自动生成，或手动输入" />
          </el-form-item>
          <el-form-item label="指令内容 (JSON)">
            <el-input
              v-model="commandForm.payload"
              type="textarea"
              :rows="4"
              :placeholder="PAYLOAD_PLACEHOLDER"
            />
          </el-form-item>
          <el-form-item label="QoS">
            <el-select v-model="commandForm.qos">
              <el-option :value="0" label="QoS 0 - 最多一次" />
              <el-option :value="1" label="QoS 1 - 至少一次" />
              <el-option :value="2" label="QoS 2 - 恰好一次" />
            </el-select>
          </el-form-item>
          <div class="command-form__actions">
            <el-button type="primary" :loading="commandLoading" @click="handleSendCommand">
              发送指令
            </el-button>
          </div>
        </el-form>
      </div>
    </div>

    <EditDeviceDialog
      v-model:visible="showEdit"
      :device="device"
      :loading="editLoading"
      @submit="handleEditSubmit"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import { useUiStore } from '@/stores/ui'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EditDeviceDialog from '@/components/device/EditDeviceDialog.vue'
import api from '@/api/axios'

const route = useRoute()
const router = useRouter()
const deviceStore = useDeviceStore()
const ui = useUiStore()

const device = ref({})
const commandLoading = ref(false)
const showEdit = ref(false)
const editLoading = ref(false)

const PAYLOAD_PLACEHOLDER = '{"action": "restart"}'

const commandForm = reactive({
  topic: '',
  payload: '',
  qos: 1
})

const commandTopic = computed(() => {
  if (!device.value.topic) return ''
  return device.value.topic.replace('/data', '/command').replace('/heartbeat', '/command')
})

// 右上下文栏只读消费该上下文，页面卸载时清空避免残留上一台设备
watch(device, (value) => ui.setRailContext({ device: value }), { immediate: true })

onMounted(async () => {
  const deviceId = route.params.id
  device.value = await deviceStore.fetchDeviceById(deviceId)
  commandForm.topic = commandTopic.value
})

onUnmounted(() => ui.setRailContext({}))

function goBack() {
  router.push('/workbench/devices')
}

function editDevice() {
  showEdit.value = true
}

async function handleEditSubmit(payload) {
  editLoading.value = true
  try {
    await deviceStore.updateDevice(payload.id, payload)
    showEdit.value = false
    device.value = await deviceStore.fetchDeviceById(route.params.id)
    commandForm.topic = commandTopic.value
  } finally {
    editLoading.value = false
  }
}

async function deleteDevice() {
  try {
    await ElMessageBox.confirm(
      `确定要删除设备「${device.value.deviceName}」吗？删除后无法恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await deviceStore.deleteDevice(device.value.id)
  router.push('/workbench/devices')
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
  } catch {
    ElMessage.error('发送指令失败')
  } finally {
    commandLoading.value = false
  }
}

function formatStatus(status) {
  const map = { ONLINE: '在线', OFFLINE: '离线', INACTIVE: '未激活' }
  return map[status] || status
}

function formatDeviceType(type) {
  const map = { sensor: '传感器', gateway: '网关', actuator: '执行器' }
  return map[type] || type
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.detail-heading {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  min-width: 0;
}

.detail-heading__back {
  flex-shrink: 0;
}

.detail-heading__name {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: var(--grid-gutter);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
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
  gap: var(--spacing-xs);
}

.info-item--full {
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
  align-self: flex-start;
  padding: 2px 10px;
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}

.device-type-badge.sensor {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.device-type-badge.gateway {
  background: var(--color-success-light);
  color: var(--color-success);
}

.device-type-badge.actuator {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.topic-text {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  background: var(--color-bg);
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
  align-self: flex-start;
}

.command-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.command-form__actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>