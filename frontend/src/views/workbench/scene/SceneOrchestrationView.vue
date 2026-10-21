<template>
  <div class="scene-orch">
    <OrchestrationCanvas
      ref="canvasRef"
      :nodes="nodes"
      :edges="edges"
      :palette="palette"
      @node-add="handleNodeAdd"
      @node-remove="handleNodeRemove"
      @node-reorder="handleNodeReorder"
      @node-update="handleNodeUpdate"
    >
      <template #trigger="{ update }">
        <el-form label-position="top" class="orch-fields" @submit.prevent>
          <el-form-item label="触发方式">
            <el-select
              :model-value="form.triggerType"
              class="orch-field"
              @change="(value) => update({ triggerType: value })"
            >
              <el-option
                v-for="option in TRIGGER_SOURCE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <template v-if="form.triggerType === 'TIMER'">
            <el-form-item label="Cron 表达式（5 字段）">
              <el-input
                :model-value="form.timerCron"
                placeholder="如：*/5 * * * *"
                @update:model-value="(value) => update({ timerCron: value })"
              />
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="触发设备">
              <el-select
                :model-value="form.triggerDeviceId"
                clearable
                filterable
                class="orch-field"
                placeholder="全部设备"
                @update:model-value="(value) => update({ triggerDeviceId: value ?? null })"
              >
                <el-option
                  v-for="device in devices"
                  :key="device.id"
                  :label="device.deviceName || device.deviceKey"
                  :value="device.id"
                />
              </el-select>
            </el-form-item>

            <p v-if="statusTrigger" class="orch-hint">
              设备状态变化时触发；不选设备表示账号下任意设备。
            </p>

            <template v-else-if="shadowTrigger">
              <el-form-item label="变更侧">
                <el-select
                  :model-value="form.triggerShadowSide || 'REPORTED'"
                  class="orch-field"
                  @update:model-value="(value) => update({ triggerShadowSide: value })"
                >
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
                  :model-value="form.triggerIdentifier"
                  maxlength="64"
                  placeholder="留空表示任意属性变更"
                  @update:model-value="(value) => update({ triggerIdentifier: value })"
                />
              </el-form-item>
              <p class="orch-hint">
                影子属性值变化时触发；desired 仅外部写入触发，场景 / 规则内部写入不会自触发。
              </p>
            </template>

            <template v-else>
              <el-form-item :label="form.triggerType === 'EVENT' ? '事件标识符' : '属性标识符'">
                <el-input
                  :model-value="form.triggerIdentifier"
                  maxlength="64"
                  @update:model-value="(value) => update({ triggerIdentifier: value })"
                />
              </el-form-item>

              <template v-if="form.triggerType === 'PROPERTY'">
                <el-form-item label="触发判定">
                  <el-radio-group
                    :model-value="form.thresholdEnabled"
                    @change="(value) => update({ thresholdEnabled: value })"
                  >
                    <el-radio-button :value="false">任意上报即触发</el-radio-button>
                    <el-radio-button :value="true">满足阈值时触发</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <template v-if="form.thresholdEnabled">
                  <el-form-item label="比较符">
                    <el-select
                      :model-value="form.triggerOperator"
                      class="orch-field"
                      @update:model-value="(value) => update({ triggerOperator: value })"
                    >
                      <el-option
                        v-for="option in OPERATOR_OPTIONS"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="阈值">
                    <el-input
                      :model-value="form.triggerThreshold"
                      @update:model-value="(value) => update({ triggerThreshold: value })"
                    />
                  </el-form-item>
                </template>
              </template>

              <el-form-item v-else label="事件类型（可选）">
                <el-select
                  :model-value="form.triggerEventType"
                  clearable
                  class="orch-field"
                  placeholder="不限事件类型"
                  @update:model-value="(value) => update({ triggerEventType: value ?? '' })"
                >
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
        </el-form>
      </template>

      <template #conditionGroup="{ update }">
        <el-form label-position="top" class="orch-fields" @submit.prevent>
          <el-form-item label="条件逻辑">
            <el-radio-group
              :model-value="form.conditionLogic"
              @change="(value) => update({ conditionLogic: value })"
            >
              <el-radio-button
                v-for="option in CONDITION_LOGIC_OPTIONS"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </template>

      <template #condition="{ node, update }">
        <el-form v-if="conditionItem(node.id)" label-position="top" class="orch-fields" @submit.prevent>
          <el-form-item label="设备">
            <el-select
              :model-value="conditionItem(node.id).deviceId"
              clearable
              filterable
              class="orch-field"
              :placeholder="timerSource ? '请选择设备（必填）' : '触发设备（默认）'"
              @update:model-value="(value) => update({ deviceId: value ?? null })"
            >
              <el-option
                v-for="device in devices"
                :key="device.id"
                :label="device.deviceName || device.deviceKey"
                :value="device.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="标识符">
            <el-input
              :model-value="conditionItem(node.id).identifier"
              maxlength="64"
              @update:model-value="(value) => update({ identifier: value })"
            />
          </el-form-item>
          <el-form-item label="比较符">
            <el-select
              :model-value="conditionItem(node.id).operator"
              class="orch-field"
              @update:model-value="(value) => update({ operator: value })"
            >
              <el-option
                v-for="option in OPERATOR_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="阈值">
            <el-input
              :model-value="conditionItem(node.id).threshold"
              maxlength="255"
              @update:model-value="(value) => update({ threshold: value })"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #step="{ node, update }">
        <el-form v-if="stepItem(node.id)" label-position="top" class="orch-fields" @submit.prevent>
          <el-form-item label="延时（秒）">
            <el-input-number
              :model-value="stepItem(node.id).delaySeconds"
              :min="0"
              :max="MAX_DELAY_SECONDS"
              controls-position="right"
              @update:model-value="(value) => update({ delaySeconds: value ?? 0 })"
            />
          </el-form-item>

          <el-form-item label="动作类型">
            <el-select
              :model-value="stepItem(node.id).actionType"
              class="orch-field"
              @change="(value) => update({ actionType: value })"
            >
              <el-option
                v-for="option in ACTION_TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="!isFlowStep(stepItem(node.id).actionType)" label="目标">
            <el-select
              :model-value="effectiveTargetType(stepItem(node.id))"
              class="orch-field"
              :disabled="targetTypeLocked(stepItem(node.id))"
              @change="(value) => update({ targetType: value })"
            >
              <el-option
                v-for="option in TARGET_TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <template v-if="effectiveTargetType(stepItem(node.id)) === 'FIXED'">
            <el-form-item label="目标维度">
              <el-radio-group
                :model-value="stepItem(node.id).targetMode"
                size="small"
                @change="(value) => update({ targetMode: value, targetConfig: createTargetConfig() })"
              >
                <el-radio-button value="device">指定设备</el-radio-button>
                <el-radio-button value="product">指定产品</el-radio-button>
                <el-radio-button value="group">指定分组</el-radio-button>
                <el-radio-button value="tag">指定标签</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="目标对象">
              <el-select
                v-if="stepItem(node.id).targetMode === 'device'"
                :model-value="targetConfigOf(node.id).deviceIds"
                multiple
                filterable
                class="orch-field"
                placeholder="可多选"
                @update:model-value="(value) => update(targetPatch(stepItem(node.id), 'deviceIds', value))"
              >
                <el-option
                  v-for="device in devices"
                  :key="device.id"
                  :label="device.deviceName || device.deviceKey"
                  :value="device.id"
                />
              </el-select>
              <el-select
                v-else-if="stepItem(node.id).targetMode === 'product'"
                :model-value="targetConfigOf(node.id).productIds"
                multiple
                filterable
                class="orch-field"
                placeholder="可多选"
                @update:model-value="(value) => update(targetPatch(stepItem(node.id), 'productIds', value))"
              >
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="product.name"
                  :value="product.id"
                />
              </el-select>
              <el-tree-select
                v-else-if="stepItem(node.id).targetMode === 'group'"
                :model-value="targetConfigOf(node.id).groupIds"
                :data="groupTree"
                :props="TREE_PROPS"
                node-key="id"
                multiple
                show-checkbox
                check-strictly
                :render-after-expand="false"
                default-expand-all
                class="orch-field"
                placeholder="可多选，含子分组"
                @update:model-value="(value) => update(targetPatch(stepItem(node.id), 'groupIds', value))"
              />
              <el-select
                v-else
                :model-value="targetConfigOf(node.id).tagIds"
                multiple
                class="orch-field"
                placeholder="可多选"
                @update:model-value="(value) => update(targetPatch(stepItem(node.id), 'tagIds', value))"
              >
                <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
              </el-select>
            </el-form-item>
          </template>

          <el-form-item
            v-if="!isFlowStep(stepItem(node.id).actionType) && parallelInfo(node.id)"
            label="并行分支"
          >
            <el-select
              :model-value="stepItem(node.id).branchIndex ?? 0"
              class="orch-field"
              @update:model-value="(value) => update({ branchIndex: value })"
            >
              <el-option
                v-for="branch in parallelInfo(node.id).branches"
                :key="`branch-${branch}`"
                :label="`分支 ${branch + 1}`"
                :value="branch"
              />
            </el-select>
          </el-form-item>

          <el-form-item :label="isFlowStep(stepItem(node.id).actionType) ? '流程配置' : '动作配置'">
            <template v-if="stepItem(node.id).actionType === 'PARALLEL'">
              <el-form-item label="并行分支数">
                <el-input-number
                  :model-value="stepItem(node.id).actionConfig.branches"
                  :min="MIN_PARALLEL_BRANCHES"
                  :max="MAX_PARALLEL_BRANCHES"
                  controls-position="right"
                  class="orch-field"
                  @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'branches', value ?? 2))"
                />
              </el-form-item>
              <p class="orch-hint">为后续步骤选择所属分支，并在所有分支结束后添加「并行汇合」。</p>
            </template>
            <template v-else-if="stepItem(node.id).actionType === 'JOIN'">
              <p class="orch-hint">并行汇合不执行动作，所有分支完成后继续后续步骤。</p>
            </template>
            <template v-else-if="stepItem(node.id).actionType === 'BRANCH'">
              <el-select
                :model-value="stepItem(node.id).actionConfig.deviceId"
                clearable
                filterable
                class="orch-field"
                placeholder="取值设备（默认触发设备）"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'deviceId', value || null))"
              >
                <el-option
                  v-for="device in devices"
                  :key="device.id"
                  :label="device.deviceName || device.deviceKey"
                  :value="device.id"
                />
              </el-select>
              <el-input
                :model-value="stepItem(node.id).actionConfig.identifier"
                class="orch-field"
                maxlength="64"
                placeholder="条件属性标识符，如 temperature"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'identifier', value))"
              />
              <el-select
                :model-value="stepItem(node.id).actionConfig.operator"
                class="orch-field"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'operator', value))"
              >
                <el-option
                  v-for="option in OPERATOR_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <el-input
                :model-value="stepItem(node.id).actionConfig.threshold"
                class="orch-field"
                maxlength="255"
                placeholder="条件阈值，如 30"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'threshold', value))"
              />
              <el-select
                :model-value="stepItem(node.id).actionConfig.trueSeq"
                clearable
                class="orch-field"
                placeholder="条件为真：选择跳转步骤"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'trueSeq', seqOrNull(value)))"
              >
                <el-option label="结束执行" :value="null" />
                <el-option
                  v-for="seq in forwardSeqOptions(node.id)"
                  :key="`true-${seq}`"
                  :label="`第 ${seq} 步`"
                  :value="seq"
                />
              </el-select>
              <el-select
                :model-value="stepItem(node.id).actionConfig.falseSeq"
                clearable
                class="orch-field"
                placeholder="条件为假：选择跳转步骤"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'falseSeq', seqOrNull(value)))"
              >
                <el-option label="结束执行" :value="null" />
                <el-option
                  v-for="seq in forwardSeqOptions(node.id)"
                  :key="`false-${seq}`"
                  :label="`第 ${seq} 步`"
                  :value="seq"
                />
              </el-select>
            </template>

            <template v-else-if="stepItem(node.id).actionType === 'LOOP'">
              <el-select
                :model-value="stepItem(node.id).actionConfig.startSeq"
                class="orch-field"
                placeholder="循环体起始步骤"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'startSeq', seqOrNull(value)))"
              >
                <el-option
                  v-for="seq in bodyStartOptions(node.id)"
                  :key="`loop-${seq}`"
                  :label="`从第 ${seq} 步开始`"
                  :value="seq"
                />
              </el-select>
              <el-input-number
                :model-value="stepItem(node.id).actionConfig.times"
                :min="1"
                :max="MAX_LOOP_TIMES"
                controls-position="right"
                class="orch-field"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'times', value ?? 1))"
              />
            </template>

            <template v-else-if="stepItem(node.id).actionType === 'UPDATE_PROPERTY'">
              <el-input
                :model-value="stepItem(node.id).actionConfig.identifier"
                class="orch-field"
                maxlength="64"
                placeholder="属性标识符，如 power"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'identifier', value))"
              />
              <el-input
                :model-value="stepItem(node.id).actionConfig.value"
                class="orch-field"
                maxlength="255"
                placeholder="属性值，支持 ${triggerValue} 占位符"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'value', value))"
              />
            </template>

            <template v-else-if="stepItem(node.id).actionType === 'SEND_COMMAND'">
              <el-select
                :model-value="stepItem(node.id).actionConfig.commandType"
                class="orch-field"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'commandType', value))"
              >
                <el-option label="属性设置" value="property_set" />
                <el-option label="服务调用" value="service" />
              </el-select>
              <el-input
                :model-value="stepItem(node.id).actionConfig.identifier"
                class="orch-field"
                maxlength="64"
                placeholder="命令标识符"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'identifier', value))"
              />
              <el-input
                :model-value="stepItem(node.id).actionConfig.params"
                class="orch-field"
                type="textarea"
                :rows="2"
                placeholder="命令参数 JSON 对象，如 {&quot;power&quot;:true}"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'params', value))"
              />
            </template>

            <template v-else-if="stepItem(node.id).actionType === 'FORWARD_MQTT'">
              <el-input
                :model-value="stepItem(node.id).actionConfig.topic"
                class="orch-field"
                maxlength="255"
                placeholder="目标 Topic，如 home/event"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'topic', value))"
              />
              <el-select
                :model-value="stepItem(node.id).actionConfig.qos"
                class="orch-field"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'qos', value))"
              >
                <el-option :value="0" label="QoS 0" />
                <el-option :value="1" label="QoS 1" />
                <el-option :value="2" label="QoS 2" />
              </el-select>
              <el-input
                :model-value="stepItem(node.id).actionConfig.payloadTemplate"
                class="orch-field"
                type="textarea"
                :rows="2"
                placeholder="载荷模板（留空使用默认载荷）"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'payloadTemplate', value))"
              />
            </template>

            <template v-else-if="stepItem(node.id).actionType === 'FORWARD_HTTP'">
              <el-select
                :model-value="stepItem(node.id).actionConfig.method"
                class="orch-field"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'method', value))"
              >
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
              </el-select>
              <el-input
                :model-value="stepItem(node.id).actionConfig.url"
                class="orch-field"
                maxlength="255"
                placeholder="https://example.com/hook"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'url', value))"
              />
              <el-input
                :model-value="stepItem(node.id).actionConfig.headers"
                class="orch-field"
                type="textarea"
                :rows="2"
                placeholder="请求头 JSON 对象（可留空）"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'headers', value))"
              />
              <el-input
                :model-value="stepItem(node.id).actionConfig.payloadTemplate"
                class="orch-field"
                type="textarea"
                :rows="2"
                placeholder="载荷模板（留空使用默认载荷）"
                @update:model-value="(value) => update(actionPatch(stepItem(node.id), 'payloadTemplate', value))"
              />
            </template>
          </el-form-item>

          <el-form-item label="启用">
            <el-switch
              :model-value="stepItem(node.id).enabled"
              @update:model-value="(value) => update({ enabled: value })"
            />
          </el-form-item>
        </el-form>
      </template>
    </OrchestrationCanvas>

    <p v-if="!hasSteps" class="scene-orch__empty">请至少添加一个执行步骤</p>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import {
  NODE_TYPE,
  ORCHESTRATION_KIND,
  isPaletteItemDisabled,
  paletteFor
} from '@/utils/orchestration'
import {
  ACTION_TYPE_OPTIONS,
  CONDITION_LOGIC_OPTIONS,
  EVENT_TYPE_OPTIONS,
  OPERATOR_OPTIONS,
  TARGET_TYPE_OPTIONS,
  TRIGGER_SOURCE_OPTIONS,
  SHADOW_SIDE_OPTIONS,
  createTargetConfig,
  isForwardAction,
  isFlowStep,
  isShadowTrigger,
  isStatusTrigger,
  parallelInfoAt,
  seqOrNull,
  MAX_PARALLEL_BRANCHES,
  MIN_PARALLEL_BRANCHES
} from '@/utils/scene'
import { applySceneEvent, projectScene } from './orchestration'

