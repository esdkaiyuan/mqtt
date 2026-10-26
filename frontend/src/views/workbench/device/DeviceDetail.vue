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
          <el-tag
            v-if="isSharedDevice"
            size="small"
            :type="canOperateDevice ? 'success' : 'warning'"
            effect="plain"
          >
            {{ canOperateDevice ? '共享 · 可操作' : '共享 · 只读' }}
          </el-tag>
        </div>
      </template>
      <template #actions>
        <template v-if="!isSharedDevice">
          <!--
            点位表入口（T-31 P2）。
            不做「仅 Modbus 设备可见」的条件渲染：设备详情接口当前不返回产品的 protocolType，
            接入方法要么改动既有出参（影响前端其他消费方），要么额外发一次产品查询。
            点位表本身对 MQTT 直连设备是空表（无点位即无内容），展示成本低于引入判断逻辑。
          -->
          <el-button @click="goDebug">
            <svg-icon name="settings" :size="14" />
            调试
          </el-button>
          <el-button @click="goHmi">
            <svg-icon name="chart" :size="14" />
            上位机
          </el-button>
          <el-button @click="goPoints">
            <svg-icon name="list" :size="14" />
            点位表
          </el-button>
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
      </template>
    </PageHeader>

    <div v-if="parent && !isSharedDevice" class="parent-gateway-bar">
      <span class="parent-gateway-bar__label">所属上级</span>
      <span class="parent-gateway-bar__name">{{ parent.deviceName }}</span>
      <span class="parent-gateway-bar__key">{{ parent.deviceKey }}</span>
      <el-button link type="primary" size="small" @click="goParentGateway">查看上级</el-button>
    </div>

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
            <span class="info-label">固件版本</span>
            <span class="info-value">{{ device.firmwareVersion || '未上报' }}</span>
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

      <div v-if="canHaveChildren && !isSharedDevice" id="device-sub-device-section" class="info-card card">
        <SubDevicePanel
          :sub-devices="subDevices"
          :loading="subDevicesLoading"
          :attaching="attaching"
          :detaching="detaching"
          :can-write="canWrite"
          @attach="openAttachDialog"
          @detach="handleDetach"
          @view="goSubDevice"
        />
      </div>

      <div v-if="!isSharedDevice" id="device-credential-section" class="info-card card">
        <DeviceCredentialPanel
          :auth-mode="authMode"
          :auth-mode-loading="authModeLoading"
          :issuing="issuing"
          :revoking="revoking"
          :token-issuing="tokenIssuing"
          :revoked="revoked"
          :can-write="canWrite"
          @issue-certificate="handleIssueCertificate"
          @revoke-certificate="handleRevokeCertificate"
          @issue-token="handleIssueToken"
        />
      </div>

      <div id="device-property-section" class="info-card card">
        <DevicePropertyPanel :properties="properties" :loading="propertiesLoading" />
      </div>

      <div id="device-property-trend-section" class="info-card card">
        <DevicePropertyTrend
          :device-id="device.id"
          :device-key="device.deviceKey || ''"
          :device-name="device.deviceName"
          :product-id="device.productId"
          :can-export="!isSharedDevice"
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
          :can-operate="canOperateDevice"
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
          :can-operate="canOperateDevice"
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
          :device-key="device.deviceKey || ''"
          :can-export="!isSharedDevice"
          @page-change="handleCommandPageChange"
        />
      </div>
    </div>

    <EditDeviceDialog
      v-model:visible="showEdit"
      :device="device"
      :parent-gateway="parent"
      :loading="editLoading"
      @submit="handleEditSubmit"
      @view-gateway="goParentGateway"
    />

    <el-dialog v-model="attachVisible" title="挂载既有设备" width="440px">
      <p class="attach-hint">仅列出本产品下的普通设备；挂载后该设备经本设备代连（支持多级子网关）</p>
      <el-select
        v-model="attachTargetId"
        placeholder="选择要挂载的设备"
        clearable
        filterable
        class="attach-select"
      >
        <el-option
          v-for="item in candidates"
          :key="item.id"
          :label="`${item.deviceName}（${item.deviceKey}）`"
          :value="item.id"
        />
      </el-select>
      <p v-if="!candidatesLoading && candidates.length === 0" class="attach-empty">
        当前产品下没有可挂载的普通设备
      </p>
      <template #footer>
        <el-button @click="attachVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="attaching"
          :disabled="!attachTargetId"
          @click="handleAttachConfirm"
        >
          挂载
        </el-button>
      </template>
    </el-dialog>

    <CertificateDialog v-model:visible="showCertificate" :certificate="certificate || {}" />
    <TokenDialog v-model:visible="showToken" :token="token || {}" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EditDeviceDialog from '@/components/device/EditDeviceDialog.vue'
