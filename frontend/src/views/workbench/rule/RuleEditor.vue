<template>
  <el-drawer
    :model-value="modelValue"
    :title="editingId ? '编辑消息规则' : '新建消息规则'"
    size="560px"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <el-form label-position="top" class="rule-form" @submit.prevent>
      <div class="form-section-title">① 基础信息</div>
      <el-form-item label="规则名称" required>
        <el-input v-model="form.name" maxlength="64" placeholder="如：高温联动降温" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="255"
          placeholder="可选，说明规则的用途"
        />
      </el-form-item>
      <el-form-item label="作用设备">
        <el-select v-model="form.deviceId" clearable placeholder="全部设备" style="width: 100%">
          <el-option
            v-for="device in devices"
            :key="device.id"
            :label="device.deviceName || device.deviceKey"
            :value="device.id"
          />
        </el-select>
      </el-form-item>

      <div class="form-section-title">② 触发条件</div>
      <el-form-item label="触发源" required>
        <el-select v-model="form.sourceType" style="width: 100%">
          <el-option
            v-for="option in SOURCE_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="form.sourceType === 'EVENT' ? '事件标识符' : '属性标识符'" required>
        <el-input
          v-model="form.identifier"
          maxlength="64"
          :placeholder="form.sourceType === 'EVENT' ? '如：overtemp' : '如：temperature'"
        />
      </el-form-item>

      <template v-if="form.sourceType === 'PROPERTY'">
        <div class="form-row">
          <el-form-item label="比较符" required>
            <el-select v-model="form.operator" style="width: 100%">
              <el-option
                v-for="option in OPERATOR_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="阈值" required>
            <el-input v-model="form.thresholdValue" placeholder="如：40" />
          </el-form-item>
        </div>
      </template>

      <el-form-item v-else label="事件类型（可选）">
        <el-select v-model="form.eventType" clearable placeholder="全部事件类型" style="width: 100%">
          <el-option
            v-for="option in EVENT_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <div class="form-section-title">③ 执行动作</div>
      <el-form-item label="动作类型" required>
        <el-select v-model="form.actionType" style="width: 100%">
          <el-option
            v-for="option in ACTION_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <template v-if="form.actionType === 'UPDATE_PROPERTY'">
        <div class="form-row">
          <el-form-item label="目标属性" required>
            <el-input v-model="form.targetIdentifier" maxlength="64" placeholder="如：switch" />
          </el-form-item>
          <el-form-item label="写入值" required>
            <el-input v-model="form.targetValue" placeholder="如：on" />
          </el-form-item>
        </div>
      </template>

      <template v-else-if="form.actionType === 'SEND_COMMAND'">
        <el-form-item label="命令类型" required>
          <el-select v-model="form.commandType" style="width: 100%">
            <el-option label="属性设置 (property_set)" value="property_set" />
            <el-option label="服务调用 (service)" value="service" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标标识符" required>
          <el-input v-model="form.targetIdentifier" maxlength="64" placeholder="属性标识符或服务标识符" />
        </el-form-item>
        <el-form-item label="命令参数（JSON，可选）">
          <el-input
            v-model="form.commandParams"
            type="textarea"
            :rows="3"
            placeholder="如：{&quot;power&quot;: 1}"
          />
        </el-form-item>
      </template>

      <template v-else-if="form.actionType === 'FORWARD_MQTT'">
        <el-form-item label="目标 Topic" required>
          <el-input v-model="form.topic" maxlength="255" placeholder="如：factory/line1/alert" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="QoS">
            <el-select v-model="form.qos" style="width: 100%">
              <el-option label="0" :value="0" />
              <el-option label="1" :value="1" />
              <el-option label="2" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="载荷模板（可选）">
            <el-input v-model="form.payloadTemplate" placeholder="支持 ${value} 等占位符" />
          </el-form-item>
        </div>
      </template>

      <template v-else>
        <el-form-item label="目标 URL" required>
          <el-input v-model="form.url" maxlength="512" placeholder="https://erp.example.com/hook" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="请求方法">
            <el-select v-model="form.method" style="width: 100%">
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
            </el-select>
          </el-form-item>
          <el-form-item label="载荷模板（可选）">
            <el-input v-model="form.payloadTemplate" placeholder="支持 ${value} 等占位符" />
          </el-form-item>
        </div>
        <el-form-item label="请求头（JSON，可选）">
          <el-input
            v-model="form.headers"
            type="textarea"
            :rows="3"
            placeholder="如：{&quot;X-Tenant&quot;: &quot;acme&quot;}"
          />
        </el-form-item>
      </template>

      <div class="form-section-title">高级</div>
      <el-form-item label="冷却窗口（秒）">
        <el-input-number
          v-model="form.cooldownSeconds"
          :min="0"
          :max="86400"
          controls-position="right"
          style="width: 100%"
        />
        <p class="form-hint">窗口内重复命中只触发一次；0 表示不限制</p>
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :disabled="!editingId" @click="openTestDialog">
        <svg-icon name="bolt" :size="14" />
        试运行
      </el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ editingId ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-drawer>

  <el-dialog v-model="testVisible" title="规则试运行（干跑）" width="480px">
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="设备" required>
        <el-select v-model="testForm.deviceId" placeholder="选择设备" style="width: 100%">
          <el-option
            v-for="device in devices"
            :key="device.id"
            :label="device.deviceName || device.deviceKey"
            :value="device.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="标识符" required>
        <el-input v-model="testForm.identifier" placeholder="待判定的属性 / 事件标识符" />
      </el-form-item>
      <el-form-item label="取值">
        <el-input v-model="testForm.valueText" placeholder="数值比较符下填数字，可留空" />
      </el-form-item>
    </el-form>

    <div v-if="testResult" class="test-result">
      <div class="test-row">
        <span class="test-label">命中结论</span>
        <el-tag :type="testResult.matched ? 'success' : 'info'" size="small">
          {{ testResult.matched ? '命中' : '未命中' }}
        </el-tag>
      </div>
      <div class="test-row">
        <span class="test-label">说明</span>
        <span class="test-value">{{ testResult.reason || '—' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">设备建模</span>
        <span class="test-value">{{ testResult.deviceModeled ? '是' : '否' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">标识符已定义</span>
        <span class="test-value">{{ testResult.identifierModeled ? '是' : '否' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">动作可执行</span>
        <span class="test-value">{{ testResult.actionExecutable ? '是' : '否' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">动作摘要</span>
        <span class="test-value">{{ testResult.actionSummary || '—' }}</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="testVisible = false">关闭</el-button>
      <el-button type="primary" :loading="testing" @click="handleTest">开始试运行</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'
import { useRuleMutations, useRuleTestMutation } from '@/composables/useRules'
import {
  SOURCE_TYPE_OPTIONS,
  ACTION_TYPE_OPTIONS,
  OPERATOR_OPTIONS,
  EVENT_TYPE_OPTIONS
} from '@/utils/rule'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  rule: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const NUMERIC_OPERATORS = new Set(['GT', 'GTE', 'LT', 'LTE'])

const { saving, createRule, updateRule } = useRuleMutations()
const { testing, testRule } = useRuleTestMutation()

const devices = ref([])
const editingId = computed(() => props.rule?.id ?? null)

const testVisible = ref(false)
const testResult = ref(null)
const testForm = reactive({ deviceId: null, identifier: '', valueText: '' })

function emptyForm() {
  return {
    name: '',
    description: '',
    deviceId: null,
    sourceType: 'PROPERTY',
    identifier: '',
    operator: 'GT',
    thresholdValue: '',
    eventType: '',
    actionType: 'UPDATE_PROPERTY',
    targetIdentifier: '',
    targetValue: '',
    commandType: 'property_set',
    commandParams: '',
    topic: '',
    qos: 1,
    url: '',
    method: 'POST',
    headers: '',
    payloadTemplate: '',
    cooldownSeconds: 0,
    enabled: 1
  }
}

const form = reactive(emptyForm())

/** 把规则实体回填到表单，actionConfig 按动作类型拆分到各自字段。 */
function fillForm(rule) {
  Object.assign(form, emptyForm())
  if (!rule) return
  const config = rule.actionConfig || {}
  Object.assign(form, {
    name: rule.name || '',
    description: rule.description || '',
    deviceId: rule.deviceId ?? null,
    sourceType: rule.sourceType || 'PROPERTY',
    identifier: rule.identifier || '',
    operator: rule.operator || 'GT',
    thresholdValue: rule.thresholdValue || '',
    eventType: rule.eventType || '',
    actionType: rule.actionType || 'UPDATE_PROPERTY',
    cooldownSeconds: rule.cooldownSeconds ?? 0,
    enabled: rule.enabled ?? 1
  })
  if (rule.actionType === 'UPDATE_PROPERTY') {
    form.targetIdentifier = config.identifier || ''
    form.targetValue = config.value ?? ''
  } else if (rule.actionType === 'SEND_COMMAND') {
    form.commandType = config.commandType || 'property_set'
    form.targetIdentifier = config.identifier || ''
    form.commandParams = config.params ? JSON.stringify(config.params) : ''
  } else if (rule.actionType === 'FORWARD_MQTT') {
    form.topic = config.topic || ''
    form.qos = config.qos ?? 1
    form.payloadTemplate = config.payloadTemplate || ''
  } else if (rule.actionType === 'FORWARD_HTTP') {
    form.url = config.url || ''
    form.method = config.method || 'POST'
    form.headers = config.headers ? JSON.stringify(config.headers) : ''
    form.payloadTemplate = config.payloadTemplate || ''
  }
}

watch(
  () => [props.modelValue, props.rule],
  ([visible]) => {
    if (visible) {
      fillForm(props.rule)
      testResult.value = null
    }
  },
  { immediate: true }
)

function parseJsonObject(text, label) {
  if (!text || !text.trim()) return null
  let parsed
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error(`${label}必须是合法 JSON`)
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${label}必须是 JSON 对象`)
  }
  return parsed
}

function validateForm() {
  if (!form.name.trim()) return '请填写规则名称'
  if (!form.identifier.trim()) {
    return form.sourceType === 'EVENT' ? '请填写事件标识符' : '请填写属性标识符'
  }
  if (form.sourceType === 'PROPERTY') {
    const threshold = String(form.thresholdValue).trim()
    if (!threshold) return '请填写阈值'
    if (NUMERIC_OPERATORS.has(form.operator) && Number.isNaN(Number(threshold))) {
      return '数值比较符下阈值必须是数字'
    }
  }
  switch (form.actionType) {
    case 'UPDATE_PROPERTY':
      if (!form.targetIdentifier.trim()) return '请填写目标属性标识符'
      if (!String(form.targetValue).trim()) return '请填写写入值'
      break
    case 'SEND_COMMAND':
      if (!form.targetIdentifier.trim()) return '请填写目标标识符'
      break
    case 'FORWARD_MQTT':
      if (!form.topic.trim()) return '请填写目标 Topic'
      if (form.topic.includes('#') || form.topic.includes('+')) return 'Topic 不允许包含通配符 # / +'
      break
    case 'FORWARD_HTTP':
      if (!form.url.trim()) return '请填写目标 URL'
      if (!/^https?:\/\//.test(form.url.trim())) return 'URL 必须以 http:// 或 https:// 开头'
      break
    default:
      break
  }
  if (form.cooldownSeconds < 0 || form.cooldownSeconds > 86400) return '冷却窗口需在 0 ~ 86400 秒'
  return ''
}

function buildActionConfig() {
  switch (form.actionType) {
    case 'UPDATE_PROPERTY':
      return { identifier: form.targetIdentifier.trim(), value: String(form.targetValue).trim() }
    case 'SEND_COMMAND': {
      const config = { commandType: form.commandType, identifier: form.targetIdentifier.trim() }
      const params = parseJsonObject(form.commandParams, '命令参数')
      if (params) config.params = params
      return config
    }
    case 'FORWARD_MQTT': {
      const config = { topic: form.topic.trim(), qos: form.qos }
      if (form.payloadTemplate.trim()) config.payloadTemplate = form.payloadTemplate
      return config
    }
    default: {
      const config = { url: form.url.trim(), method: form.method }
      const headers = parseJsonObject(form.headers, '请求头')
      if (headers) config.headers = headers
      if (form.payloadTemplate.trim()) config.payloadTemplate = form.payloadTemplate
      return config
    }
  }
}

function buildPayload() {
  const payload = {
    name: form.name.trim(),
    description: form.description.trim() || null,
    deviceId: form.deviceId ?? null,
    sourceType: form.sourceType,
    identifier: form.identifier.trim(),
    actionType: form.actionType,
    actionConfig: buildActionConfig(),
    cooldownSeconds: form.cooldownSeconds ?? 0,
    enabled: form.enabled
  }
  if (form.sourceType === 'PROPERTY') {
    payload.operator = form.operator
    payload.thresholdValue = String(form.thresholdValue).trim()
    payload.eventType = null
  } else {
    payload.operator = null
    payload.thresholdValue = null
    payload.eventType = form.eventType || null
  }
  return payload
}

async function handleSubmit() {
  const error = validateForm()
  if (error) {
    ElMessage.warning(error)
    return
  }
  let payload
  try {
    payload = buildPayload()
  } catch (e) {
    ElMessage.warning(e.message)
    return
  }
  try {
    if (editingId.value) {
      await updateRule(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createRule(payload)
      ElMessage.success('规则已创建')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    // 具体失败原因已由 axios 拦截器统一提示
  }
}

function openTestDialog() {
  testForm.deviceId = form.deviceId ?? null
  testForm.identifier = form.identifier
  testForm.valueText = ''
  testResult.value = null
  testVisible.value = true
}

async function handleTest() {
  if (!testForm.deviceId) {
    ElMessage.warning('请选择试运行设备')
    return
  }
  if (!testForm.identifier.trim()) {
    ElMessage.warning('请填写标识符')
    return
  }
  try {
    testResult.value = await testRule(editingId.value, {
      deviceId: testForm.deviceId,
      identifier: testForm.identifier.trim(),
      valueText: testForm.valueText
    })
  } catch {
    // 失败原因由拦截器提示
  }
}

async function loadDevices() {
  try {
    const page = unwrapResult(await deviceApi.getList({ page: 1, size: 200 }), { records: [] })
    devices.value = page.records || []
  } catch {
    devices.value = []
  }
}

onMounted(loadDevices)
</script>

<style scoped>
.form-section-title {
  margin: var(--spacing-md) 0 var(--spacing-sm);
  padding-left: var(--spacing-sm);
  border-left: 3px solid var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.rule-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}

.form-hint {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.test-result {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius);
}

.test-row {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
}

.test-label {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.test-value {
  color: var(--color-text-primary);
  text-align: right;
  word-break: break-all;
}
</style>