/**
 * 场景画布视图（T-29 设计文档 §10.1 / §10.3 / §10.4）。
 *
 * 画布是 `form` 的图形态投影：`projectScene(form)` 派生 `nodes` / `edges`，
 * 属性面板与节点拖拽事件经 `applySceneEvent` 就地写回 `form`，不持有第二份状态。
 */
const OrchestrationCanvas = defineAsyncComponent(
  () => import('@/components/orchestration/OrchestrationCanvas.vue')
)

const props = defineProps({
  form: {
    type: Object,
    required: true
  },
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
  }
})

/** 与后端 app.rule 限制保持一致：单步最大延时（场景设计文档 §12）。 */
const MAX_DELAY_SECONDS = 86400
/** 与后端 SceneConstants.MAX_LOOP_TIMES 保持一致。 */
const MAX_LOOP_TIMES = 20
/** 分组树选择器字段映射（与设备列表保持一致）。 */
const TREE_PROPS = { label: 'name', children: 'children' }

const canvasRef = ref(null)

const timerSource = computed(() => props.form.triggerType === 'TIMER')
const statusTrigger = computed(() => isStatusTrigger(props.form.triggerType))
const shadowTrigger = computed(() => isShadowTrigger(props.form.triggerType))
const hasSteps = computed(() => Array.isArray(props.form.steps) && props.form.steps.length > 0)

