<template>
  <el-drawer
    :model-value="modelValue"
    :title="editingId ? '编辑场景' : '新建场景'"
    size="640px"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <el-form label-position="top" class="scene-form" @submit.prevent>
      <div class="form-section-title">① 基础信息</div>
      <el-form-item label="场景名称" required>
        <el-input v-model="form.name" maxlength="64" placeholder="如：回家开灯" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="255"
          placeholder="可选，说明场景的用途"
        />
      </el-form-item>

      <div class="form-section-title">② 触发源</div>
      <el-form-item label="触发方式" required>
        <el-select v-model="form.triggerType" class="form-control" @change="handleTriggerTypeChange">
          <el-option
            v-for="option in TRIGGER_SOURCE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <template v-if="form.triggerType === 'TIMER'">
        <el-form-item label="Cron 表达式（5 字段）" required>
          <el-input v-model="form.timerCron" placeholder="如：*/5 * * * *" @keyup.enter="handleSubmit" />
          <p class="form-hint">分钟级定时，时区固定为 Asia/Shanghai。</p>
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="触发设备">
          <el-select v-model="form.triggerDeviceId" clearable filterable class="form-control" placeholder="全部设备">
            <el-option
              v-for="device in devices"
              :key="device.id"
              :label="device.deviceName || device.deviceKey"
              :value="device.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.triggerType === 'EVENT' ? '事件标识符' : '属性标识符'" required>
          <el-input
            v-model="form.triggerIdentifier"
            maxlength="64"
            :placeholder="form.triggerType === 'EVENT' ? '如：door_open' : '如：temperature'"
          />
        </el-form-item>

        <template v-if="form.triggerType === 'PROPERTY'">
          <el-form-item label="触发判定">
            <el-radio-group v-model="form.thresholdEnabled">
              <el-radio-button :value="false">任意上报即触发</el-radio-button>
              <el-radio-button :value="true">满足阈值时触发</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <div v-if="form.thresholdEnabled" class="form-row">
            <el-form-item label="比较符" required>
              <el-select v-model="form.triggerOperator" class="form-control">
                <el-option
                  v-for="option in OPERATOR_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="阈值" required>
              <el-input v-model="form.triggerThreshold" placeholder="如：30" />
            </el-form-item>
          </div>
        </template>

        <el-form-item v-else label="事件类型（可选）">
          <el-select v-model="form.triggerEventType" clearable class="form-control" placeholder="不限事件类型">
            <el-option
              v-for="option in EVENT_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </template>

      <div class="form-section-title">③ 条件组</div>
      <SceneConditionEditor
        v-model="form.conditions"
        v-model:logic="form.conditionLogic"
        :devices="devices"
        :timer-source="timerSource"
      />

      <div class="form-section-title">④ 执行步骤</div>
      <SceneStepEditor
        v-model="form.steps"
        :devices="devices"
        :products="products"
        :group-tree="groupTree"
        :tags="tags"
        :timer-source="timerSource"
      />

      <div class="form-section-title">高级</div>
      <el-form-item label="冷却窗口（秒）">
        <el-input-number
          v-model="form.cooldownSeconds"
          :min="0"
          :max="86400"
          controls-position="right"
          class="form-control"
        />
        <p class="form-hint">窗口内重复命中只触发一次；0 表示不限制</p>
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
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

  <SceneTestDialog
    v-model="testVisible"
    :scene-id="editingId"
    :trigger-type="form.triggerType"
    :devices="devices"
  />
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import SceneConditionEditor from '@/components/scene/SceneConditionEditor.vue'
import SceneStepEditor from '@/components/scene/SceneStepEditor.vue'
import SceneTestDialog from '@/components/scene/SceneTestDialog.vue'
import { deviceApi } from '@/api/device'
import { productApi } from '@/api/product'
import { useDeviceGroupTreeQuery, useTagListQuery } from '@/composables/useDeviceGroups'
import { useSceneMutations } from '@/composables/useScenes'
import { unwrapResult } from '@/utils/result'
import {
  TRIGGER_SOURCE_OPTIONS,
  OPERATOR_OPTIONS,
  EVENT_TYPE_OPTIONS,
  isForwardAction,
  parseJsonObject
} from '@/utils/scene'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  scene: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const NUMERIC_OPERATORS = new Set(['GT', 'GTE', 'LT', 'LTE'])
const MAX_CONDITIONS = 10
const MAX_STEPS = 20

const { saving, createScene, updateScene } = useSceneMutations()

const editingId = computed(() => props.scene?.id ?? null)
const timerSource = computed(() => form.triggerType === 'TIMER')

const testVisible = ref(false)

const { tree: groupTree } = useDeviceGroupTreeQuery()
const { tags } = useTagListQuery()

