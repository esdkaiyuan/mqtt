<template>
  <div class="step-editor">
    <div class="step-editor__head">
      <span class="step-editor__title">步骤流</span>
      <el-button link type="primary" :disabled="list.length >= MAX_STEPS" @click="addStep">
        <svg-icon name="add" :size="14" />
        新增步骤
      </el-button>
    </div>

    <p v-if="list.length === 0" class="step-editor__hint">请至少添加一个执行步骤。</p>
    <p v-else-if="list.length >= MAX_STEPS" class="step-editor__hint">
      每个场景最多 {{ MAX_STEPS }} 个步骤。
    </p>

    <div v-for="(step, index) in list" :key="index" class="step-editor__item">
      <div class="step-editor__item-head">
        <span class="step-editor__seq">步骤 {{ index + 1 }}</span>
        <div class="step-editor__item-actions">
          <el-button link :disabled="index === 0" @click="moveStep(index, -1)">上移</el-button>
          <el-button link :disabled="index === list.length - 1" @click="moveStep(index, 1)">
            下移
          </el-button>
          <el-button link type="danger" @click="removeStep(index)">删除</el-button>
        </div>
      </div>

      <div class="step-editor__row">
        <div class="step-editor__field step-editor__field--delay">
          <span class="step-editor__label">延时</span>
          <el-input-number
            v-model="step.delaySeconds"
            :min="0"
            :max="MAX_DELAY_SECONDS"
            :step="1"
            controls-position="right"
          />
          <span class="step-editor__unit">秒</span>
        </div>
        <div class="step-editor__field step-editor__field--action">
          <span class="step-editor__label">动作</span>
          <el-select
            :model-value="step.actionType"
            class="step-editor__control"
            @change="(value) => handleActionTypeChange(step, value)"
          >
            <el-option
              v-for="option in ACTION_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
        <div class="step-editor__field step-editor__field--target">
          <span class="step-editor__label">目标</span>
          <el-select
            :model-value="effectiveTargetType(step)"
            class="step-editor__control"
            :disabled="targetTypeLocked(step)"
            @change="(value) => handleTargetTypeChange(step, value)"
          >
            <el-option
              v-for="option in TARGET_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
        <div class="step-editor__field step-editor__field--enabled">
          <el-switch v-model="step.enabled" />
          <span class="step-editor__unit">启用</span>
        </div>
      </div>

      <p v-if="isForwardAction(step.actionType)" class="step-editor__note">
        转发类动作仅下发一次，目标固定为「触发设备」。
      </p>
      <p v-else-if="timerSource" class="step-editor__note">
        定时触发下无触发设备，目标必须指定具体设备。
      </p>

      <div v-if="effectiveTargetType(step) === 'FIXED'" class="step-editor__target">
        <el-radio-group
          :model-value="step.targetMode"
          size="small"
          @change="(value) => handleTargetModeChange(step, value)"
        >
          <el-radio-button value="device">指定设备</el-radio-button>
          <el-radio-button value="product">指定产品</el-radio-button>
          <el-radio-button value="group">指定分组</el-radio-button>
          <el-radio-button value="tag">指定标签</el-radio-button>
        </el-radio-group>

        <el-select
          v-if="step.targetMode === 'device'"
          v-model="step.targetConfig.deviceIds"
          multiple
          filterable
          class="step-editor__target-control"
          placeholder="可多选"
        >
          <el-option
            v-for="device in devices"
            :key="device.id"
            :label="device.deviceName || device.deviceKey"
            :value="device.id"
          />
        </el-select>
        <el-select
          v-else-if="step.targetMode === 'product'"
          v-model="step.targetConfig.productIds"
          multiple
          filterable
          class="step-editor__target-control"
          placeholder="可多选"
        >
          <el-option
            v-for="product in products"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
        <el-tree-select
          v-else-if="step.targetMode === 'group'"
          v-model="step.targetConfig.groupIds"
          :data="groupTree"
          :props="TREE_PROPS"
          node-key="id"
          multiple
          show-checkbox
          check-strictly
          :render-after-expand="false"
          default-expand-all
          class="step-editor__target-control"
          placeholder="可多选，含子分组"
        />
        <el-select
          v-else
          v-model="step.targetConfig.tagIds"
          multiple
          class="step-editor__target-control"
          placeholder="可多选"
        >
          <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
      </div>

      <div class="step-editor__config">
        <template v-if="step.actionType === 'UPDATE_PROPERTY'">
          <el-input
            v-model="step.actionConfig.identifier"
            class="step-editor__config-item"
            maxlength="64"
            placeholder="属性标识符，如 power"
          />
          <el-input
            v-model="step.actionConfig.value"
            class="step-editor__config-item"
            maxlength="255"
            placeholder="属性值，支持 ${triggerValue} 占位符"
          />
        </template>

        <template v-else-if="step.actionType === 'SEND_COMMAND'">
          <el-select v-model="step.actionConfig.commandType" class="step-editor__config-item">
            <el-option label="属性设置" value="property_set" />
            <el-option label="服务调用" value="service" />
          </el-select>
          <el-input
            v-model="step.actionConfig.identifier"
            class="step-editor__config-item"
            maxlength="64"
            placeholder="命令标识符"
          />
          <el-input
            v-model="step.actionConfig.params"
            class="step-editor__config-item"
            type="textarea"
            :rows="2"
            placeholder="命令参数 JSON 对象，如 {&quot;power&quot;:true}"
          />
        </template>

        <template v-else-if="step.actionType === 'FORWARD_MQTT'">
          <el-input
            v-model="step.actionConfig.topic"
            class="step-editor__config-item"
            maxlength="255"
            placeholder="目标 Topic，如 home/event"
          />
          <el-select v-model="step.actionConfig.qos" class="step-editor__config-item step-editor__config-item--qos">
            <el-option :value="0" label="QoS 0" />
            <el-option :value="1" label="QoS 1" />
            <el-option :value="2" label="QoS 2" />
          </el-select>
          <el-input
            v-model="step.actionConfig.payloadTemplate"
            class="step-editor__config-item"
            type="textarea"
            :rows="2"
            placeholder="载荷模板（留空使用默认载荷）"
          />
        </template>

        <template v-else-if="step.actionType === 'FORWARD_HTTP'">
          <el-select
            v-model="step.actionConfig.method"
            class="step-editor__config-item step-editor__config-item--method"
          >
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
          </el-select>
          <el-input
            v-model="step.actionConfig.url"
            class="step-editor__config-item"
            maxlength="255"
            placeholder="https://example.com/hook"
          />
          <el-input
            v-model="step.actionConfig.headers"
            class="step-editor__config-item"
            type="textarea"
            :rows="2"
            placeholder="请求头 JSON 对象（可留空），如 {&quot;X-Token&quot;:&quot;abc&quot;}"
          />
          <el-input
            v-model="step.actionConfig.payloadTemplate"
            class="step-editor__config-item"
            type="textarea"
            :rows="2"
            placeholder="载荷模板（留空使用默认载荷）"
          />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import SvgIcon from '@/components/Icon.vue'
