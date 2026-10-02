<template>
  <footer class="status-bar">
    <div class="status-bar__left">
      <span class="status-bar__item">
        <em :class="['status-bar__dot', `is-${ui.realtimeStatus}`]" />
        实时通道{{ realtimeText }}
      </span>
      <span class="status-bar__item status-bar__item--optional">Broker {{ brokerText }}</span>
      <span class="status-bar__item status-bar__item--optional">在线 {{ ui.onlineDevices }}/{{ ui.totalDevices }}</span>
    </div>
    <div class="status-bar__right">
      <span class="status-bar__item status-bar__item--muted">{{ ui.version }}</span>
      <span class="status-bar__item status-bar__item--muted">更新于 {{ lastRefreshText }}</span>
    </div>
  </footer>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const REALTIME_TEXT = {
  connecting: '连接中',
  open: '已连接',
  reconnecting: '重连中',
  closed: '未连接'
}
const realtimeText = computed(() => REALTIME_TEXT[ui.realtimeStatus] || '未知')
const brokerText = computed(() => (ui.brokerStatus === 'ok' ? '正常' : ui.brokerStatus === 'down' ? '异常' : '未知'))

const lastRefreshText = computed(() => {
  if (!ui.lastRefreshAt) return '--:--:--'
  const d = new Date(ui.lastRefreshAt)
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map((n) => String(n).padStart(2, '0'))
    .join(':')
})

onMounted(() => {
  ui.startHealthPolling()
  ui.connectRealtime()
})

onUnmounted(() => {
  ui.stopHealthPolling()
  ui.disconnectRealtime()
})
</script>

<style scoped>
.status-bar {
  height: var(--statusbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--grid-gutter);
  padding: 0 var(--spacing-lg);
  background: var(--color-white);
  border-top: 1px solid var(--border-color);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
}

.status-bar__left,
.status-bar__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  min-width: 0;
}

.status-bar__item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  white-space: nowrap;
}

.status-bar__item--muted {
  color: var(--color-text-tertiary);
}

.status-bar__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  background: var(--color-text-tertiary);
}

.status-bar__dot.is-open {
  background: var(--color-success);
}

.status-bar__dot.is-connecting,
.status-bar__dot.is-reconnecting {
  background: var(--color-warning);
}

.status-bar__dot.is-closed {
  background: var(--color-danger);
}

/* 平板及以下：精简，隐藏版本与更新时间 */
@media (max-width: 1023px) {
  .status-bar__right {
    display: none;
  }
}

/* 手机：仅保留实时通道状态圆点 */
@media (max-width: 767px) {
  .status-bar__item--optional {
    display: none;
  }
}
</style>