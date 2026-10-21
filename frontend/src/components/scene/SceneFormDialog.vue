<template>
  <el-drawer
    :model-value="modelValue"
    :size="drawerSize"
    :class="['scene-form-drawer', { 'scene-form-drawer--canvas': viewMode === 'canvas' }]"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <template #header>
      <div class="scene-form__header">
        <div class="scene-form__header-top">
          <span class="scene-form__title">{{ editingId ? '编辑场景' : '新建场景' }}</span>
          <el-radio-group v-model="viewMode" size="small" class="scene-form__view-toggle">
            <el-radio-button value="form">表单</el-radio-button>
            <el-radio-button value="canvas">画布</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 全局属性：非节点字段，表单 / 画布两视图共用（T-29 设计文档 §5.1） -->
        <div class="scene-form__global">
          <div class="scene-form__global-field scene-form__global-field--name">
            <span class="scene-form__global-label">场景名称 <em>*</em></span>
            <el-input v-model="form.name" maxlength="64" placeholder="如：回家开灯" />
          </div>
          <div class="scene-form__global-field">
            <span class="scene-form__global-label">描述</span>
            <el-input v-model="form.description" maxlength="255" placeholder="可选，说明场景的用途" />
          </div>
          <div class="scene-form__global-field scene-form__global-field--cooldown">
            <span class="scene-form__global-label">冷却窗口（秒）</span>
            <el-input-number
              v-model="form.cooldownSeconds"
              :min="0"
              :max="86400"
              controls-position="right"
            />
            <span class="scene-form__global-hint">0 表示不限制</span>
          </div>
          <div class="scene-form__global-field scene-form__global-field--enabled">
            <span class="scene-form__global-label">启用</span>
            <el-switch v-model="form.enabled" />
          </div>
        </div>
      </div>
    </template>

    <el-form v-if="viewMode === 'form'" label-position="top" class="scene-form" @submit.prevent>
      <div class="form-section-title">① 触发源</div>
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

        <p v-if="statusTrigger" class="form-hint form-hint--block">
          设备状态变化时触发（上线 = ONLINE，下线 = OFFLINE）；不选设备表示账号下任意设备。
        </p>

        <template v-else-if="shadowTrigger">
          <el-form-item label="变更侧" required>
            <el-select v-model="form.triggerShadowSide" class="form-control">
              <el-option
                v-for="option in SHADOW_SIDE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="属性标识符（可选）">
            <el-input
              v-model="form.triggerIdentifier"
              maxlength="64"
              placeholder="留空表示任意属性变更"
            />
          </el-form-item>
          <p class="form-hint form-hint--block">
            影子版本更新且属性值确有变化时触发；desired 变更仅控制台 / 开放 API / 上位机等外部写入触发，
            场景与规则内部写入不会自触发。
          </p>
        </template>

        <template v-else>
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
      </template>

      <div class="form-section-title">② 条件组</div>
      <SceneConditionEditor
        v-model="form.conditions"
        v-model:logic="form.conditionLogic"
        :devices="devices"
        :timer-source="timerSource"
      />

      <div class="form-section-title">③ 执行步骤</div>
      <SceneStepEditor
        v-model="form.steps"
        :devices="devices"
        :products="products"
        :group-tree="groupTree"
        :tags="tags"
        :timer-source="timerSource"
      />
    </el-form>

    <SceneOrchestrationView
      v-else
      ref="canvasViewRef"
      :form="form"
      :devices="devices"
      :products="products"
      :group-tree="groupTree"
      :tags="tags"
    />

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
    :trigger-shadow-side="form.triggerShadowSide"
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
import SceneOrchestrationView from '@/views/workbench/scene/SceneOrchestrationView.vue'
import { deviceApi } from '@/api/device'
import { productApi } from '@/api/product'
import { useDeviceGroupTreeQuery, useTagListQuery } from '@/composables/useDeviceGroups'
import { useSceneMutations } from '@/composables/useScenes'
import { unwrapResult } from '@/utils/result'
import {
  TRIGGER_SOURCE_OPTIONS,
  SHADOW_SIDE_OPTIONS,
  OPERATOR_OPTIONS,
  EVENT_TYPE_OPTIONS,
  isForwardAction,
  isFlowStep,
  isShadowTrigger,
  isStatusTrigger,
  normalizeParallelMembership,
  normalizeTriggerSource,
  parseJsonObject,
  seqOrNull,
  validateParallelStructure,
  MAX_PARALLEL_BRANCHES,
  MIN_PARALLEL_BRANCHES
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
/** 默认视图：既有表单视图（T-29 设计文档 §11）。 */
const DEFAULT_VIEW_MODE = 'form'

const { saving, createScene, updateScene } = useSceneMutations()

const editingId = computed(() => props.scene?.id ?? null)
const timerSource = computed(() => form.triggerType === 'TIMER')
const statusTrigger = computed(() => isStatusTrigger(form.triggerType))
const shadowTrigger = computed(() => isShadowTrigger(form.triggerType))

const viewMode = ref(DEFAULT_VIEW_MODE)
const canvasViewRef = ref(null)
const testVisible = ref(false)

/** 画布视图放宽抽屉宽度以容纳图形（T-29 设计文档 §10.1）。 */
const drawerSize = computed(() => (viewMode.value === 'canvas' ? 'min(1100px, 92vw)' : '640px'))

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
    triggerShadowSide: 'REPORTED',
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
    case 'BRANCH':
      actionConfig = {
        deviceId: config.deviceId ?? null,
        identifier: config.identifier || '',
        operator: config.operator || 'GT',
        threshold: config.threshold ?? '',
        trueSeq: seqOrNull(config.trueSeq),
        falseSeq: seqOrNull(config.falseSeq)
      }
      break
    case 'LOOP':
      actionConfig = {
        startSeq: seqOrNull(config.startSeq),
        times: config.times ?? 3
      }
      break
    case 'PARALLEL':
      actionConfig = { branches: Number(config.branches) || 2 }
      break
    case 'JOIN':
      actionConfig = {}
      break
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
    parallelSeq: step.parallelSeq ?? null,
    branchIndex: Number.isInteger(step.branchIndex) ? step.branchIndex : null,
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
    triggerShadowSide: scene.triggerShadowSide || 'REPORTED',
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
    if (visible) {
      fillForm(props.scene)
      // 每次打开回到默认表单视图；切换视图不重置 form（设计文档 §10.1 / §11）
      viewMode.value = DEFAULT_VIEW_MODE
    }
  },
  { immediate: true }
)

