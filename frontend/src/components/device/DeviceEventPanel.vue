<template>
  <div class="device-event-panel">
    <h3 class="card-title">事件</h3>
    <p class="card-desc">物模型解析出的事件记录，按上报时间倒序</p>

    <div v-loading="loading" class="device-event-panel__body">
      <EmptyState
        v-if="!loading && events.length === 0"
        description="暂无事件记录"
        :image-size="80"
      />

      <div v-else class="event-timeline">
        <div v-for="event in events" :key="event.id" class="event-timeline__item">
          <span class="event-timeline__dot" :class="`event-timeline__dot--${event.eventType}`" />
          <div class="event-timeline__body">
            <div class="event-timeline__head">
              <span class="event-timeline__id">{{ event.identifier }}</span>
              <el-tag :type="typeTag(event.eventType)" size="small">{{ event.eventType }}</el-tag>
              <span class="event-timeline__time">{{ formatTime(event.reportedAt) }}</span>
            </div>
            <pre v-if="outputText(event)" class="event-timeline__data">{{ outputText(event) }}</pre>
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > 0" class="device-event-panel__pagination">
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        background
        @current-change="$emit('page-change', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import EmptyState from '@/components/common/EmptyState.vue'

defineProps({
  events: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 }
})

defineEmits(['page-change'])

function typeTag(type) {
  if (type === 'alert') return 'warning'
  if (type === 'fault') return 'danger'
  return 'info'
}

function formatTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

/** 输出参数按 JSON 美化展示；无法解析时原样透出，避免丢失信息。 */
function outputText(event) {
  if (!event.outputData) return ''
  try {
    return JSON.stringify(JSON.parse(event.outputData), null, 2)
  } catch {
    return event.outputData
  }
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
  margin-bottom: var(--spacing-md);
}

.event-timeline {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.event-timeline__item {
  display: flex;
  gap: var(--spacing-md);
  padding-left: var(--spacing-xs);
}

.event-timeline__dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--color-info);
}

.event-timeline__dot--alert {
  background: var(--color-warning);
}

.event-timeline__dot--fault {
  background: var(--color-danger);
}

.event-timeline__body {
  flex: 1;
  min-width: 0;
}

.event-timeline__head {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.event-timeline__id {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.event-timeline__time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.event-timeline__data {
  margin: var(--spacing-sm) 0 0;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
  white-space: pre-wrap;
  word-break: break-all;
}

.device-event-panel__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
}
</style>