import { ACTION_TYPE_OPTIONS, TARGET_TYPE_OPTIONS, isForwardAction } from '@/utils/scene'

/** 与后端 app.rule 限制保持一致：单场景步骤数与单步最大延时（设计文档 §12）。 */
const MAX_STEPS = 20
const MAX_DELAY_SECONDS = 86400

/** 分组树选择器字段映射（与设备列表保持一致）。 */
const TREE_PROPS = { label: 'name', children: 'children' }

const props = defineProps({
  devices: {
    type: Array,
    default: () => []
  },
  products: {
    type: Array,
    default: () => []
  },
  groupTree: {
    type: Array,
    default: () => []
  },
  tags: {
    type: Array,
    default: () => []
  },
  // 触发源为 TIMER 时没有触发设备，UPDATE_PROPERTY / SEND_COMMAND 目标必须为 FIXED（设计文档 §7.4）
  timerSource: {
    type: Boolean,
    default: false
  }
})

const steps = defineModel({ type: Array, required: true })

const list = computed(() => steps.value || [])

function createTargetConfig() {
  return { deviceIds: [], productIds: [], groupIds: [], tagIds: [] }
}

/** 各动作类型的 action_config 缺省形状（设计文档 §5.3）。 */
function createActionConfig(actionType) {
  switch (actionType) {
    case 'SEND_COMMAND':
      return { commandType: 'property_set', identifier: '', params: '' }
    case 'FORWARD_MQTT':
      return { topic: '', qos: 1, payloadTemplate: '' }
    case 'FORWARD_HTTP':
      return { url: '', method: 'POST', headers: '', payloadTemplate: '' }
    case 'UPDATE_PROPERTY':
    default:
      return { identifier: '', value: '' }
  }
}

