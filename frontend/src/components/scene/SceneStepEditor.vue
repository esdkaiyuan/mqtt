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
        <div
          v-if="!isFlowStep(step.actionType) && parallelInfo(index)"
          class="step-editor__field step-editor__field--branch"
        >
          <span class="step-editor__label">并行分支</span>
          <el-select
            :model-value="branchValue(step)"
            class="step-editor__control"
            @change="(value) => handleBranchChange(step, value)"
          >
            <el-option
              v-for="branch in parallelInfo(index).branches"
              :key="`branch-${branch}`"
              :label="`分支 ${branch + 1}`"
              :value="branch"
            />
          </el-select>
        </div>
        <div v-if="!isFlowStep(step.actionType)" class="step-editor__field step-editor__field--target">
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

      <p v-if="isFlowStep(step.actionType)" class="step-editor__note">
        {{ flowStepNote(step.actionType) }}
      </p>
      <p v-else-if="isForwardAction(step.actionType)" class="step-editor__note">
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
        <template v-if="step.actionType === 'BRANCH'">
          <el-select
            v-model="step.actionConfig.deviceId"
            clearable
            filterable
            class="step-editor__config-item"
            placeholder="取值设备（默认触发设备）"
          >
            <el-option
              v-for="device in devices"
              :key="device.id"
              :label="device.deviceName || device.deviceKey"
              :value="device.id"
            />
          </el-select>
          <el-input
            v-model="step.actionConfig.identifier"
            class="step-editor__config-item"
            maxlength="64"
            placeholder="条件属性标识符，如 temperature"
          />
          <el-select v-model="step.actionConfig.operator" class="step-editor__config-item step-editor__config-item--operator">
            <el-option
              v-for="option in OPERATOR_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-model="step.actionConfig.threshold"
            class="step-editor__config-item"
            maxlength="255"
            placeholder="条件阈值，如 30"
          />
          <el-select
            :model-value="seqOrNull(step.actionConfig.trueSeq)"
            clearable
            class="step-editor__config-item"
            placeholder="条件为真：选择跳转步骤"
            @update:model-value="(value) => (step.actionConfig.trueSeq = seqOrNull(value))"
          >
            <el-option label="结束执行" :value="null" />
            <el-option
              v-for="seq in forwardSeqOptions(index)"
              :key="`true-${seq}`"
              :label="`第 ${seq} 步`"
              :value="seq"
            />
          </el-select>
          <el-select
            :model-value="seqOrNull(step.actionConfig.falseSeq)"
            clearable
            class="step-editor__config-item"
            placeholder="条件为假：选择跳转步骤"
            @update:model-value="(value) => (step.actionConfig.falseSeq = seqOrNull(value))"
          >
            <el-option label="结束执行" :value="null" />
            <el-option
              v-for="seq in forwardSeqOptions(index)"
              :key="`false-${seq}`"
              :label="`第 ${seq} 步`"
              :value="seq"
            />
          </el-select>
        </template>

        <template v-else-if="step.actionType === 'LOOP'">
          <el-select
            :model-value="seqOrNull(step.actionConfig.startSeq)"
            class="step-editor__config-item"
            placeholder="循环体起始步骤"
            @update:model-value="(value) => (step.actionConfig.startSeq = seqOrNull(value))"
          >
            <el-option
              v-for="seq in bodyStartOptions(index)"
              :key="`loop-${seq}`"
              :label="`从第 ${seq} 步开始`"
              :value="seq"
            />
          </el-select>
          <div class="step-editor__field step-editor__field--loop-times">
            <span class="step-editor__label">循环次数</span>
            <el-input-number
              v-model="step.actionConfig.times"
              :min="1"
              :max="MAX_LOOP_TIMES"
              controls-position="right"
            />
          </div>
        </template>

        <template v-else-if="step.actionType === 'PARALLEL'">
          <div class="step-editor__field step-editor__field--parallel-branches">
            <span class="step-editor__label">并行分支数</span>
            <el-input-number
              v-model="step.actionConfig.branches"
              :min="MIN_PARALLEL_BRANCHES"
              :max="MAX_PARALLEL_BRANCHES"
              controls-position="right"
              @change="syncMembership"
            />
          </div>
          <p class="step-editor__note">
            请为后续步骤选择所属分支，并在所有分支结束后添加「并行汇合」。
          </p>
        </template>

        <template v-else-if="step.actionType === 'JOIN'">
          <p class="step-editor__note">并行汇合不执行动作，所有分支完成后继续后续步骤。</p>
        </template>

        <template v-else-if="step.actionType === 'UPDATE_PROPERTY'">
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
import {
  ACTION_TYPE_OPTIONS,
  OPERATOR_OPTIONS,
  TARGET_TYPE_OPTIONS,
  isFlowStep,
  isForwardAction,
  createTargetConfig,
  createActionConfig,
  createStep,
  normalizeStep,
  normalizeParallelMembership,
  parallelInfoAt,
  remapFlowRefsAfterMove,
  remapFlowRefsAfterRemove,
  seqOrNull,
  withNormalizedSeq,
  MAX_PARALLEL_BRANCHES,
  MIN_PARALLEL_BRANCHES
} from '@/utils/scene'