const productsQuery = useQuery({
  queryKey: ['scene-product-options'],
  queryFn: async () => unwrapResult(await productApi.getList(), [])
})
const products = computed(() => productsQuery.data.value ?? [])

const devicesQuery = useQuery({
  queryKey: ['scene-device-options'],
  queryFn: async () => unwrapResult(await deviceApi.getList({ page: 1, size: 200 }), { records: [] })
})
const devices = computed(() => devicesQuery.data.value?.records ?? [])

function emptyForm() {
  return {
    name: '',
    description: '',
    triggerType: 'PROPERTY',
    triggerDeviceId: null,
    triggerIdentifier: '',
    triggerOperator: 'GT',
    triggerThreshold: '',
    thresholdEnabled: false,
    triggerEventType: '',
    timerCron: '',
    conditionLogic: 'AND',
    conditions: [],
    cooldownSeconds: 0,
    enabled: true,
    steps: []
  }
}

const form = reactive(emptyForm())

/** 步骤实体回填为步骤编辑器形状（actionConfig 拆字段、targetMode 推断）。 */
function toEditorStep(step) {
  const config = step.actionConfig || {}
  const targetConfig = step.targetConfig && typeof step.targetConfig === 'object' ? step.targetConfig : null
  let actionConfig
  switch (step.actionType) {
    case 'SEND_COMMAND':
      actionConfig = {
        commandType: config.commandType || 'property_set',
        identifier: config.identifier || '',
        params:
          config.params == null
            ? ''
            : typeof config.params === 'string'
              ? config.params
              : JSON.stringify(config.params)
      }
      break
    case 'FORWARD_MQTT':
      actionConfig = {
        topic: config.topic || '',
        qos: config.qos ?? 1,
        payloadTemplate: config.payloadTemplate || ''
      }
      break
    case 'FORWARD_HTTP':
      actionConfig = {
        url: config.url || '',
        method: config.method || 'POST',
        headers:
          config.headers == null
            ? ''
            : typeof config.headers === 'string'
              ? config.headers
              : JSON.stringify(config.headers),
        payloadTemplate: config.payloadTemplate || ''
      }
      break
    case 'UPDATE_PROPERTY':
    default:
      actionConfig = { identifier: config.identifier || '', value: config.value ?? '' }
      break
  }
  return {
    seq: step.seq ?? 0,
    delaySeconds: Number(step.delaySeconds ?? 0),
    actionType: step.actionType || 'UPDATE_PROPERTY',
    targetType: step.targetType || 'TRIGGER',
    targetMode: resolveTargetMode(targetConfig),
    targetConfig:
      targetConfig || { deviceIds: [], productIds: [], groupIds: [], tagIds: [] },
    actionConfig,
    enabled: step.enabled === 1 || step.enabled === true
  }
}

function resolveTargetMode(config) {
  if (!config) return 'device'
  if (config.productIds?.length) return 'product'
  if (config.groupIds?.length) return 'group'
  if (config.tagIds?.length) return 'tag'
  return 'device'
}

/** 把场景实体回填到表单，条件组与步骤流归一化后交给子编辑器。 */
function fillForm(scene) {
  Object.assign(form, emptyForm())
  form.conditions = []
  form.steps = []
  if (!scene) return
  const hasThreshold = Boolean(scene.triggerOperator || scene.triggerThreshold)
  Object.assign(form, {
    name: scene.name || '',
    description: scene.description || '',
    triggerType: scene.triggerType || 'PROPERTY',
    triggerDeviceId: scene.triggerDeviceId ?? null,
    triggerIdentifier: scene.triggerIdentifier || '',
    triggerOperator: scene.triggerOperator || (hasThreshold ? 'GT' : 'GT'),
    triggerThreshold: scene.triggerThreshold ?? '',
    thresholdEnabled: hasThreshold,
    triggerEventType: scene.triggerEventType || '',
    timerCron: scene.timerCron || '',
    conditionLogic: scene.conditionLogic || 'AND',
    cooldownSeconds: scene.cooldownSeconds ?? 0,
    enabled: scene.enabled === 1 || scene.enabled === true
  })
  form.conditions = Array.isArray(scene.conditions)
    ? scene.conditions.map((condition) => ({
        deviceId: condition.deviceId ?? null,
        identifier: condition.identifier || '',
        operator: condition.operator || 'GT',
        threshold: condition.threshold ?? ''
      }))
    : []
  form.steps = Array.isArray(scene.steps) ? scene.steps.map(toEditorStep) : []
}

watch(
  () => [props.modelValue, props.scene],
  ([visible]) => {
    if (visible) fillForm(props.scene)
  },
  { immediate: true }
)

