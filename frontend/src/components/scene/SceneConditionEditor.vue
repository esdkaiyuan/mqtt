<template>
  <div class="condition-editor">
    <div class="condition-editor__head">
      <span class="condition-editor__title">条件组</span>
      <el-button link type="primary" @click="addCondition">
        <svg-icon name="add" :size="14" />
        新增条件
      </el-button>
    </div>

    <div class="condition-editor__logic">
      <el-radio-group v-model="logic" size="small">
        <el-radio-button
          v-for="option in CONDITION_LOGIC_OPTIONS"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <p v-if="list.length === 0" class="condition-editor__hint">
      未配置附加条件，触发匹配后直接执行步骤。
    </p>

    <div v-for="(condition, index) in list" :key="index" class="condition-editor__item">
      <div class="condition-editor__row">
        <el-select
          v-model="condition.deviceId"
          class="condition-editor__device"
          :clearable="!timerSource"
          filterable
          :placeholder="timerSource ? '请选择设备（必填）' : '触发设备（默认）'"
        >
          <el-option
            v-for="device in devices"
            :key="device.id"
            :label="device.deviceName || device.deviceKey"
            :value="device.id"
          />
        </el-select>
        <el-input
          v-model="condition.identifier"
          class="condition-editor__id"
          maxlength="64"
          placeholder="标识符"
        />
        <el-select v-model="condition.operator" class="condition-editor__operator">
          <el-option
            v-for="option in OPERATOR_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="condition.threshold"
          class="condition-editor__threshold"
          maxlength="255"
          placeholder="阈值"
        />
        <el-button link type="danger" @click="removeCondition(index)">删除</el-button>
      </div>
      <p v-if="timerSource && !condition.deviceId" class="condition-editor__item-hint">
        定时触发下条件设备必填。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import SvgIcon from '@/components/Icon.vue'
import { CONDITION_LOGIC_OPTIONS, OPERATOR_OPTIONS } from '@/utils/scene'

defineProps({
  devices: {
    type: Array,
    default: () => []
  },
  // 触发源为 TIMER 时没有触发设备，条件项设备必填（设计文档 §7.3）
  timerSource: {
    type: Boolean,
    default: false
  }
})

const conditions = defineModel({ type: Array, required: true })
const logic = defineModel('logic', { type: String, default: 'AND' })

const list = computed(() => conditions.value || [])

function createCondition() {
  return { deviceId: null, identifier: '', operator: 'GT', threshold: '' }
}

function addCondition() {
  conditions.value = [...list.value, createCondition()]
}

function removeCondition(index) {
  conditions.value = list.value.filter((_, i) => i !== index)
}
</script>

<style scoped>
.condition-editor {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.condition-editor__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.condition-editor__title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.condition-editor__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.condition-editor__item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-card);
}

.condition-editor__row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.condition-editor__device {
  width: 180px;
}

.condition-editor__id {
  flex: 1;
  min-width: 120px;
}

.condition-editor__operator {
  width: 130px;
}

.condition-editor__threshold {
  width: 120px;
}

.condition-editor__item-hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-warning);
}
</style>
