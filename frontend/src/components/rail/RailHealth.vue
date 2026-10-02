<template>
  <div class="rail-health">
    <section class="rail-health__section">
      <h4 class="rail-health__heading">设备在线率</h4>
      <div class="rail-health__ring">
        <el-progress
          type="circle"
          :percentage="ui.onlineRate"
          :width="120"
          :stroke-width="10"
          :color="ringColor"
        />
      </div>
      <div class="rail-health__counts">
        <span class="rail-health__count">
          <em class="rail-health__dot rail-health__dot--online" />在线 {{ ui.onlineDevices }}
        </span>
        <span class="rail-health__count">
          <em class="rail-health__dot rail-health__dot--offline" />离线 {{ offlineCount }}
        </span>
      </div>
    </section>

    <section class="rail-health__section">
      <h4 class="rail-health__heading">链路状态</h4>
      <ul class="rail-health__list">
        <li class="rail-health__row">
          <span>实时通道</span>
          <span :class="['rail-health__value', `is-${ui.realtimeStatus}`]">
            {{ realtimeText }}
          </span>
        </li>
        <li class="rail-health__row">
          <span>Broker</span>
          <span :class="['rail-health__value', brokerClass]">{{ brokerText }}</span>
        </li>
      </ul>
    </section>

    <section class="rail-health__section">
      <h4 class="rail-health__heading">最近事件</h4>
      <el-empty
        v-if="recentEvents.length === 0"
        description="暂无事件"
        :image-size="60"
      />
      <ul v-else class="rail-health__list">
        <li v-for="(event, index) in recentEvents" :key="index" class="rail-health__row">
          <span class="rail-health__event">{{ event.topic || event.type || '事件' }}</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const offlineCount = computed(() => Math.max(0, ui.totalDevices - ui.onlineDevices))
const ringColor = computed(() => (ui.onlineRate >= 80 ? 'var(--color-success)' : ui.onlineRate >= 50 ? 'var(--color-warning)' : 'var(--color-danger)'))

const REALTIME_TEXT = {
  connecting: '连接中',
  open: '已连接',
  reconnecting: '重连中',
  closed: '未连接'
}
const realtimeText = computed(() => REALTIME_TEXT[ui.realtimeStatus] || '未知')

const brokerText = computed(() => {
  if (ui.brokerStatus === 'ok') return '正常'
  if (ui.brokerStatus === 'down') return '异常'
  return '未知'
})
const brokerClass = computed(() => (ui.brokerStatus === 'ok' ? 'is-open' : ui.brokerStatus === 'down' ? 'is-closed' : ''))

const recentEvents = computed(() => ui.events.slice(-5).reverse())
</script>

<style scoped>
.rail-health__section + .rail-health__section {
  margin-top: var(--spacing-2xl);
}

.rail-health__heading {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.rail-health__ring {
  display: flex;
  justify-content: center;
}

.rail-health__counts {
  display: flex;
  justify-content: center;
  gap: var(--spacing-lg);
  margin-top: var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.rail-health__count {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.rail-health__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.rail-health__dot--online {
  background: var(--color-success);
}

.rail-health__dot--offline {
  background: var(--color-text-tertiary);
}

.rail-health__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.rail-health__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.rail-health__value {
  font-weight: var(--font-weight-medium);
}

.rail-health__value.is-open {
  color: var(--color-success);
}

.rail-health__value.is-connecting,
.rail-health__value.is-reconnecting {
  color: var(--color-warning);
}

.rail-health__value.is-closed {
  color: var(--color-danger);
}

.rail-health__event {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>