import CertificateDialog from '@/components/device/CertificateDialog.vue'
import TokenDialog from '@/components/device/TokenDialog.vue'
import DeviceCredentialPanel from '@/components/device/DeviceCredentialPanel.vue'
import DevicePropertyPanel from '@/components/device/DevicePropertyPanel.vue'
import DevicePropertyTrend from '@/components/device/DevicePropertyTrend.vue'
import DeviceEventPanel from '@/components/device/DeviceEventPanel.vue'
import DeviceControlPanel from '@/components/device/DeviceControlPanel.vue'
import DeviceShadowPanel from '@/components/device/DeviceShadowPanel.vue'
import DeviceCommandHistory from '@/components/device/DeviceCommandHistory.vue'
import SubDevicePanel from '@/components/device/SubDevicePanel.vue'
import { useDeviceData } from '@/composables/useDeviceData'
import { useDeviceCommand } from '@/composables/useDeviceCommand'
import { useDeviceShadow } from '@/composables/useDeviceShadow'
import { useDeviceCredentials } from '@/composables/useDeviceCredentials'
import { useDeviceTopology } from '@/composables/useDeviceTopology'

const route = useRoute()
const router = useRouter()
const deviceStore = useDeviceStore()
const ui = useUiStore()
const authStore = useAuthStore()

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

// 证书与令牌（T-25）：认证方式取决于所属产品，产品列表加载后回填
const showCertificate = ref(false)
const showToken = ref(false)
const credentialDeviceId = computed(() => device.value.id || null)
const credentialProductId = computed(() => device.value.productId || null)
const {
  authMode,
  authModeLoading,
  issuing,
  revoking,
  tokenIssuing,
  certificate,
  token,
  revoked,
  loadAuthMode,
  issueCertificate,
  revokeCertificate,
  issueToken
} = useDeviceCredentials(credentialDeviceId, credentialProductId)

// 签发与吊销为管理员动作，前端置灰仅作体验层提示，后端仍独立鉴权
const canWrite = computed(() => authStore.hasRole(['ADMIN']))

// T-48：共享分组成员按 accessLevel 收敛页面操作；命令 / 影子写值还需平台 OPERATOR 角色
const isSharedDevice = computed(() => ['VIEW', 'OPERATE'].includes(device.value?.accessLevel))
const canOperateDevice = computed(() =>
  ['OWNER', 'OPERATE'].includes(device.value?.accessLevel || 'OWNER') &&
  authStore.hasRole(['ADMIN', 'OPERATOR'])
)

// 设备拓扑（T-26 / T-47）：GATEWAY 与 SUB（子网关）都可以有下级
const canHaveChildren = computed(() => ['GATEWAY', 'SUB'].includes(device.value.nodeType))
const topologyDeviceId = computed(() => device.value.id || null)
const {
  subDevices,
  subDevicesLoading,
  candidates,
  candidatesLoading,
  attaching,
  detaching,
  parent,
  loadSubDevices,
  loadCandidates,
  attachSubDevice,
  detachSubDevice,
  loadParent
} = useDeviceTopology(topologyDeviceId)

const attachVisible = ref(false)
const attachTargetId = ref(null)

async function openAttachDialog() {
  attachTargetId.value = null
  attachVisible.value = true
  await loadCandidates(device.value.productId)
}

