<template>
  <div class="device-debug-page page">
    <PageHeader :title="pageTitle" :desc="pageDesc">
      <template #actions>
        <el-button @click="goBack">返回设备</el-button>
        <el-button :loading="contextQuery.isFetching.value" @click="contextQuery.refetch()">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </template>
    </PageHeader>

    <div v-if="deviceQuery.isLoading.value" class="debug-loading card">加载设备信息...</div>

    <EmptyState v-else-if="!deviceKey" description="设备不存在或加载失败">
      <el-button size="small" @click="goBack">返回设备列表</el-button>
    </EmptyState>

    <template v-else>
      <el-tabs v-model="activeTab" class="debug-tabs">
        <!-- ================= 连接信息 ================= -->
        <el-tab-pane label="连接信息" name="connection">
          <div v-if="context" class="debug-connection">
            <el-alert
              v-if="!context.modeled"
              type="warning"
              :closable="false"
              show-icon
              title="该产品尚未定义物模型"
              description="模拟上报仍可发送，但属性解析 / 事件 / 告警不会触发。请先到产品页定义物模型。"
            />

            <div class="card">
              <h3 class="card-title">Broker 连接参数</h3>
              <p class="card-desc">
                设备端按以下参数直连 Broker；密码位不回显，仅在创建 / 重置设备时一次性返回。
              </p>
              <div class="endpoint-grid">
                <div v-for="item in endpoints" :key="item.label" class="endpoint">
                  <span class="endpoint__label">{{ item.label }}</span>
                  <code class="endpoint__value">{{ item.value }}</code>
                  <el-button link type="primary" size="small" @click="copy(item.value, item.label)">复制</el-button>
                </div>
              </div>
              <div class="credential-row">
                <div>
                  <span class="field-label">用户名</span>
                  <code class="field-value">{{ context.broker.username }}</code>
                </div>
                <div>
                  <span class="field-label">密码</span>
                  <span class="field-value field-value--muted">{{ context.broker.passwordHint }}</span>
                </div>
              </div>
            </div>

            <div class="card">
              <h3 class="card-title">Topic 一览</h3>
              <table class="debug-table">
                <thead>
                  <tr><th>方向</th><th>类型</th><th>Topic</th><th /></tr>
                </thead>
                <tbody>
                  <tr v-for="item in topicRows" :key="item.type">
                    <td>{{ item.direction }}</td>
                    <td><code>{{ item.type }}</code></td>
                    <td><code>{{ item.topic }}</code></td>
                    <td class="debug-table__action">
                      <el-button link type="primary" size="small" @click="copy(item.topic, item.type)">复制</el-button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="card">
              <h3 class="card-title">属性最新值</h3>
              <div v-if="context.properties.length === 0" class="empty-hint">物模型中没有属性</div>
              <table v-else class="debug-table">
                <thead>
                  <tr><th>标识符</th><th>类型</th><th>可写</th><th>最新值</th><th>上报时间</th></tr>
                </thead>
                <tbody>
                  <tr v-for="property in context.properties" :key="property.identifier">
                    <td><code>{{ property.identifier }}</code></td>
                    <td>{{ property.type }}</td>
                    <td>{{ property.accessMode === 'rw' ? '是' : '否' }}</td>
                    <td>{{ property.value ?? '—' }}</td>
                    <td>{{ formatTime(property.reportedAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div v-else class="debug-loading card">加载调试上下文...</div>
        </el-tab-pane>

        <!-- ================= 上行模拟 ================= -->
        <el-tab-pane label="上行模拟" name="simulate">
          <div class="debug-simulate">
            <div class="card">
              <h3 class="card-title">模拟设备上报</h3>
              <p class="card-desc">
                报文会进入与真机完全相同的摄取管线：物模型解析、影子、规则、告警、Webhook 与实时推送都会真实触发。
              </p>
              <el-form label-position="top" @submit.prevent>
                <el-form-item label="消息类型">
                  <el-select v-model="messageType" class="simulate-type">
                    <el-option
                      v-for="item in MESSAGE_TYPES"
                      :key="item.value"
                      :value="item.value"
                      :label="item.label"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item :label="payloadLabel">
                  <el-input
                    v-model="payloadText"
                    type="textarea"
                    :rows="12"
                    class="payload-editor"
                    :placeholder="payloadPlaceholder"
                  />
                </el-form-item>
              </el-form>
              <div class="simulate-actions">
                <el-button @click="resetPayload">恢复模板</el-button>
                <el-button type="primary" :loading="simulating" @click="submitSimulation">发送模拟上报</el-button>
              </div>
            </div>

            <div class="simulate-side">
              <div class="card">
                <h3 class="card-title">发送结果</h3>
                <el-empty v-if="!simulateResult" description="尚未发送" :image-size="60" />
                <template v-else>
                  <el-alert
                    :type="simulateResult.submitted ? 'success' : 'warning'"
                    :closable="false"
                    show-icon
                    :title="simulateResult.submitted ? '已提交到摄取管线' : '摄取管线已关闭，本次未提交'"
                  />
                  <dl class="result-list">
                    <div><dt>Topic</dt><dd><code>{{ simulateResult.topic }}</code></dd></div>
                    <div><dt>消息类型</dt><dd>{{ simulateResult.messageType }}</dd></div>
                    <div><dt>载荷大小</dt><dd>{{ simulateResult.size }} 字节</dd></div>
                    <div><dt>接收时间</dt><dd>{{ formatTime(simulateResult.acceptedAt) }}</dd></div>
                  </dl>
                </template>
              </div>
              <div class="card">
                <h3 class="card-title">属性模板说明</h3>
                <p class="card-desc">
                  模板按物模型属性生成，值只是占位。修改后点击发送即可验证解析、告警与规则链路。
                </p>
                <div class="template-hints">
                  <span v-for="property in context?.properties ?? []" :key="property.identifier" class="template-hint">
                    {{ property.identifier }} · {{ property.type }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ================= 实时报文 ================= -->
        <el-tab-pane label="实时报文" name="live">
          <div class="debug-live">
            <div class="card">
              <div class="section-head">
                <h3 class="card-title">上行报文</h3>
                <span :class="['connection-badge', ui.realtimeStatus]">
                  {{ realtimeText }}
                </span>
              </div>
              <el-empty v-if="liveEvents.length === 0" description="尚未收到该设备的实时报文" :image-size="60" />
              <div v-else class="event-list">
                <div v-for="event in liveEvents" :key="event.id" class="event-item">
                  <div class="event-item__head">
                    <code class="event-item__topic">{{ event.topic || 'device.data' }}</code>
                    <span class="event-item__seq">#{{ event.id }}</span>
                  </div>
                  <pre class="event-item__payload">{{ prettyPayload(event.payload) }}</pre>
                </div>
              </div>
            </div>

            <div class="card">
              <div class="section-head">
                <h3 class="card-title">最近命令</h3>
                <el-button link type="primary" size="small" @click="commandsQuery.refetch()">刷新</el-button>
              </div>
              <el-empty v-if="commands.length === 0" description="该设备还没有命令记录" :image-size="60" />
              <table v-else class="debug-table">
                <thead>
                  <tr><th>类型</th><th>标识符</th><th>状态</th><th>创建时间</th></tr>
                </thead>
                <tbody>
                  <tr v-for="record in commands" :key="record.commandId || record.id">
                    <td>{{ record.commandType || record.type }}</td>
                    <td>{{ record.identifier || '—' }}</td>
                    <td><span :class="['command-status', String(record.status).toLowerCase()]">{{ record.status }}</span></td>
                    <td>{{ formatTime(record.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="card live-command-card">
            <h3 class="card-title">下行调试</h3>
            <el-alert
              v-if="!canSendCommand"
              type="info"
              :closable="false"
              show-icon
              title="当前角色为只读，不能下发命令"
              description="属性设置与服务调用仅对 ADMIN / OPERATOR 开放；你仍可查看实时报文与命令记录。"
            />
            <DeviceControlPanel
              v-else
              :capability="capability"
              :device-key="deviceKey"
              :loading="contextQuery.isFetching.value"
              :sending="commandSending"
              @send="sendCommand"
            />
          </div>
        </el-tab-pane>

        <!-- ================= 属性代码 ================= -->
        <el-tab-pane label="属性代码" name="code">
          <div class="debug-code">
            <div class="card code-toolbar">
              <div class="code-toolbar__row">
                <el-radio-group v-model="codeTarget" @change="handleTargetChange">
                  <el-radio-button value="device">设备端</el-radio-button>
                  <el-radio-button value="host">上位机端</el-radio-button>
                </el-radio-group>
                <el-select v-model="codeLanguage" class="code-language">
                  <el-option
                    v-for="item in languageOptions"
                    :key="item.value"
                    :value="item.value"
                    :label="item.label"
                  />
                </el-select>
                <el-button :disabled="!code" @click="downloadCode">下载代码</el-button>
              </div>
              <p class="card-desc">
                代码按当前设备与物模型生成。设备端密钥、上位机 API Key 均为占位符，请通过环境变量注入。
              </p>
            </div>

            <div v-if="codeQuery.isFetching.value" class="debug-loading card">生成代码...</div>
            <CodeBlock v-else-if="code" :code="code.content" :language="highlightLanguage" />
            <EmptyState v-else description="代码生成失败，请稍后重试" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
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
import CodeBlock from '@/components/docs/CodeBlock.vue'
import DeviceControlPanel from '@/components/device/DeviceControlPanel.vue'
import { deviceApi } from '@/api/device'
import { debugApi } from '@/api/debug'
import { unwrapResult } from '@/utils/result'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'

const MESSAGE_TYPES = [
  { value: 'data', label: '属性 / 事件上报 data' },
  { value: 'heartbeat', label: '心跳 heartbeat' },
  { value: 'lwt', label: '离线遗嘱 lwt' },
  { value: 'reply', label: '命令回执 reply' },
  { value: 'ota', label: 'OTA 进度 ota' }
]

const JSON_REQUIRED = ['data', 'reply', 'ota']
const LANGUAGE_OPTIONS = {
  device: [
    { value: 'javascript', label: 'JavaScript (Node.js)' },
    { value: 'python', label: 'Python (paho-mqtt)' },
    { value: 'java', label: 'Java (Paho)' },
    { value: 'arduino', label: 'Arduino C++ (ESP32)' }
  ],
  host: [
    { value: 'javascript', label: 'JavaScript (fetch)' },
    { value: 'python', label: 'Python (requests)' },
    { value: 'java', label: 'Java (HttpClient)' }
  ]
}

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const ui = useUiStore()
const authStore = useAuthStore()

const deviceId = computed(() => route.params.id)
const activeTab = ref('connection')

const deviceQuery = useQuery({
  queryKey: computed(() => ['debug-device', deviceId.value]),
  queryFn: async () => unwrapResult(await deviceApi.getDetail(deviceId.value))
})

const device = computed(() => deviceQuery.data.value)
const deviceKey = computed(() => device.value?.deviceKey || '')

const contextQuery = useQuery({
  queryKey: computed(() => ['device-debug-context', deviceKey.value]),
  queryFn: async () => unwrapResult(await debugApi.getContext(deviceKey.value)),
  enabled: computed(() => Boolean(deviceKey.value))
})

const context = computed(() => contextQuery.data.value)
const pageTitle = computed(() => (device.value ? `调试 · ${device.value.deviceName}` : '设备调试'))
const pageDesc = computed(() =>
  device.value ? `设备标识 ${device.value.deviceKey} · 模拟上行与属性代码联调` : ''
)

const endpoints = computed(() => {
  const broker = context.value?.broker
  if (!broker) return []
  return [
    { label: 'MQTT TCP', value: broker.tcp },
    { label: 'MQTT TLS', value: broker.tls },
    { label: 'MQTT WebSocket', value: broker.ws }
  ]
})

const topicRows = computed(() => {
  const topics = context.value?.topics
  if (!topics) return []
  return [
    { direction: '上行', type: 'data', topic: topics.data },
    { direction: '上行', type: 'heartbeat', topic: topics.heartbeat },
    { direction: '上行', type: 'lwt', topic: topics.lwt },
    { direction: '上行', type: 'reply', topic: topics.reply },
    { direction: '上行', type: 'ota', topic: topics.ota },
    { direction: '下行', type: 'cmd/down', topic: topics.commandDown }
  ]
})

/* ---------- 上行模拟 ---------- */

const messageType = ref('data')
const payloadText = ref('')
const simulating = ref(false)
const simulateResult = ref(null)

const payloadLabel = computed(() =>
  JSON_REQUIRED.includes(messageType.value) ? 'JSON 载荷' : '载荷（可空）'
)
const payloadPlaceholder = computed(() =>
  JSON_REQUIRED.includes(messageType.value) ? '{"identifier": value}' : '可留空，或填写文本'
)

function sampleValue(property) {
  const type = String(property.type || '').toLowerCase()
  const latest = property.value
  if (latest !== null && latest !== undefined && latest !== '') {
    if (type === 'bool' || type === 'boolean') return latest === 'true' || latest === '1'
    if (['int', 'integer', 'long', 'float', 'double', 'number'].includes(type)) return Number(latest)
    return latest
  }
  if (type === 'bool' || type === 'boolean') return false
  if (['int', 'integer', 'long'].includes(type)) return property.min ?? 0
  if (['float', 'double', 'number'].includes(type)) return property.min ?? 25.0
  if (type === 'enum') return property.enumKeys?.[0] ?? 'value'
  if (type === 'date') return '2026-01-01 00:00:00'
  if (type === 'struct' || type === 'object') return {}
  if (type === 'array') return []
  return 'hello'
}

function defaultPayload(type) {
  if (type === 'heartbeat' || type === 'lwt') return ''
  if (type === 'reply') return JSON.stringify({ id: 'command-id', code: 200, data: {} }, null, 2)
  if (type === 'ota') return JSON.stringify({ step: 'downloading', progress: 50 }, null, 2)
  const params = {}
  for (const property of context.value?.properties ?? []) {
    params[property.identifier] = sampleValue(property)
  }
  // 物模型解析只认 Alink 信封：平铺的 {temperature: 25} 会被判定为无法解析
  return JSON.stringify({
    id: `debug-${Date.now()}`,
    version: '1.0',
    method: 'thing.event.property.post',
    params
  }, null, 2)
}

function resetPayload() {
  payloadText.value = defaultPayload(messageType.value)
}

watch(messageType, () => resetPayload(), { immediate: true })
watch(context, (value) => {
  if (value && !payloadText.value.trim()) resetPayload()
})

async function submitSimulation() {
  let payload = null
  if (JSON_REQUIRED.includes(messageType.value)) {
    try {
      payload = JSON.parse(payloadText.value || '{}')
    } catch {
      ElMessage.warning('载荷不是合法 JSON')
      return
    }
  } else if (payloadText.value.trim()) {
    payload = payloadText.value
  }

  simulating.value = true
  try {
    simulateResult.value = unwrapResult(
      await debugApi.simulate(deviceKey.value, { messageType: messageType.value, payload })
    )
    if (simulateResult.value?.submitted) {
      ElMessage.success('模拟上报已提交到摄取管线')
    } else {
      ElMessage.warning('摄取管线已关闭，本次模拟未提交')
    }
    queryClient.invalidateQueries({ queryKey: ['device-debug-context', deviceKey.value] })
  } finally {
    simulating.value = false
  }
}

/* ---------- 实时报文 ---------- */

const liveEvents = computed(() =>
  [...ui.events]
    .reverse()
    .filter((event) => event.deviceKey === deviceKey.value)
    .slice(0, 50)
)

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

const commandsQuery = useQuery({
  queryKey: computed(() => ['debug-commands', deviceKey.value]),
  queryFn: async () =>
    unwrapResult(await deviceApi.getCommandRecords(deviceKey.value, { page: 1, size: 10 }), { records: [] }),
  enabled: computed(() => Boolean(deviceKey.value)),
  refetchInterval: 10000
})
const commands = computed(() => commandsQuery.data.value?.records || [])

const canSendCommand = computed(() => authStore.hasRole(['ADMIN', 'OPERATOR']))
const commandSending = ref(false)
const capability = computed(() => ({
  modeled: Boolean(context.value?.modeled),
  version: context.value?.thingModelVersion || 0,
  properties: context.value?.writableProperties || [],
  services: context.value?.services || []
}))

async function sendCommand(payload) {
  if (!canSendCommand.value) {
    ElMessage.warning('当前角色无权下发命令')
    return
  }
  commandSending.value = true
  try {
    await deviceApi.sendCommand(deviceKey.value, payload)
    ElMessage.success('命令已下发')
    commandsQuery.refetch()
  } finally {
    commandSending.value = false
  }
}

function prettyPayload(payload) {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload || ''
  }
}

/* ---------- 属性代码 ---------- */

const codeTarget = ref('device')
const codeLanguage = ref('javascript')
const languageOptions = computed(() => LANGUAGE_OPTIONS[codeTarget.value] || [])

watch(codeTarget, (target) => {
  const options = LANGUAGE_OPTIONS[target] || []
  if (!options.some((item) => item.value === codeLanguage.value)) {
    codeLanguage.value = options[0]?.value || 'javascript'
  }
})

const codeQuery = useQuery({
  queryKey: computed(() => ['device-debug-code', deviceKey.value, codeTarget.value, codeLanguage.value]),
  queryFn: async () =>
    unwrapResult(
      await debugApi.getCode(deviceKey.value, {
        target: codeTarget.value,
        language: codeLanguage.value
      })
    ),
  enabled: computed(() => Boolean(deviceKey.value) && activeTab.value === 'code')
})

const code = computed(() => codeQuery.data.value)
const highlightLanguage = computed(() => (codeLanguage.value === 'arduino' ? 'cpp' : codeLanguage.value))

function handleTargetChange() {
  // watch 会修正语言；这里只负责在切换后重新取代码
  codeQuery.refetch()
}

function downloadCode() {
  if (!code.value) return
  const blob = new Blob([code.value.content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = code.value.filename || 'debug-code.txt'
  link.click()
  URL.revokeObjectURL(url)
}

/* ---------- 通用 ---------- */

async function copy(value, label) {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(`${label} 已复制`)
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

function goBack() {
  router.push(`/workbench/devices/${deviceId.value}`)
}
</script>

<style scoped>
.debug-loading {
  padding: var(--spacing-xl);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.debug-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--spacing-lg);
}

.debug-connection,
.debug-simulate,
.debug-live,
.debug-code {
  display: flex;
  flex-direction: column;
  gap: var(--grid-gutter);
}

.debug-simulate,
.debug-live {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(280px, 2fr);
  align-items: start;
}

.debug-simulate > .card:first-child {
  grid-row: span 2;
}

.simulate-side {
  display: flex;
  flex-direction: column;
  gap: var(--grid-gutter);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-md);
  line-height: 1.6;
}

.endpoint-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--spacing-md);
}

.endpoint {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-bg);
  min-width: 0;
}

.endpoint__label {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.endpoint__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-primary);
}

.credential-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xl);
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--border-color-light);
}

