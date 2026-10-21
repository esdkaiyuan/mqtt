<template>
  <div class="hmi-detail-page page">
    <PageHeader :title="detail?.name || '上位机'" :desc="pageDesc">
      <template #actions>
        <el-button @click="goList">返回列表</el-button>
        <el-button :disabled="!detail" @click="goDevice">设备详情</el-button>
        <el-button :disabled="!detail" @click="editorVisible = true">编辑</el-button>
        <el-button :loading="detailQuery.isFetching.value" @click="refreshAll">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div v-if="detailQuery.isLoading.value" class="hmi-loading card">加载上位机...</div>

    <template v-else-if="detail">
      <div class="runtime-bar card">
        <div class="runtime-bar__device">
          <span class="runtime-bar__label">绑定设备</span>
          <span class="runtime-bar__value">{{ detail.deviceName || `设备 ${detail.deviceId}` }}</span>
          <code class="runtime-bar__key">{{ detail.deviceKey }}</code>
        </div>
        <div class="runtime-bar__status">
          <span :class="['runtime-dot', ui.realtimeStatus]" />
          <span>{{ realtimeText }}</span>
          <span class="runtime-bar__divider" />
          <span>{{ detail.config?.widgets?.length || 0 }} 个控件</span>
          <span class="runtime-bar__divider" />
          <span>最近刷新 {{ lastRefreshText }}</span>
        </div>
      </div>

      <el-alert
        v-if="modelEmpty"
        type="warning"
        :closable="false"
        show-icon
        title="产品尚未定义物模型"
        description="当前上位机只有事件面板；请先到产品页定义属性与服务，再编辑上位机添加控件。"
        class="runtime-alert"
      />

      <HmiGrid
        :widgets="widgets"
        :model="detail.model"
        :values="values"
        :events="eventRecords"
        :device-id="detail.deviceId"
        :device-name="detail.deviceName"
        @update="handleUpdate"
        @command="handleCommand"
      />
    </template>

    <EmptyState v-else description="上位机不存在或加载失败">
      <el-button size="small" @click="goList">返回列表</el-button>
    </EmptyState>

    <HmiServiceDialog
      v-model="serviceDialogVisible"
      :service="activeService"
      :device-key="deviceKey"
      @submitted="handleServiceSubmitted"
    />

    <HmiEditorDrawer
      v-if="detail"
      v-model="editorVisible"
      :detail="detail"
      :saving="saving"
      @save="handleSaveEdit"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SvgIcon from '@/components/Icon.vue'
import HmiGrid from '@/components/hmi/HmiGrid.vue'
import HmiServiceDialog from '@/components/hmi/HmiServiceDialog.vue'
import HmiEditorDrawer from '@/components/hmi/HmiEditorDrawer.vue'
import { deviceApi } from '@/api/device'
import { hmiApi } from '@/api/hmi'
import { unwrapResult } from '@/utils/result'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const ui = useUiStore()

const id = computed(() => route.params.id)
const detailQuery = useQuery({
  queryKey: computed(() => ['hmi-view', id.value]),
  queryFn: async () => unwrapResult(await hmiApi.detail(id.value))
})

const detail = computed(() => detailQuery.data.value)
const deviceKey = computed(() => detail.value?.deviceKey || '')
const widgets = computed(() => detail.value?.config?.widgets || [])
const modelEmpty = computed(() => (detail.value?.model?.properties?.length || 0) === 0
  && (detail.value?.model?.services?.length || 0) === 0)
const pageDesc = computed(() => (detail.value?.deviceKey
  ? `实时监控与控制 · ${detail.value.deviceKey}`
  : '实时监控与控制'))

/* ---------- 属性值：最新值 + 影子 + SSE ---------- */

const propertiesQuery = useQuery({
  queryKey: computed(() => ['hmi-properties', deviceKey.value]),
  queryFn: async () => unwrapResult(await deviceApi.getProperties(deviceKey.value), []),
  enabled: computed(() => Boolean(deviceKey.value)),
  refetchInterval: 30000
})

const shadowQuery = useQuery({
  queryKey: computed(() => ['hmi-shadow', deviceKey.value]),
  queryFn: async () => unwrapResult(await deviceApi.getDeviceShadow(deviceKey.value)),
  enabled: computed(() => Boolean(deviceKey.value))
})

const sseValues = ref({})

function specOf(identifier) {
  return detail.value?.model?.properties?.find((item) => item.identifier === identifier) || null
}

function coerce(raw, spec) {
  if (raw === null || raw === undefined) return undefined
  const type = String(spec?.type || '').toLowerCase()
  if (['bool', 'boolean'].includes(type)) {
    return raw === true || raw === 'true' || raw === 1 || raw === '1'
  }
  if (['int', 'integer', 'long', 'float', 'double', 'number'].includes(type)) {
    const value = Number(raw)
    return Number.isFinite(value) ? value : raw
  }
  return raw
}

const latestValues = computed(() => {
  const result = {}
  for (const item of propertiesQuery.data.value || []) {
    const value = coerce(item.value, specOf(item.identifier))
    if (value !== undefined) result[item.identifier] = value
  }
  return result
})

const reportedValues = computed(() => {
  const result = {}
  const reported = shadowQuery.data.value?.reported || {}
  for (const [identifier, raw] of Object.entries(reported)) {
    const value = coerce(raw, specOf(identifier))
    if (value !== undefined) result[identifier] = value
  }
  return result
})

const values = computed(() => ({
  ...reportedValues.value,
  ...latestValues.value,
  ...sseValues.value
}))

/**
 * 事件缓冲按批到达时只取最后一条会丢掉同批内的匹配报文，
 * 因此用单调 id 游标逐条消费；缓冲区前移不影响已消费位置。
 */