async function handleAttachConfirm() {
  if (!attachTargetId.value) return
  try {
    await attachSubDevice(attachTargetId.value)
    attachVisible.value = false
    ElMessage.success('子设备已挂载')
  } catch {
    // 失败提示由 axios 拦截器统一给出（含 6255~6260 业务错误）
  }
}

async function handleDetach(subDevice) {
  try {
    await ElMessageBox.confirm(
      `确定将「${subDevice.deviceName}」从本设备解绑吗？设备与凭据保留，可重新挂载。`,
      '确认解绑',
      { confirmButtonText: '解绑', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await detachSubDevice(subDevice.id)
    ElMessage.success('已解绑')
  } catch {
    // 失败提示由 axios 拦截器统一给出
  }
}

function goSubDevice(subDevice) {
  router.push(`/workbench/devices/${subDevice.id}`)
}

function goParentGateway() {
  router.push(`/workbench/devices/${parent.value?.id}`)
}

watch(
  credentialProductId,
  (value) => {
    if (value && !isSharedDevice.value) loadAuthMode()
  },
  { immediate: true }
)

function handleEventPageChange(page) {
  eventPage.value = page
}

function handleCommandPageChange(page) {
  commandPage.value = page
}

// 右上下文栏只读消费该上下文，页面卸载时清空避免残留上一台设备
watch(device, (value) => ui.setRailContext({ device: value }), { immediate: true })

onMounted(loadDevice)

// 上级设备与其子设备之间互跳复用同一个路由组件，须按 id 变化重新加载
watch(() => route.params.id, loadDevice)

async function loadDevice() {
  device.value = await deviceStore.fetchDeviceById(route.params.id)
  subDevices.value = []
  parent.value = null
  // 共享成员不访问拓扑 / 凭据等所有者能力，避免无谓的 403
  if (isSharedDevice.value) return
  if (canHaveChildren.value) {
    await loadSubDevices()
  }
  if (device.value?.nodeType === 'SUB') {
    await loadParent()
  }
}

onUnmounted(() => ui.setRailContext({}))

function goBack() {
  router.push('/workbench/devices')
}

// 设备统一日志时间线（T-20）
function goLogs() {
  router.push(`/workbench/devices/${route.params.id}/logs`)
}

function goDebug() {
  router.push(`/workbench/devices/${route.params.id}/debug`)
}

function goHmi() {
  router.push({ path: '/workbench/hmi/new', query: { deviceId: route.params.id } })
}

// 设备点位表（T-31 P2）
function goPoints() {
  router.push(`/workbench/devices/${route.params.id}/points`)
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

// 证书与令牌（T-25）：签发结果仅在本次弹层展示，关闭后不再回显
async function handleIssueCertificate() {
  try {
    const result = await issueCertificate()
    if (!result) return
    showCertificate.value = true
  } catch {
    // 失败提示由 axios 拦截器统一给出（含 6249 认证方式不支持 / 6251 签发失败）
  }
}

async function handleRevokeCertificate() {
  try {
    await ElMessageBox.confirm(
      `确定要吊销设备「${device.value.deviceName}」的当前证书吗？吊销后该设备将立即无法接入。`,
      '确认吊销',
      { confirmButtonText: '吊销', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const result = await revokeCertificate('控制台手动吊销')
    if (!result) return
    ElMessage.success('证书已吊销')
  } catch {
    // 失败提示由 axios 拦截器统一给出（含 6252 吊销失败）
  }
}

async function handleIssueToken() {
  try {
    const result = await issueToken()
    if (!result) return
    showToken.value = true
  } catch {
    // 失败提示由 axios 拦截器统一给出（含 6249 认证方式不支持 / 6253 令牌签发失败）
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

.parent-gateway-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary);
  border-radius: var(--border-radius);
  flex-wrap: wrap;
}

.parent-gateway-bar__label {
  color: var(--color-primary);
  font-weight: 600;
}

.parent-gateway-bar__name {
  color: var(--color-text-primary);
}

.parent-gateway-bar__key {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.attach-hint {
  margin-bottom: var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.attach-select {
  width: 100%;
}

.attach-empty {
  margin-top: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>