/** 与后端 app.rule 限制保持一致：单场景步骤数与单步最大延时（设计文档 §12）。 */
const MAX_STEPS = 20
const MAX_DELAY_SECONDS = 86400
/** 与后端 SceneConstants.MAX_LOOP_TIMES 保持一致。 */
const MAX_LOOP_TIMES = 20

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

/** 转发类动作目标锁 TRIGGER；TIMER 源下其余动作锁 FIXED；分支 / 循环无目标。 */
function effectiveTargetType(step) {
  if (isFlowStep(step.actionType)) return 'NONE'
  if (isForwardAction(step.actionType)) return 'TRIGGER'
  if (props.timerSource) return 'FIXED'
  return step.targetType
}

function targetTypeLocked(step) {
  return isFlowStep(step.actionType) || isForwardAction(step.actionType) || props.timerSource
}

/** 分支只能向后跳转：可选项为当前步骤之后、已启用的步骤序号。 */
function forwardSeqOptions(index) {
  return list.value
    .map((item, position) => ({ seq: position + 1, enabled: item.enabled }))
    .slice(index + 1)
    .filter((item) => item.enabled !== false)
    .map((item) => item.seq)
}

/** 循环体必须位于循环步骤之前：可选项为当前步骤之前、已启用的步骤序号。 */
function bodyStartOptions(index) {
  return list.value
    .map((item, position) => ({ seq: position + 1, enabled: item.enabled }))
    .slice(0, index)
    .filter((item) => item.enabled !== false)
    .map((item) => item.seq)
}

function addStep() {
  if (list.value.length >= MAX_STEPS) return
  syncMembership(withNormalizedSeq([...list.value, createStep(props.timerSource)]))
}

function removeStep(index) {
  const next = [...list.value]
  remapFlowRefsAfterRemove(next, index)
  syncMembership(withNormalizedSeq(next.filter((_, i) => i !== index)))
}

function moveStep(index, offset) {
  const target = index + offset
  if (target < 0 || target >= list.value.length) return
  const next = [...list.value]
  const [moved] = next.splice(index, 1)
  next.splice(target, 0, moved)
  remapFlowRefsAfterMove(next, index, target)
  syncMembership(withNormalizedSeq(next))
}

function handleActionTypeChange(step, actionType) {
  step.actionType = actionType
  step.actionConfig = createActionConfig(actionType)
  normalizeStep(step, props.timerSource)
  syncMembership()
}

function parallelInfo(index) {
  return parallelInfoAt(list.value, index)
}

function branchValue(step) {
  return Number.isInteger(step.branchIndex) ? step.branchIndex : 0
}

function handleBranchChange(step, branchIndex) {
  step.branchIndex = branchIndex
}

function syncMembership(items = list.value) {
  steps.value = normalizeParallelMembership([...items])
  return steps.value
}

function flowStepNote(actionType) {
  switch (actionType) {
    case 'BRANCH':
      return '分支步骤按属性条件选择后续步骤，不执行动作；目标只能向后跳转。'
    case 'LOOP':
      return '循环步骤标记循环体结束，重复执行其前的步骤；循环次数有上限。'
    case 'PARALLEL':
      return '并行开始把后续步骤分到多条分支并发执行，所有分支完成后由「并行汇合」继续。'
    case 'JOIN':
      return '并行汇合不执行动作，等待所有分支完成后继续后续步骤。'
    default:
      return ''
  }
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
    list.value.forEach((step) => normalizeStep(step, props.timerSource))
    syncMembership(withNormalizedSeq([...list.value]))
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

.step-editor__field--loop-times .el-input-number,
.step-editor__field--parallel-branches .el-input-number {
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