let lastEventId = 0
watch(
  () => ui.events.length,
  () => {
    for (const event of ui.events) {
      if (event.id <= lastEventId) continue
      lastEventId = event.id
      if (event.deviceKey !== deviceKey.value || !event.payload) continue
      try {
        const payload = JSON.parse(event.payload)
        if (!payload || typeof payload !== 'object' || Array.isArray(payload)) continue
        const next = { ...sseValues.value }
        // 1) 属性趋势流：{ samples: [{ identifier, valueText }] }
        if (Array.isArray(payload.samples)) {
          for (const sample of payload.samples) {
            if (sample?.identifier) {
              next[sample.identifier] = coerce(sample.valueText, specOf(sample.identifier))
            }
          }
        } else {
          // 2) 原始上报：Alink 取 params，普通 TSL 直接平铺
          const source = payload.params && typeof payload.params === 'object' && !Array.isArray(payload.params)
            ? payload.params
            : payload
          for (const [identifier, raw] of Object.entries(source)) {
            next[identifier] = coerce(raw, specOf(identifier))
          }
        }
        sseValues.value = next
      } catch {
        // 非 JSON 报文不影响上位机展示
      }
    }
  }
)

/* ---------- 写值 / 服务 ---------- */

async function handleUpdate(widget, value) {
  const identifier = widget.identifier
  if (!identifier) return
  const previous = sseValues.value[identifier]
  sseValues.value = { ...sseValues.value, [identifier]: value }
  try {
    await deviceApi.setDeviceShadowDesired(deviceKey.value, { [identifier]: value })
    ElMessage.success(`${widget.title || identifier} 已下发`)
    queryClient.invalidateQueries({ queryKey: ['hmi-properties', deviceKey.value] })
    queryClient.invalidateQueries({ queryKey: ['hmi-shadow', deviceKey.value] })
  } catch {
    const next = { ...sseValues.value }
    if (previous === undefined) {
      delete next[identifier]
    } else {
      next[identifier] = previous
    }
    sseValues.value = next
  }
}

const serviceDialogVisible = ref(false)
const activeService = ref(null)

async function handleCommand(widget) {
  const service = detail.value?.model?.services?.find((item) => item.identifier === widget.identifier)
  if (!service) {
    ElMessage.warning('服务已从物模型移除')
    return
  }
  activeService.value = service
  if (!service.input || service.input.length === 0) {
    await sendService(service, {})
  } else {
    serviceDialogVisible.value = true
  }
}

async function sendService(service, params) {
  try {
    await deviceApi.sendCommand(deviceKey.value, {
      type: 'service',
      identifier: service.identifier,
      params,
      callType: service.callType || 'async'
    })
    ElMessage.success('服务调用已下发')
  } catch {
    // 错误提示由 axios 拦截器统一给出
  }
}

function handleServiceSubmitted() {
  queryClient.invalidateQueries({ queryKey: ['hmi-properties', deviceKey.value] })
}

/* ---------- 事件 ---------- */

const eventsQuery = useQuery({
  queryKey: computed(() => ['hmi-events', deviceKey.value]),
  queryFn: async () => unwrapResult(await deviceApi.getEvents(deviceKey.value, { page: 1, size: 8 }), { records: [] }),
  enabled: computed(() => Boolean(deviceKey.value)),
  refetchInterval: 15000
})

const eventRecords = computed(() => eventsQuery.data.value?.records || [])

/* ---------- 编辑 ---------- */

const editorVisible = ref(false)
const saving = ref(false)

async function handleSaveEdit(payload) {
  if (!detail.value) return
  saving.value = true
  try {
    unwrapResult(await hmiApi.update(detail.value.id, {
      name: payload.name,
      deviceId: detail.value.deviceId,
      config: JSON.stringify(payload.config)
    }))
    await detailQuery.refetch()
    editorVisible.value = false
    ElMessage.success('上位机已保存')
  } finally {
    saving.value = false
  }
}

/* ---------- 通用 ---------- */

const realtimeText = computed(() => {
  switch (ui.realtimeStatus) {
    case 'open':
      return '实时通道已连接'
    case 'connecting':
      return '实时通道连接中'
    case 'reconnecting':
      return '实时通道重连中'
    default:
      return '实时通道未连接'
  }
})

const lastRefreshText = ref('—')
watch(
  () => propertiesQuery.dataUpdatedAt.value,
  (timestamp) => {
    if (!timestamp) return
    lastRefreshText.value = new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
  }
)

function refreshAll() {
  propertiesQuery.refetch()
  shadowQuery.refetch()
  eventsQuery.refetch()
  detailQuery.refetch()
}

function goList() {
  router.push('/workbench/hmi')
}

function goDevice() {
  if (detail.value) router.push(`/workbench/devices/${detail.value.deviceId}`)
}
</script>

<style scoped>
.hmi-loading {
  padding: var(--spacing-xl);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.runtime-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-lg);
  flex-wrap: wrap;
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md) var(--spacing-lg);
}

.runtime-bar__device {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-width: 0;
}

.runtime-bar__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.runtime-bar__value {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.runtime-bar__key {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.runtime-bar__status {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.runtime-bar__divider {
  width: 1px;
  height: 12px;
  background: var(--border-color);
  margin: 0 var(--spacing-xs);
}

.runtime-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
}

.runtime-dot.open {
  background: var(--color-success, #12b76a);
}

.runtime-dot.reconnecting,
.runtime-dot.connecting {
  background: var(--color-warning, #f79009);
}

.runtime-alert {
  margin-bottom: var(--grid-gutter);
}
</style>