/** 切换触发方式时清理其他源专属字段（设计文档 §7.2）。 */
function handleTriggerTypeChange() {
  if (form.triggerType === 'TIMER') {
    form.triggerDeviceId = null
    form.triggerIdentifier = ''
    form.triggerOperator = null
    form.triggerThreshold = ''
    form.thresholdEnabled = false
    form.triggerEventType = ''
  } else {
    form.timerCron = ''
    if (form.triggerType === 'EVENT') {
      form.triggerOperator = null
      form.triggerThreshold = ''
      form.thresholdEnabled = false
    }
  }
}

function isValidCron(expr) {
  const parts = String(expr).trim().split(/\s+/)
  if (parts.length !== 5) return false
  return parts.every((part) => /^[\d*/,-]+$/.test(part))
}

function validateConditions() {
  if (form.conditions.length > MAX_CONDITIONS) {
    return `条件组最多 ${MAX_CONDITIONS} 项`
  }
  for (let index = 0; index < form.conditions.length; index += 1) {
    const item = form.conditions[index]
    const label = `第 ${index + 1} 个条件`
    if (timerSource.value && !item.deviceId) {
      return `${label}：定时触发下条件设备必填`
    }
    if (!String(item.identifier || '').trim()) return `${label}：请填写标识符`
    if (String(item.identifier).length > 64) return `${label}：标识符不能超过 64 个字符`
    if (!item.operator) return `${label}：请选择比较符`
    const threshold = String(item.threshold ?? '').trim()
    if (!threshold) return `${label}：请填写阈值`
    if (threshold.length > 255) return `${label}：阈值不能超过 255 个字符`
    if (NUMERIC_OPERATORS.has(item.operator) && Number.isNaN(Number(threshold))) {
      return `${label}：数值比较符下阈值必须是数字`
    }
  }
  return ''
}

function validateStepActionConfig(step, index) {
  const label = `第 ${index + 1} 步`
  const config = step.actionConfig || {}
  switch (step.actionType) {
    case 'UPDATE_PROPERTY':
      if (!String(config.identifier || '').trim()) return `${label}：请填写目标属性标识符`
      if (!String(config.value ?? '').trim()) return `${label}：请填写写入值`
      break
    case 'SEND_COMMAND':
      if (!config.commandType) return `${label}：请选择命令类型`
      if (!String(config.identifier || '').trim()) return `${label}：请填写命令标识符`
      if (String(config.params || '').trim()) {
        const parsed = parseJsonObject(config.params, '命令参数')
        if (parsed.error) return `${label}：${parsed.error}`
      }
      break
    case 'FORWARD_MQTT':
      if (!String(config.topic || '').trim()) return `${label}：请填写目标 Topic`
      if (String(config.topic).includes('#') || String(config.topic).includes('+')) {
        return `${label}：Topic 不允许包含通配符 # / +`
      }
      break
    case 'FORWARD_HTTP':
      if (!String(config.url || '').trim()) return `${label}：请填写目标 URL`
      if (!/^https?:\/\//.test(String(config.url).trim())) {
        return `${label}：URL 必须以 http:// 或 https:// 开头`
      }
      if (String(config.headers || '').trim()) {
        const parsed = parseJsonObject(config.headers, '请求头')
        if (parsed.error) return `${label}：${parsed.error}`
      }
      break
    default:
      return `${label}：未知的动作类型`
  }
  return ''
}

function targetConfigIsEmpty(config) {
  if (!config) return true
  return (
    !(config.deviceIds?.length || config.productIds?.length || config.groupIds?.length || config.tagIds?.length)
  )
}

function validateSteps() {
  if (form.steps.length === 0) return '请至少添加一个执行步骤'
  if (form.steps.length > MAX_STEPS) return `每个场景最多 ${MAX_STEPS} 个步骤`
  for (let index = 0; index < form.steps.length; index += 1) {
    const step = form.steps[index]
    const label = `第 ${index + 1} 步`
    if (step.delaySeconds < 0 || step.delaySeconds > 86400) {
      return `${label}：延时需在 0 ~ 86400 秒`
    }
    const targetType = isForwardAction(step.actionType) ? 'TRIGGER' : timerSource.value ? 'FIXED' : step.targetType
    if (!isForwardAction(step.actionType) && timerSource.value && targetType !== 'FIXED') {
      return `${label}：定时触发下目标必须指定设备`
    }
    if (targetType === 'FIXED' && targetConfigIsEmpty(step.targetConfig)) {
      return `${label}：请至少选择一个目标`
    }
    const configError = validateStepActionConfig(step, index)
    if (configError) return configError
  }
  return ''
}

