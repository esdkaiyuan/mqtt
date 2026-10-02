<template>
  <div class="rail-topic">
    <section class="rail-topic__section">
      <h4 class="rail-topic__heading">Topic 过滤</h4>
      <el-input
        v-model="keyword"
        placeholder="输入 Topic 关键字"
        clearable
      />
      <p class="rail-topic__hint">过滤实时消息列表中 Topic 包含该关键字的记录。</p>
    </section>

    <section class="rail-topic__section">
      <h4 class="rail-topic__heading">通道详情</h4>
      <ul class="rail-topic__list">
        <li class="rail-topic__row">
          <span>连接状态</span>
          <span :class="['rail-topic__value', `is-${ui.realtimeStatus}`]">{{ realtimeText }}</span>
        </li>
        <li class="rail-topic__row">
          <span>已接收事件</span>
          <strong>{{ ui.events.length }}</strong>
        </li>
        <li class="rail-topic__row">
          <span>命中过滤</span>
          <strong>{{ matchedCount }}</strong>
        </li>
      </ul>
    </section>

    <section class="rail-topic__section">
      <h4 class="rail-topic__heading">已发现 Topic</h4>
      <el-empty v-if="topics.length === 0" description="暂无消息" :image-size="60" />
      <ul v-else class="rail-topic__topics">
        <li
          v-for="topic in topics"
          :key="topic"
          class="rail-topic__topic"
          :title="topic"
          @click="applyTopic(topic)"
        >
          {{ topic }}
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const keyword = computed({
  get: () => ui.messageFilter,
  set: (value) => ui.setMessageFilter(value)
})

const REALTIME_TEXT = {
  connecting: '连接中',
  open: '已连接',
  reconnecting: '重连中',
  closed: '未连接'
}
const realtimeText = computed(() => REALTIME_TEXT[ui.realtimeStatus] || '未知')

const topics = computed(() => {
  const seen = new Set()
  for (let i = ui.events.length - 1; i >= 0; i -= 1) {
    const topic = ui.events[i]?.topic
    if (topic) seen.add(topic)
    if (seen.size >= 8) break
  }
  return [...seen]
})

const matchedCount = computed(() => {
  const kw = ui.messageFilter.trim().toLowerCase()
  if (!kw) return ui.events.length
  return ui.events.filter((event) => String(event.topic || '').toLowerCase().includes(kw)).length
})

function applyTopic(topic) {
  ui.setMessageFilter(topic)
}
</script>

<style scoped>
.rail-topic__section + .rail-topic__section {
  margin-top: var(--spacing-2xl);
}

.rail-topic__heading {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.rail-topic__hint {
  margin-top: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.6;
}

.rail-topic__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.rail-topic__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.rail-topic__row strong {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

.rail-topic__value.is-open {
  color: var(--color-success);
}

.rail-topic__value.is-connecting,
.rail-topic__value.is-reconnecting {
  color: var(--color-warning);
}

.rail-topic__value.is-closed {
  color: var(--color-danger);
}

.rail-topic__topics {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.rail-topic__topic {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
  background: var(--color-bg);
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--transition-fast), border-color var(--transition-fast);
}

.rail-topic__topic:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
</style>