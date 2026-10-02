<template>
  <div class="device-shadow-panel">
    <div class="device-shadow-panel__head">
      <div class="device-shadow-panel__intro">
        <h3 class="card-title">设备影子</h3>
        <p class="card-desc">
          云端保存的 desired / reported / delta 三份状态，版本号单调递增，delta 收敛即清空
        </p>
      </div>
      <div class="device-shadow-panel__meta">
        <el-tag size="small" type="info">version {{ shadow.version ?? 0 }}</el-tag>
        <span class="device-shadow-panel__updated">{{ updatedLabel }}</span>
      </div>
    </div>

    <el-alert
      v-if="!loading && !shadow.modeled"
      type="warning"
      :closable="false"
      show-icon
      title="该产品未定义物模型"
      description="影子不可用，请先到产品页为所属产品定义属性。"
    />

    <template v-else>
      <el-alert
        v-if="!online"
        type="info"
        :closable="false"
        show-icon
        title="设备离线"
        description="期望值已保存到影子，设备上线后自动下发。"
      />

      <div class="device-shadow-panel__grid">
        <section
          v-for="column in COLUMNS"
          :key="column.key"
          class="shadow-column"
          :class="`shadow-column--${column.key}`"
        >
          <header class="shadow-column__head">
            <span class="shadow-column__title">{{ column.title }}</span>
            <span class="shadow-column__count">{{ keysOf(column.key).length }}</span>
          </header>
          <ul v-if="keysOf(column.key).length" class="shadow-column__list">
            <li
              v-for="key in keysOf(column.key)"
              :key="key"
              class="shadow-row"
              :class="{ 'shadow-row--delta': column.key !== 'delta' && hasDelta(key) }"
            >
              <span class="shadow-row__key">{{ key }}</span>
              <span class="shadow-row__value">{{ mapOf(column.key)[key] }}</span>
            </li>
          </ul>
          <p v-else class="shadow-column__empty">{{ column.empty }}</p>
        </section>
      </div>

      <div v-if="fields.length" class="device-shadow-panel__setter">
        <h4 class="device-shadow-panel__setter-title">设置期望值</h4>
        <el-form label-position="top" class="device-shadow-panel__form" @submit.prevent>
          <el-form-item v-for="field in fields" :key="field.identifier" :label="field.identifier">
            <el-input-number
              v-if="field.control === 'number'"
              v-model="values[field.identifier]"
              :min="field.min ?? undefined"
              :max="field.max ?? undefined"
              :step="field.integer ? 1 : 0.1"
              :precision="field.integer ? 0 : undefined"
              controls-position="right"
            />
            <el-switch v-else-if="field.control === 'switch'" v-model="values[field.identifier]" />
            <el-select
              v-else-if="field.control === 'select'"
              v-model="values[field.identifier]"
              placeholder="请选择"
            >
              <el-option v-for="key in field.enumKeys" :key="key" :value="key" :label="key" />
            </el-select>
            <el-date-picker
              v-else-if="field.control === 'date'"
              v-model="values[field.identifier]"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择时间"
            />
            <el-input
              v-else-if="field.control === 'json'"
              v-model="values[field.identifier]"
              type="textarea"
              :rows="3"
              :placeholder="JSON_PLACEHOLDER"
            />
            <el-input v-else v-model="values[field.identifier]" placeholder="请输入" />
          </el-form-item>
        </el-form>
      </div>

      <div class="device-shadow-panel__footer">
        <el-button v-if="deltaKeys.length" :loading="setting" @click="resendDelta">
          按 delta 补发（{{ deltaKeys.length }}）
        </el-button>
        <el-button v-if="fields.length" type="primary" :loading="setting" @click="submit">
          下发期望值
        </el-button>
        <p v-if="!fields.length && !deltaKeys.length" class="device-shadow-panel__empty">
          物模型中没有可写（rw）属性
        </p>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { buildParams, initialValues, propertyFields, restoreParams } from '@/utils/commandForm'

const props = defineProps({
  shadow: { type: Object, required: true },
  capability: { type: Object, default: () => ({ properties: [] }) },
  online: { type: Boolean, default: true },
  loading: { type: Boolean, default: false },
  setting: { type: Boolean, default: false }
})

const emit = defineEmits(['set-desired'])

const JSON_PLACEHOLDER = '{"key": "value"}'

const COLUMNS = [
  { key: 'desired', title: '期望值 desired', empty: '暂无期望值' },
  { key: 'reported', title: '上报值 reported', empty: '暂无上报值' },
  { key: 'delta', title: '差异 delta', empty: '已收敛，无差异' }
]

const values = ref({})

const fields = computed(() => propertyFields(props.capability))
const deltaKeys = computed(() => Object.keys(props.shadow.delta ?? {}).sort())

// 能力加载后重置表单值，避免残留上一组字段
watch(fields, () => {
  values.value = initialValues(fields.value)
}, { immediate: true })

function mapOf(key) {
  return props.shadow[key] ?? {}
}

function keysOf(key) {
  return Object.keys(mapOf(key)).sort()
}

function hasDelta(key) {
  return Object.prototype.hasOwnProperty.call(props.shadow.delta ?? {}, key)
}

const updatedLabel = computed(() =>
  props.shadow.updatedAt ? `更新于 ${new Date(props.shadow.updatedAt).toLocaleString('zh-CN')}` : '尚未变更'
)

function submit() {
  const { params, error } = buildParams(fields.value, values.value)
  if (error) {
    ElMessage.warning(error)
    return
  }
  if (Object.keys(params).length === 0) {
    ElMessage.warning('请至少填写一个期望值')
    return
  }
  emit('set-desired', { params })
}

/** 一键补发：把 delta（归一化文本）还原为原生类型后重新提交，等价于重发期望值。 */
function resendDelta() {
  const { params } = restoreParams(fields.value, props.shadow.delta)
  if (Object.keys(params).length === 0) {
    ElMessage.warning('delta 暂无可补发的期望值')
    return
  }
  emit('set-desired', { params })
}
</script>

<style scoped>
.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.device-shadow-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}

.device-shadow-panel__intro {
  min-width: 0;
}

.device-shadow-panel__meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
}

.device-shadow-panel__updated {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.device-shadow-panel__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.shadow-column {
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  padding: var(--spacing-sm) var(--spacing-md);
  min-height: 96px;
}

.shadow-column__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
  padding-bottom: var(--spacing-xs);
  border-bottom: 1px solid var(--color-border);
}

.shadow-column__title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

.shadow-column__count {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-family-mono);
}

.shadow-column__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.shadow-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
}

.shadow-row--delta {
  background: var(--color-warning-light);
  color: var(--color-warning);
  font-weight: var(--font-weight-medium);
}

.shadow-row__key {
  font-family: var(--font-family-mono);
  color: var(--color-text-secondary);
  word-break: break-all;
}

.shadow-row--delta .shadow-row__key {
  color: inherit;
}

.shadow-row__value {
  font-family: var(--font-family-mono);
  color: var(--color-text-primary);
  word-break: break-all;
  text-align: right;
}

.shadow-row--delta .shadow-row__value {
  color: inherit;
}

.shadow-column__empty {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.device-shadow-panel__setter {
  margin-bottom: var(--spacing-md);
}

.device-shadow-panel__setter-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-sm);
}

.device-shadow-panel__form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.device-shadow-panel__footer {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.device-shadow-panel__empty {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

@media (max-width: 768px) {
  .device-shadow-panel__grid {
    grid-template-columns: 1fr;
  }
}
</style>
