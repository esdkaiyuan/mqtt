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
        <el-button @click="goLogs">
          <svg-icon name="history" :size="14" />
          设备日志
        </el-button>
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

      <div id="device-property-section" class="info-card card">
        <DevicePropertyPanel :properties="properties" :loading="propertiesLoading" />
      </div>

      <div id="device-property-trend-section" class="info-card card">
        <DevicePropertyTrend
          :device-id="device.id"
          :device-name="device.deviceName"
          :product-id="device.productId"
        />
      </div>

      <div id="device-event-section" class="info-card card">
        <DeviceEventPanel
          :events="events"
          :total="eventsTotal"
          :loading="eventsLoading"
          :page="eventPage"
          :size="eventSize"
          @page-change="handleEventPageChange"
        />
      </div>

      <div id="device-control-section" class="info-card card">
        <DeviceControlPanel
          :capability="capability"
          :device-key="device.deviceKey || ''"
          :loading="capabilityLoading"
          :sending="sending"
          @send="handleSendCommand"
        />
      </div>

      <div id="device-shadow-section" class="info-card card">
        <DeviceShadowPanel
          :shadow="shadow"
          :capability="capability"
          :online="device.status === 'ONLINE'"
          :loading="shadowLoading"
          :setting="setting"
          @set-desired="handleSetDesired"
        />
      </div>

      <div id="device-command-section" class="info-card card">
        <DeviceCommandHistory
          :records="records"
          :total="recordsTotal"
          :loading="recordsLoading"
          :page="commandPage"
          :size="commandSize"
          @page-change="handleCommandPageChange"
        />
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import { useUiStore } from '@/stores/ui'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EditDeviceDialog from '@/components/device/EditDeviceDialog.vue'
import DevicePropertyPanel from '@/components/device/DevicePropertyPanel.vue'
import DevicePropertyTrend from '@/components/device/DevicePropertyTrend.vue'
import DeviceEventPanel from '@/components/device/DeviceEventPanel.vue'
import DeviceControlPanel from '@/components/device/DeviceControlPanel.vue'
import DeviceShadowPanel from '@/components/device/DeviceShadowPanel.vue'
import DeviceCommandHistory from '@/components/device/DeviceCommandHistory.vue'
import { useDeviceData } from '@/composables/useDeviceData'
import { useDeviceCommand } from '@/composables/useDeviceCommand'
import { useDeviceShadow } from '@/composables/useDeviceShadow'

const route = useRoute()
const router = useRouter()
const deviceStore = useDeviceStore()
const ui = useUiStore()

const device = ref({})
const showEdit = ref(false)
const editLoading = ref(false)

// 物模型派生数据（只读）：deviceKey 待设备详情加载后才有值
const deviceKey = computed(() => device.value.deviceKey || '')
const eventPage = ref(1)
const eventSize = ref(10)
const { properties, propertiesLoading, events, eventsTotal, eventsLoading } = useDeviceData(
  deviceKey,
  eventPage,
  eventSize
)

// 命令下发与服务调用（T-15）
const commandPage = ref(1)
const commandSize = ref(10)
const { capability, capabilityLoading, records, recordsTotal, recordsLoading, sending, sendCommand } =
  useDeviceCommand(deviceKey, commandPage, commandSize)

// 设备影子（T-16）：desired / reported / delta 三份状态与期望值下发
const { shadow, shadowLoading, setting, setDesired } = useDeviceShadow(deviceKey)

function handleEventPageChange(page) {
  eventPage.value = page
}

function handleCommandPageChange(page) {
  commandPage.value = page
}

// 右上下文栏只读消费该上下文，页面卸载时清空避免残留上一台设备
watch(device, (value) => ui.setRailContext({ device: value }), { immediate: true })

onMounted(async () => {
  const deviceId = route.params.id
  device.value = await deviceStore.fetchDeviceById(deviceId)
})

onUnmounted(() => ui.setRailContext({}))

function goBack() {
  router.push('/workbench/devices')
}

// 设备统一日志时间线（T-20）
function goLogs() {
  router.push(`/workbench/devices/${route.params.id}/logs`)
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

async function handleSendCommand(payload) {
  try {
    const record = await sendCommand(payload)
    if (!record) return
    if (payload.callType === 'sync') {
      if (record.status === 'ACKED') {
        ElMessage.success('命令执行成功')
      } else {
        ElMessage.warning(`命令未成功，状态：${record.status}`)
      }
    } else {
      ElMessage.success('命令已下发')
    }
  } catch {
    // 失败提示由 axios 拦截器统一给出（含 6201~6205 业务错误）
  }
}

async function handleSetDesired({ params }) {
  try {
    const record = await setDesired(params)
    if (!record) return
    if (record.status === 'QUEUED') {
      ElMessage.warning('设备离线，期望值已保存，上线后自动补发')
    } else {
      ElMessage.success('期望值已下发')
    }
  } catch {
    // 失败提示由 axios 拦截器统一给出（含 6206 业务错误）
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

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>