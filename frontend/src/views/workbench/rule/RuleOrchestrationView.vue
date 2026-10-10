<template>
  <div class="rule-orch rule-orchestration-view">
    <OrchestrationCanvas
      ref="canvasRef"
      :nodes="nodes"
      :edges="edges"
      @node-update="handleNodeUpdate"
    >
      <template #trigger="{ update }">
        <el-form label-position="top" class="rule-orch__fields" @submit.prevent>
          <el-form-item label="触发源">
            <el-select
              :model-value="form.sourceType"
              class="rule-orch__field"
              @change="(value) => update({ sourceType: value })"
            >
              <el-option
                v-for="item in SOURCE_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="form.sourceType === 'EVENT' ? '事件标识符' : '属性标识符'">
            <el-input
              :model-value="form.identifier"
              maxlength="64"
              :placeholder="form.sourceType === 'EVENT' ? '如：overtemp' : '如：temperature'"
              @update:model-value="(value) => update({ identifier: value })"
            />
          </el-form-item>
          <template v-if="form.sourceType === 'PROPERTY'">
            <el-form-item label="比较符">
              <el-select
                :model-value="form.operator"
                class="rule-orch__field"
                @update:model-value="(value) => update({ operator: value })"
              >
                <el-option
                  v-for="item in OPERATOR_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="阈值">
              <el-input
                :model-value="form.thresholdValue"
                placeholder="如：40"
                @update:model-value="(value) => update({ thresholdValue: value })"
              />
            </el-form-item>
          </template>
          <el-form-item v-else label="事件类型（可选）">
            <el-select
              :model-value="form.eventType"
              clearable
              class="rule-orch__field"
              placeholder="全部事件类型"
              @update:model-value="(value) => update({ eventType: value ?? '' })"
            >
              <el-option
                v-for="item in EVENT_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </template>

      <template #scope="{ update }">
        <el-form label-position="top" class="rule-orch__fields" @submit.prevent>
          <el-form-item label="作用范围">
            <el-select
              :model-value="form.scopeType"
              class="rule-orch__field"
              @change="(value) => update({ scopeType: value })"
            >
              <el-option
                v-for="item in SCOPE_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.scopeType === 'DEVICE'" label="作用设备" required>
            <el-select
              :model-value="form.deviceId"
              clearable
              filterable
              class="rule-orch__field"
              placeholder="选择设备"
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
          <el-form-item v-else-if="form.scopeType === 'GROUP'" label="作用分组" required>
            <el-select
              :model-value="form.groupIds"
              multiple
              filterable
              clearable
              class="rule-orch__field"
              placeholder="选择分组"
              @update:model-value="(value) => update({ groupIds: value || [] })"
            >
              <el-option v-for="group in groups" :key="group.id" :label="group.name" :value="group.id" />
            </el-select>
            <p class="rule-orch__hint">按分组作用时，含子分组的设备一并生效</p>
          </el-form-item>
          <el-form-item v-else-if="form.scopeType === 'TAG'" label="作用标签" required>
            <el-select
              :model-value="form.tagIds"
              multiple
              filterable
              clearable
              class="rule-orch__field"
              placeholder="选择标签"
              @update:model-value="(value) => update({ tagIds: value || [] })"
            >
              <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>

      <template #action="{ update }">
        <el-form label-position="top" class="rule-orch__fields" @submit.prevent>
          <el-form-item label="动作类型">
            <el-select
              :model-value="form.actionType"
              class="rule-orch__field"
              @change="(value) => update({ actionType: value })"
            >
              <el-option
                v-for="item in ACTION_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <template v-if="form.actionType === 'UPDATE_PROPERTY'">
            <el-form-item label="目标属性" required>
              <el-input :model-value="form.targetIdentifier" @update:model-value="(value) => update({ targetIdentifier: value })" />
            </el-form-item>
            <el-form-item label="写入值" required>
              <el-input :model-value="form.targetValue" @update:model-value="(value) => update({ targetValue: value })" />
            </el-form-item>
          </template>

          <template v-else-if="form.actionType === 'SEND_COMMAND'">
            <el-form-item label="命令类型">
              <el-select
                :model-value="form.commandType"
                class="rule-orch__field"
                @update:model-value="(value) => update({ commandType: value })"
              >
                <el-option label="属性设置 (property_set)" value="property_set" />
                <el-option label="服务调用 (service)" value="service" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标标识符" required>
              <el-input :model-value="form.targetIdentifier" @update:model-value="(value) => update({ targetIdentifier: value })" />
            </el-form-item>
            <el-form-item label="命令参数（JSON，可选）">
              <el-input
                :model-value="form.commandParams"
                type="textarea"
                :rows="3"
                @update:model-value="(value) => update({ commandParams: value })"
              />
            </el-form-item>
          </template>

          <template v-else-if="form.actionType === 'FORWARD_MQTT'">
            <el-form-item label="目标 Topic" required>
              <el-input :model-value="form.topic" @update:model-value="(value) => update({ topic: value })" />
            </el-form-item>
            <el-form-item label="QoS">
              <el-select
                :model-value="form.qos"
                class="rule-orch__field"
                @update:model-value="(value) => update({ qos: value })"
              >
                <el-option v-for="value in [0, 1, 2]" :key="value" :label="String(value)" :value="value" />
              </el-select>
            </el-form-item>
            <el-form-item label="载荷模板（可选）">
              <el-input :model-value="form.payloadTemplate" @update:model-value="(value) => update({ payloadTemplate: value })" />
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="目标 URL" required>
              <el-input :model-value="form.url" @update:model-value="(value) => update({ url: value })" />
            </el-form-item>
            <el-form-item label="请求方法">
              <el-select
                :model-value="form.method"
                class="rule-orch__field"
                @update:model-value="(value) => update({ method: value })"
              >
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
              </el-select>
            </el-form-item>
            <el-form-item label="请求头（JSON，可选）">
              <el-input
                :model-value="form.headers"
                type="textarea"
                :rows="3"
                @update:model-value="(value) => update({ headers: value })"
              />
            </el-form-item>
            <el-form-item label="载荷模板（可选）">
              <el-input :model-value="form.payloadTemplate" @update:model-value="(value) => update({ payloadTemplate: value })" />
            </el-form-item>
          </template>
        </el-form>
      </template>
    </OrchestrationCanvas>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { applyRuleEvent, projectRule } from './orchestration'
