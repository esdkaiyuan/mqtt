<template>
  <el-drawer
    :model-value="modelValue"
    title="编辑上位机"
    size="560px"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="editor-body">
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="name" maxlength="64" />
        </el-form-item>
      </el-form>

      <div class="editor-section">
        <h4>添加控件</h4>
        <div class="add-row">
          <el-select v-model="addPropertyId" filterable placeholder="选择属性" class="add-select">
            <el-option
              v-for="property in addableProperties"
              :key="property.identifier"
              :value="property.identifier"
              :label="`${property.identifier} · ${property.type}${property.accessMode === 'rw' ? ' · 可写' : ''}`"
            />
          </el-select>
          <el-button :disabled="!addPropertyId" @click="addProperty">添加</el-button>
        </div>
        <div class="add-row">
          <el-select v-model="addServiceId" filterable placeholder="选择服务" class="add-select">
            <el-option
              v-for="service in detail.model?.services || []"
              :key="service.identifier"
              :value="service.identifier"
              :label="service.identifier"
            />
          </el-select>
          <el-button :disabled="!addServiceId" @click="addService">添加</el-button>
        </div>
        <el-button :disabled="hasEvents" @click="addEvents">添加「最近事件」面板</el-button>
      </div>

      <div class="editor-section">
        <h4>控件列表（{{ widgets.length }}）</h4>
        <div v-if="widgets.length === 0" class="empty-hint">至少保留一个控件</div>
        <div v-for="(widget, index) in widgets" :key="widget.id || index" class="widget-row">
          <div class="widget-row__main">
            <span class="widget-row__type">{{ typeLabel(widget.type) }}</span>
            <el-input v-model="widget.title" maxlength="64" class="widget-row__title" />
          </div>
          <div class="widget-row__meta">
            <span class="widget-row__identifier">{{ widget.identifier || '—' }}</span>
            <label class="widget-row__span">
              跨度
              <el-input-number v-model="widget.span" :min="1" :max="12" size="small" controls-position="right" />
            </label>
            <el-input
              v-if="supportsUnit(widget.type)"
              v-model="widget.unit"
              maxlength="16"
              placeholder="单位"
              class="widget-row__unit"
            />
            <el-button link type="danger" @click="removeWidget(index)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="widgets.length === 0" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const TYPE_LABELS = {
  value: '数值',
  switch: '开关',
  slider: '滑块',
  input: '输入',
  select: '枚举',
  button: '服务',
  chart: '趋势',
  events: '事件'
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** HmiDetailVO */
  detail: { type: Object, required: true },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'save'])

const name = ref('')
const widgets = ref([])
const addPropertyId = ref('')
const addServiceId = ref('')

const hasEvents = computed(() => widgets.value.some((widget) => widget.type === 'events'))

/**
 * 可添加属性：只读属性都可作为展示值；可写属性仅开放 bool / enum / 数值 / 文本，
 * 与生成器保持一致 —— 可写的 date / struct / array 目前没有合适的通用控件，
 * 放进来会生成一个后端必然拒绝的配置。
 */
const addableProperties = computed(() =>
  (props.detail?.model?.properties || []).filter((property) => {
    if (property.accessMode !== 'rw') return true
    const type = String(property.type || '').toLowerCase()
    return ['bool', 'boolean', 'enum', 'int', 'integer', 'long', 'float', 'double', 'number', 'text', 'string']
      .includes(type)
  })
)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    name.value = props.detail?.name || ''
    widgets.value = JSON.parse(JSON.stringify(props.detail?.config?.widgets || []))
    addPropertyId.value = ''
    addServiceId.value = ''
  },
  { immediate: true }
)

function propertyOf(identifier) {
  return props.detail?.model?.properties?.find((item) => item.identifier === identifier) || null
}

function addProperty() {
  const property = propertyOf(addPropertyId.value)
  if (!property) return
  const widget = { id: nextId(), type: widgetTypeFor(property), title: property.identifier, identifier: property.identifier, span: 4 }
  if (widget.type === 'switch' || widget.type === 'select') widget.span = 3
  if (['slider', 'input'].includes(widget.type)) {
    widget.min = property.min ?? undefined
    widget.max = property.max ?? undefined
    widget.step = property.integer ? 1 : 0.1
  }
  if (widget.type === 'chart') widget.span = 12
  widgets.value.push(widget)
  addPropertyId.value = ''
}

function addService() {
  const service = addServiceId.value
  if (!service) return
  widgets.value.push({ id: nextId(), type: 'button', title: service, identifier: service, span: 3 })
  addServiceId.value = ''
}

function addEvents() {
  widgets.value.push({ id: nextId(), type: 'events', title: '最近事件', span: 12 })
}

function removeWidget(index) {
  widgets.value.splice(index, 1)
}

function widgetTypeFor(property) {
  const type = String(property.type || '').toLowerCase()
  if (property.accessMode !== 'rw') return 'value'
  if (['bool', 'boolean'].includes(type)) return 'switch'
  if (type === 'enum') return 'select'
  if (['int', 'integer', 'long', 'float', 'double', 'number'].includes(type)) {
    return property.min !== null && property.min !== undefined
      && property.max !== null && property.max !== undefined ? 'slider' : 'input'
  }
  return 'input'
}

function supportsUnit(type) {
  return ['value', 'slider', 'input', 'chart'].includes(type)
}

function nextId() {
  return `w${Date.now()}${Math.floor(Math.random() * 1000)}`
}

function typeLabel(type) {
  return TYPE_LABELS[type] || type
}

function save() {
  if (!name.value.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  emit('save', {
    name: name.value.trim(),
    config: {
      title: name.value.trim(),
      widgets: widgets.value
    }
  })
}
</script>

<style scoped>
.editor-body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.editor-section h4 {
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.add-row {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.add-select {
  flex: 1;
}

.widget-row {
  padding: var(--spacing-sm) 0;
  border-bottom: 1px solid var(--border-color-light);
}

.widget-row__main {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.widget-row__type {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-bg);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.widget-row__title {
  flex: 1;
}

.widget-row__meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.widget-row__identifier {
  flex: 1;
  min-width: 80px;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.widget-row__span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.widget-row__span :deep(.el-input-number) {
  width: 90px;
}

.widget-row__unit {
  width: 90px;
}

.empty-hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