const nodes = computed(() => projectScene(props.form).nodes)
const edges = computed(() => projectScene(props.form).edges)

const palette = computed(() =>
  paletteFor(ORCHESTRATION_KIND.SCENE).map((item) => ({
    ...item,
    disabled: isPaletteItemDisabled(item, props.form),
    // 无步骤时高亮「步骤」项（设计文档 §10.4）
    highlight: item.type === NODE_TYPE.STEP && !hasSteps.value
  }))
)

function parseIndex(id, prefix) {
  if (typeof id !== 'string' || !id.startsWith(`${prefix}-`)) return -1
  const raw = id.slice(prefix.length + 1)
  return /^\d+$/.test(raw) ? Number(raw) : -1
}

/** 由节点 id 解析出对应的 `form` 项，属性面板直接读写这份响应式数据。 */
function conditionItem(id) {
  const index = parseIndex(id, NODE_TYPE.CONDITION)
  return index >= 0 ? (props.form.conditions?.[index] ?? null) : null
}

function stepItem(id) {
  const index = parseIndex(id, NODE_TYPE.STEP)
  return index >= 0 ? (props.form.steps?.[index] ?? null) : null
}

/** 分支只能向后跳转：可选项为当前步骤之后、已启用的步骤序号。 */
function forwardSeqOptions(id) {
  const index = parseIndex(id, NODE_TYPE.STEP)
  if (index < 0) return []
  return (props.form.steps || [])
    .map((item, position) => ({ seq: position + 1, enabled: item.enabled }))
    .slice(index + 1)
    .filter((item) => item.enabled !== false)
    .map((item) => item.seq)
}