.field-label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 2px;
}

.field-value {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.field-value--muted {
  font-family: var(--font-family);
  color: var(--color-text-secondary);
}

.debug-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}

.debug-table th,
.debug-table td {
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
  text-align: left;
  vertical-align: middle;
}

.debug-table th {
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
  background: var(--color-bg);
}

.debug-table td code,
.debug-table th code {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.debug-table__action {
  text-align: right;
}

.empty-hint {
  padding: var(--spacing-lg);
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.simulate-type {
  width: 260px;
}

.payload-editor :deep(textarea) {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.simulate-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.result-list {
  margin-top: var(--spacing-md);
}

.result-list > div {
  display: flex;
  gap: var(--spacing-md);
  padding: var(--spacing-xs) 0;
  border-bottom: 1px dashed var(--border-color-light);
}

.result-list dt {
  width: 80px;
  flex-shrink: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.result-list dd {
  margin: 0;
  min-width: 0;
  word-break: break-all;
  font-size: var(--font-size-sm);
}

.template-hints {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
}

.template-hint {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-bg);
  color: var(--color-text-secondary);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.section-head .card-title {
  margin-bottom: 0;
}

.connection-badge {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: var(--font-size-xs);
  background: var(--color-bg);
  color: var(--color-text-tertiary);
}

.connection-badge.open {
  background: var(--color-success-light, #ecfdf3);
  color: var(--color-success, #12b76a);
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  max-height: 560px;
  overflow-y: auto;
}

.event-item {
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-bg);
}

.event-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.event-item__topic {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.event-item__seq {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.event-item__payload {
  margin: 0;
  max-height: 180px;
  overflow: auto;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

.command-status {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-bg);
  font-size: var(--font-size-xs);
}

.live-command-card {
  margin-top: var(--grid-gutter);
}

.code-toolbar__row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  margin-bottom: var(--spacing-sm);
}

.code-language {
  width: 220px;
}

@media (max-width: 1024px) {
  .debug-simulate,
  .debug-live {
    grid-template-columns: minmax(0, 1fr);
  }

  .debug-simulate > .card:first-child {
    grid-row: auto;
  }
}
</style>