function validateForm() {
  if (!form.name.trim()) return '请填写场景名称'
  if (form.name.trim().length > 64) return '场景名称不能超过 64 个字符'
  if (form.triggerType === 'TIMER') {
    if (!String(form.timerCron || '').trim()) return '请填写 Cron 表达式'
    if (!isValidCron(form.timerCron)) return 'Cron 表达式需为合法 5 字段'
  } else {
    if (!form.triggerIdentifier.trim()) {
      return form.triggerType === 'EVENT' ? '请填写事件标识符' : '请填写属性标识符'
    }
    if (form.triggerType === 'PROPERTY' && form.thresholdEnabled) {
      if (!form.triggerOperator) return '请选择比较符'
      const threshold = String(form.triggerThreshold ?? '').trim()
      if (!threshold) return '请填写阈值'
      if (NUMERIC_OPERATORS.has(form.triggerOperator) && Number.isNaN(Number(threshold))) {
        return '数值比较符下阈值必须是数字'
      }
    }
  }
  if (form.cooldownSeconds < 0 || form.cooldownSeconds > 86400) return '冷却窗口需在 0 ~ 86400 秒'
  const conditionError = validateConditions()
  if (conditionError) return conditionError
  return validateSteps()
}

function buildStepActionConfig(step) {
  const config = step.actionConfig || {}
  switch (step.actionType) {
    case 'UPDATE_PROPERTY':
      return { identifier: String(config.identifier).trim(), value: String(config.value).trim() }
    case 'SEND_COMMAND': {
      const result = { commandType: config.commandType, identifier: String(config.identifier).trim() }
      const params = String(config.params || '').trim()
      if (params) result.params = params
      return result
    }
    case 'FORWARD_MQTT': {
      const result = { topic: String(config.topic).trim(), qos: config.qos ?? 1 }
      if (String(config.payloadTemplate || '').trim()) result.payloadTemplate = config.payloadTemplate
      return result
    }
    default: {
      const result = { url: String(config.url).trim(), method: config.method || 'POST' }
      const headers = parseJsonObject(config.headers, '请求头')
      if (headers.value) result.headers = headers.value
      if (String(config.payloadTemplate || '').trim()) result.payloadTemplate = config.payloadTemplate
      return result
    }
  }
}

function buildStepPayload(step, index) {
  const targetType = isForwardAction(step.actionType)
    ? 'TRIGGER'
    : timerSource.value
      ? 'FIXED'
      : step.targetType
  return {
    seq: index + 1,
    delaySeconds: Number(step.delaySeconds ?? 0),
    actionType: step.actionType,
    targetType,
    targetConfig: targetType === 'FIXED' ? { ...step.targetConfig } : null,
    actionConfig: buildStepActionConfig(step),
    enabled: Boolean(step.enabled)
  }
}

function buildPayload() {
  const payload = {
    name: form.name.trim(),
    description: form.description.trim() || null,
    triggerType: form.triggerType,
    triggerDeviceId: form.triggerType === 'TIMER' ? null : form.triggerDeviceId ?? null,
    triggerIdentifier: form.triggerType === 'TIMER' ? null : form.triggerIdentifier.trim(),
    triggerOperator: null,
    triggerThreshold: null,
    triggerEventType: null,
    timerCron: null,
    conditionLogic: form.conditions.length ? form.conditionLogic : null,
    conditions: form.conditions.map((condition) => ({
      deviceId: condition.deviceId ?? null,
      identifier: String(condition.identifier).trim(),
      operator: condition.operator,
      threshold: String(condition.threshold).trim()
    })),
    cooldownSeconds: form.cooldownSeconds ?? 0,
    enabled: Boolean(form.enabled),
    steps: form.steps.map(buildStepPayload)
  }
  if (form.triggerType === 'PROPERTY' && form.thresholdEnabled) {
    payload.triggerOperator = form.triggerOperator
    payload.triggerThreshold = String(form.triggerThreshold).trim()
  }
  if (form.triggerType === 'EVENT') {
    payload.triggerEventType = form.triggerEventType || null
  }
  if (form.triggerType === 'TIMER') {
    payload.timerCron = String(form.timerCron).trim()
  }
  return payload
}

async function handleSubmit() {
  const error = validateForm()
  if (error) {
    ElMessage.warning(error)
    return
  }
  const payload = buildPayload()
  try {
    if (editingId.value) {
      await updateScene(editingId.value, payload)
      ElMessage.success('场景已更新')
    } else {
      await createScene(payload)
      ElMessage.success('场景已创建')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    // 具体失败原因已由 axios 拦截器统一提示
  }
}

function openTestDialog() {
  testVisible.value = true
}

onMounted(() => {
  productsQuery.refetch?.()
})
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

.scene-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-control {
  width: 100%;
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
</style>