/** 循环体必须位于循环步骤之前：可选项为当前步骤之前、已启用的步骤序号。 */
function bodyStartOptions(id) {
  const index = parseIndex(id, NODE_TYPE.STEP)
  if (index < 0) return []
  return (props.form.steps || [])
    .map((item, position) => ({ seq: position + 1, enabled: item.enabled }))
    .slice(0, index)
    .filter((item) => item.enabled !== false)
    .map((item) => item.seq)
}

function targetConfigOf(id) {
  return stepItem(id)?.targetConfig ?? createTargetConfig()
}

function parallelInfo(id) {
  const index = parseIndex(id, NODE_TYPE.STEP)
  if (index < 0) return null
  return parallelInfoAt(props.form.steps || [], index)
}

/** 分支 / 循环无目标；转发类动作目标锁 TRIGGER；TIMER 源下其余动作锁 FIXED（与 SceneStepEditor 同口径）。 */
function effectiveTargetType(step) {
  if (!step) return 'TRIGGER'
  if (isFlowStep(step.actionType)) return 'NONE'
  if (isForwardAction(step.actionType)) return 'TRIGGER'
  if (timerSource.value) return 'FIXED'
  return step.targetType
}

function targetTypeLocked(step) {
  if (!step) return true
  return isFlowStep(step.actionType) || isForwardAction(step.actionType) || timerSource.value
}

