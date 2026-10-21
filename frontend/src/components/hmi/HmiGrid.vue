<template>
  <div class="hmi-grid">
    <div
      v-for="widget in widgets"
      :key="widget.id"
      class="hmi-grid__cell"
      :style="{ gridColumn: `span ${widget.span || 4}` }"
    >
      <HmiWidget
        :widget="widget"
        :property="propertyOf(widget)"
        :service="serviceOf(widget)"
        :value="values[widget.identifier]"
        :events="events"
        :device-id="deviceId"
        :device-name="deviceName"
        :preview="preview"
        @update="(target, value) => emit('update', target, value)"
        @command="(target) => emit('command', target)"
      />
    </div>
  </div>
</template>

<script setup>
import HmiWidget from './HmiWidget.vue'

const props = defineProps({
  widgets: { type: Array, default: () => [] },
  /** HmiDetailVO.model：{ properties, services } */
  model: { type: Object, default: () => ({ properties: [], services: [] }) },
  values: { type: Object, default: () => ({}) },
  events: { type: Array, default: () => [] },
  deviceId: { type: [String, Number], default: null },
  deviceName: { type: String, default: '' },
  preview: { type: Boolean, default: false }
})

const emit = defineEmits(['update', 'command'])

function propertyOf(widget) {
  return props.model?.properties?.find((item) => item.identifier === widget.identifier) || null
}

function serviceOf(widget) {
  return props.model?.services?.find((item) => item.identifier === widget.identifier) || null
}
</script>

<style scoped>
.hmi-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--grid-gutter);
  align-items: stretch;
}

.hmi-grid__cell {
  min-width: 0;
  display: flex;
}

.hmi-grid__cell > :deep(.hmi-widget) {
  flex: 1;
}

@media (max-width: 900px) {
  .hmi-grid__cell {
    grid-column: span 12 !important;
  }
}
</style>