function createStep() {
  return {
    seq: 0,
    delaySeconds: 0,
    actionType: 'UPDATE_PROPERTY',
    targetType: props.timerSource ? 'FIXED' : 'TRIGGER',
    targetMode: 'device',
    targetConfig: createTargetConfig(),
    actionConfig: createActionConfig('UPDATE_PROPERTY'),
    enabled: true
  }
}

/** 转发类动作目标锁 TRIGGER；TIMER 源下其余动作锁 FIXED。 */
function effectiveTargetType(step) {
  if (isForwardAction(step.actionType)) return 'TRIGGER'
  if (props.timerSource) return 'FIXED'
  return step.targetType
}

function targetTypeLocked(step) {
  return isForwardAction(step.actionType) || props.timerSource
}

/** 修正步骤的目标类型与 target_config，保证与触发源 / 动作类型约束一致。 */
function normalizeStep(step) {
  if (isForwardAction(step.actionType)) {
    step.targetType = 'TRIGGER'
    step.targetConfig = null
    return
  }
  if (!step.targetConfig || typeof step.targetConfig !== 'object') {
    step.targetConfig = createTargetConfig()
  }
  if (props.timerSource) {
    step.targetType = 'FIXED'
  }
  if (!step.targetMode) {
    step.targetMode = resolveTargetMode(step.targetConfig)
  }
}

/** 编辑既有场景时，按已选中的维度推断目标模式。 */
function resolveTargetMode(config) {
  if (!config) return 'device'
  if (config.productIds?.length) return 'product'
  if (config.groupIds?.length) return 'group'
  if (config.tagIds?.length) return 'tag'
  return 'device'
}

/** 步骤序号归一化为 1..N 连续，避免删除 / 移动后出现空洞（设计文档 §7.4）。 */
function withNormalizedSeq(items) {
  return items.map((step, index) => {
    step.seq = index + 1
    return step
  })
}

function addStep() {
  if (list.value.length >= MAX_STEPS) return
  steps.value = withNormalizedSeq([...list.value, createStep()])
}

function removeStep(index) {
  steps.value = withNormalizedSeq(list.value.filter((_, i) => i !== index))
}

function moveStep(index, offset) {
  const target = index + offset
  if (target < 0 || target >= list.value.length) return
  const next = [...list.value]
  const [moved] = next.splice(index, 1)
  next.splice(target, 0, moved)
  steps.value = withNormalizedSeq(next)
}

function handleActionTypeChange(step, actionType) {
  step.actionType = actionType
  step.actionConfig = createActionConfig(actionType)
  normalizeStep(step)
}

function handleTargetTypeChange(step, targetType) {
  step.targetType = targetType
  if (targetType === 'FIXED' && !step.targetConfig) {
    step.targetConfig = createTargetConfig()
  }
}

/** 切换目标模式时清空其他维度，保证 target_config 只有一个维度非空。 */
function handleTargetModeChange(step, mode) {
  step.targetMode = mode
  step.targetConfig = createTargetConfig()
}

watch(
  () => props.timerSource,
  () => {
    steps.value = withNormalizedSeq([...list.value])
    list.value.forEach((step) => normalizeStep(step))
  }
)
</script>

<style scoped>
.step-editor {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.step-editor__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.step-editor__title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.step-editor__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.step-editor__item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-card);
}

.step-editor__item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.step-editor__seq {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.step-editor__item-actions {
  display: flex;
  gap: var(--spacing-xs);
}

.step-editor__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-md);
}

.step-editor__field {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.step-editor__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.step-editor__control {
  width: 150px;
}

.step-editor__field--delay .el-input-number {
  width: 120px;
}

.step-editor__unit {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.step-editor__field--enabled {
  margin-left: auto;
}

.step-editor__note {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.step-editor__target {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  border-radius: var(--border-radius-sm);
  background: var(--color-bg-page);
}

.step-editor__target-control {
  width: 100%;
}

.step-editor__config {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.step-editor__config-item {
  width: 100%;
}

.step-editor__config-item--qos,
.step-editor__config-item--method {
  width: 140px;
}
</style>