function actionPatch(step, key, value) {
  return { actionConfig: { ...step.actionConfig, [key]: value } }
}

function targetPatch(step, key, value) {
  return { targetConfig: { ...(step.targetConfig || createTargetConfig()), [key]: value } }
}

function handleNodeAdd(event) {
  applySceneEvent(props.form, { type: 'node-add', nodeType: event.type })
}

function handleNodeRemove({ id }) {
  applySceneEvent(props.form, { type: 'node-remove', id })
}

function handleNodeReorder({ id, position }) {
  applySceneEvent(props.form, { type: 'node-reorder', id, position })
}

function handleNodeUpdate({ id, patch }) {
  applySceneEvent(props.form, { type: 'node-update', id, patch })
}

/** 校验文案 → 节点 id（设计文档 §10.3）：`第 N 个条件` / `第 N 步`。 */
const ERROR_NODE_PATTERNS = [
  { pattern: /第\s*(\d+)\s*个条件/, prefix: NODE_TYPE.CONDITION },
  { pattern: /第\s*(\d+)\s*步/, prefix: NODE_TYPE.STEP }
]

/**
 * 把 `validateForm` 的错误文案定位到画布节点：选中并居中。
 * 返回是否命中节点，未命中时调用方无需额外提示。
 */
function focusError(message) {
  if (!message) return false
  for (const { pattern, prefix } of ERROR_NODE_PATTERNS) {
    const match = pattern.exec(String(message))
    if (match) {
      const index = Number(match[1]) - 1
      if (index < 0) return false
      return canvasRef.value?.focusNode?.(`${prefix}-${index}`) ?? false
    }
  }
  return false
}

defineExpose({ focusError })
</script>

<style scoped>
.scene-orch {
  position: relative;
  height: 100%;
  min-height: 480px;
}

.orch-fields {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.orch-fields :deep(.el-form-item) {
  margin-bottom: var(--spacing-sm);
}

.orch-field {
  width: 100%;
}

.orch-hint {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.scene-orch__empty {
  position: absolute;
  left: 50%;
  bottom: var(--spacing-md);
  transform: translateX(-50%);
  margin: 0;
  padding: var(--spacing-xs) var(--spacing-md);
  border: 1px solid var(--color-warning);
  border-radius: var(--border-radius-sm);
  background: var(--color-warning-light);
  font-size: var(--font-size-xs);
  color: var(--color-warning);
  pointer-events: none;
}
</style>