/** 切换触发方式时清理其他源专属字段，与画布触发节点面板共用同一口径（设计文档 §7.2 / §10.5）。 */
function handleTriggerTypeChange() {
  normalizeTriggerSource(form)
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
    case 'BRANCH': {
      if (!String(config.identifier || '').trim()) return `${label}：请填写分支条件属性标识符`
      if (!config.operator) return `${label}：请选择分支条件比较符`
      if (!String(config.threshold ?? '').trim()) return `${label}：请填写分支条件阈值`
      if (NUMERIC_OPERATORS.has(config.operator) && Number.isNaN(Number(config.threshold))) {
        return `${label}：数值比较符下分支条件阈值必须是数字`
      }
      const trueSeq = seqOrNull(config.trueSeq)
      const falseSeq = seqOrNull(config.falseSeq)
      if (trueSeq == null && falseSeq == null) {
        return `${label}：请至少设置一个分支跳转目标`
      }
      for (const [name, target] of [['为真', trueSeq], ['为假', falseSeq]]) {
        if (target == null) continue
        if (target <= index + 1) {
          return `${label}：条件${name}的跳转目标必须大于当前步骤`
        }
      }
      break
    }
    case 'LOOP': {
      const startSeq = seqOrNull(config.startSeq)
      if (startSeq == null || startSeq < 1 || startSeq > index) {
        return `${label}：请选择当前步骤之前的循环体起始步骤`
      }
      if (!Number.isInteger(config.times) || config.times < 1 || config.times > 20) {
        return `${label}：循环次数需在 1 ~ 20`
      }
      break
    }
    case 'PARALLEL': {
      const branches = Number(config.branches)
      if (!Number.isInteger(branches) || branches < MIN_PARALLEL_BRANCHES || branches > MAX_PARALLEL_BRANCHES) {
        return `${label}：并行分支数需在 ${MIN_PARALLEL_BRANCHES} ~ ${MAX_PARALLEL_BRANCHES}`
      }
      break
    }
    case 'JOIN':
      break
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
  normalizeParallelMembership(form.steps)
  for (let index = 0; index < form.steps.length; index += 1) {
    const step = form.steps[index]
    const label = `第 ${index + 1} 步`
    if (step.delaySeconds < 0 || step.delaySeconds > 86400) {
      return `${label}：延时需在 0 ~ 86400 秒`
    }
    const flowStep = isFlowStep(step.actionType)
    const targetType = flowStep
      ? 'NONE'
      : isForwardAction(step.actionType)
        ? 'TRIGGER'
        : timerSource.value
          ? 'FIXED'
          : step.targetType
    if (!flowStep && !isForwardAction(step.actionType) && timerSource.value && targetType !== 'FIXED') {
      return `${label}：定时触发下目标必须指定设备`
    }
    if (!flowStep && targetType === 'FIXED' && targetConfigIsEmpty(step.targetConfig)) {
      return `${label}：请至少选择一个目标`
    }
    const configError = validateStepActionConfig(step, index)
    if (configError) return configError
  }
  return validateParallelStructure(form.steps)
}

