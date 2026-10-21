<template>
  <div :class="['hmi-widget', `hmi-widget--${widget.type}`, { 'hmi-widget--preview': preview }]">
    <div class="hmi-widget__head">
      <span class="hmi-widget__title">{{ widget.title || widget.identifier || '未命名控件' }}</span>
      <span class="hmi-widget__type">{{ typeLabel }}</span>
    </div>

    <div class="hmi-widget__body">
      <!-- 只读值 -->
      <template v-if="widget.type === 'value'">
        <div class="value-display">
          <span class="value-display__number">{{ displayValue }}</span>
          <span v-if="widget.unit" class="value-display__unit">{{ widget.unit }}</span>
        </div>
        <p v-if="propertyMissing" class="widget-warning">属性已从物模型移除</p>
      </template>

      <!-- 开关 -->
      <template v-else-if="widget.type === 'switch'">
        <div class="control-row">
          <el-switch
            v-model="localValue"
            :disabled="preview || propertyMissing"
            @change="emitUpdate"
          />
          <span class="control-state">{{ Boolean(localValue) ? '开' : '关' }}</span>
        </div>
      </template>

      <!-- 滑块 -->
      <template v-else-if="widget.type === 'slider'">
        <el-slider
          v-model="localValue"
          :min="minValue"
          :max="maxValue"
          :step="stepValue"
          :disabled="preview || propertyMissing"
          show-input
          @change="emitUpdate"
        />
      </template>

      <!-- 输入 -->
      <template v-else-if="widget.type === 'input'">
        <el-input-number
          v-if="isNumeric"
          v-model="localValue"
          :min="widget.min ?? undefined"
          :max="widget.max ?? undefined"
          :step="stepValue"
          :precision="property?.integer ? 0 : undefined"
          :disabled="preview || propertyMissing"
          controls-position="right"
          @change="emitUpdate"
        />
        <el-input
          v-else
          v-model="localValue"
          :disabled="preview || propertyMissing"
          placeholder="输入后回车提交"
          @change="emitUpdate"
        />
      </template>

      <!-- 下拉 -->
      <template v-else-if="widget.type === 'select'">
        <el-select
          v-model="localValue"
          :disabled="preview || propertyMissing"
          placeholder="请选择"
          @change="emitUpdate"
        >
          <el-option
            v-for="key in enumKeys"
            :key="key"
            :value="key"
            :label="key"
          />
        </el-select>
      </template>

      <!-- 服务按钮 -->
      <template v-else-if="widget.type === 'button'">
        <p class="service-desc">{{ service?.input?.length ? '调用前会弹出参数表单' : '无参数，点击后立即下发' }}</p>
        <el-button type="primary" :disabled="preview || serviceMissing" @click="emit('command', widget)">
          调用 {{ widget.title || widget.identifier }}
        </el-button>
        <p v-if="serviceMissing" class="widget-warning">服务已从物模型移除</p>
      </template>

      <!-- 趋势 -->
      <template v-else-if="widget.type === 'chart'">
        <div v-if="preview" class="chart-placeholder">趋势图</div>
        <BoardPanel v-else-if="deviceId" :panel="panel" :device-names="deviceNames" />
        <p v-else class="widget-warning">未绑定设备</p>
      </template>

      <!-- 事件 -->
      <template v-else-if="widget.type === 'events'">
        <div v-if="preview" class="chart-placeholder">最近事件</div>
        <div v-else-if="events.length === 0" class="events-empty">暂无事件</div>
        <ul v-else class="events-list">
          <li v-for="event in events" :key="event.id || `${event.identifier}-${event.reportedAt}`">
            <span :class="['event-type', event.eventType]">{{ event.eventType || 'info' }}</span>
            <span class="event-name">{{ event.identifier }}</span>
            <span class="event-time">{{ formatTime(event.reportedAt) }}</span>
          </li>
        </ul>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import BoardPanel from '@/components/dashboard/BoardPanel.vue'

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
  widget: { type: Object, required: true },
  /** 物模型属性投影（可能为 null，表示属性已被移除） */
  property: { type: Object, default: null },
  /** 物模型服务投影 */
  service: { type: Object, default: null },
  /** 已按类型归一化的当前值 */
  value: { type: [String, Number, Boolean, Object, Array], default: undefined },
  events: { type: Array, default: () => [] },
  deviceId: { type: [String, Number], default: null },
  deviceName: { type: String, default: '' },
  preview: { type: Boolean, default: false }
})

const emit = defineEmits(['update', 'command'])

/** 控件本地值：拖拽 / 输入时先即时反馈，提交仍由 change 事件触发一次。 */
const localValue = ref(props.value)
watch(
  () => props.value,
  (value) => {
    localValue.value = value
  }
)

function emitUpdate() {
  emit('update', props.widget, localValue.value)
}

const typeLabel = computed(() => TYPE_LABELS[props.widget.type] || props.widget.type)
const propertyMissing = computed(() => !props.preview && props.widget.type !== 'events'
  && props.widget.type !== 'button' && !props.property)
const serviceMissing = computed(() => !props.preview && !props.service)

const normalizedType = computed(() => String(props.property?.type || '').toLowerCase())
const isNumeric = computed(() => ['int', 'integer', 'long', 'float', 'double', 'number'].includes(normalizedType.value))
const enumKeys = computed(() => props.property?.enumKeys || [])

const minValue = computed(() => Number(props.widget.min ?? 0))
const maxValue = computed(() => {
  const max = Number(props.widget.max)
  return Number.isFinite(max) ? max : 100
})
const stepValue = computed(() => Number(props.widget.step ?? (props.property?.integer ? 1 : 0.1)))

const displayValue = computed(() => {
  const value = props.value
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
})

const panel = computed(() => ({
  title: props.widget.title || props.widget.identifier,
  deviceIds: [Number(props.deviceId)],
  identifier: props.widget.identifier,
  bucket: '5m',
  rangeDays: 1,
  chartType: 'line',
  aggregation: 'avg'
}))

const deviceNames = computed(() => ({
  [Number(props.deviceId)]: props.deviceName || `设备 ${props.deviceId}`
}))

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.hmi-widget {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 128px;
  padding: var(--spacing-lg);
  background: var(--color-bg-container, #fff);
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius);
}

.hmi-widget__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.hmi-widget__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}

.hmi-widget__type {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-bg);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.hmi-widget__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.value-display {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.value-display__number {
  font-size: 28px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  line-height: 1.2;
  word-break: break-all;
}

.value-display__unit {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.control-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.control-state {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.service-desc {
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.widget-warning {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-warning, #b54708);
}

.chart-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  border: 1px dashed var(--border-color);
  border-radius: var(--border-radius-sm);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.events-empty {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.events-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  max-height: 240px;
  overflow-y: auto;
}

.events-list li {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
}

.event-type {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-bg);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.event-type.alert {
  color: var(--color-warning, #b54708);
}

.event-type.fault {
  color: var(--color-danger, #d92d20);
}

.event-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.event-time {
  flex-shrink: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.hmi-widget--chart :deep(.board-panel),
.hmi-widget--events {
  min-height: 0;
}
</style>