import {
  ACTION_TYPE_OPTIONS,
  EVENT_TYPE_OPTIONS,
  OPERATOR_OPTIONS,
  SOURCE_TYPE_OPTIONS
} from '@/utils/rule'
import { SCOPE_TYPE_OPTIONS } from '@/utils/scope'

const OrchestrationCanvas = defineAsyncComponent(
  () => import('@/components/orchestration/OrchestrationCanvas.vue')
)

const props = defineProps({
  form: { type: Object, required: true },
  devices: { type: Array, default: () => [] },
  groups: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] }
})

const canvasRef = ref(null)
const candidates = computed(() => ({
  devices: props.devices,
  groups: props.groups,
  tags: props.tags
}))
const graph = computed(() => projectRule(props.form, candidates.value))
const nodes = computed(() => graph.value.nodes)
const edges = computed(() => graph.value.edges)

function handleNodeUpdate(event) {
  applyRuleEvent(props.form, { type: 'node-update', ...event })
}

function focusError(message) {
  const text = String(message || '')
  let nodeId = null
  if (/作用设备|作用分组|作用标签|作用范围/.test(text)) nodeId = 'scope'
  else if (/规则名称/.test(text)) return false
  else if (/标识符|阈值|比较符|事件类型/.test(text)) nodeId = 'trigger'
  else if (/目标|写入值|命令|Topic|URL|请求头/.test(text)) nodeId = 'action'
  return nodeId ? (canvasRef.value?.focusNode?.(nodeId) ?? false) : false
}

defineExpose({ focusError })
</script>

<style scoped>
.rule-orch {
  height: 100%;
  min-height: 480px;
}

.rule-orch__fields {
  width: 100%;
}

.rule-orch__field {
  width: 100%;
}

.rule-orch__hint {
  margin: var(--spacing-xs) 0 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
</style>