function validateForm() {
  if (!form.name.trim()) return '请填写场景名称'
  if (form.name.trim().length > 64) return '场景名称不能超过 64 个字符'
  if (form.triggerType === 'TIMER') {
    if (!String(form.timerCron || '').trim()) return '请填写 Cron 表达式'
    if (!isValidCron(form.timerCron)) return 'Cron 表达式需为合法 5 字段'
  } else if (shadowTrigger.value) {
    if (!['REPORTED', 'DESIRED', 'ANY'].includes(form.triggerShadowSide)) {
      return '请选择影子变更侧'
    }
    if (form.triggerIdentifier.trim().length > 64) return '属性标识符不能超过 64 个字符'
  } else if (!statusTrigger.value) {
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
    case 'BRANCH':
      return {
        deviceId: config.deviceId || null,
        identifier: String(config.identifier).trim(),
        operator: config.operator,
        threshold: String(config.threshold ?? '').trim(),
        trueSeq: seqOrNull(config.trueSeq),
        falseSeq: seqOrNull(config.falseSeq)
      }
    case 'LOOP':
      return { startSeq: seqOrNull(config.startSeq), times: Number(config.times) }
    case 'PARALLEL':
      return { branches: Number(config.branches) }
    case 'JOIN':
      return {}
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
  const flowStep = isFlowStep(step.actionType)
  const targetType = flowStep
    ? 'NONE'
    : isForwardAction(step.actionType)
      ? 'TRIGGER'
      : timerSource.value
        ? 'FIXED'
        : step.targetType
  return {
    seq: index + 1,
    delaySeconds: Number(step.delaySeconds ?? 0),
    actionType: step.actionType,
    targetType,
    targetConfig: !flowStep && targetType === 'FIXED' ? { ...step.targetConfig } : null,
    actionConfig: buildStepActionConfig(step),
    parallelSeq: flowStep ? null : step.parallelSeq ?? null,
    branchIndex: flowStep ? null : step.branchIndex ?? null,
    enabled: Boolean(step.enabled)
  }
}

function buildPayload() {
  normalizeParallelMembership(form.steps)
  const payload = {
    name: form.name.trim(),
    description: form.description.trim() || null,
    triggerType: form.triggerType,
    triggerDeviceId: form.triggerType === 'TIMER' ? null : form.triggerDeviceId ?? null,
    triggerIdentifier:
      form.triggerType === 'TIMER' || statusTrigger.value
        ? null
        : shadowTrigger.value
          ? (form.triggerIdentifier.trim() || null)
          : form.triggerIdentifier.trim(),
    triggerShadowSide: shadowTrigger.value ? form.triggerShadowSide : null,
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
    // 画布视图下把「第 N 个条件 / 第 N 步」错误定位到对应节点（设计文档 §10.3）
    if (viewMode.value === 'canvas') canvasViewRef.value?.focusError?.(error)
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
.scene-form__header {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.scene-form__header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.scene-form__title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.scene-form__global {
  display: grid;
  grid-template-columns: 1.4fr 2fr 1fr auto;
  gap: var(--spacing-md);
  align-items: start;
}

.scene-form__global-field {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xxs, 4px);
  min-width: 0;
}

.scene-form__global-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.scene-form__global-label em {
  color: var(--color-danger);
  font-style: normal;
}

.scene-form__global-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.scene-form__global-field--cooldown :deep(.el-input-number),
.scene-form__global-field--cooldown :deep(.el-input) {
  width: 100%;
}

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

.form-hint--block {
  margin: 0 0 var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius);
}

/* 画布视图：内容区撑满抽屉，交由画布自身滚动 / 缩放 */
.scene-form-drawer--canvas :deep(.el-drawer__body) {
  overflow: hidden;
}
</style>