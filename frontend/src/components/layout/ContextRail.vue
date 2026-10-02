<template>
  <!-- 平板及以下：右栏改为抽屉，由顶栏按钮唤起 -->
  <el-drawer
    v-if="ui.railAsDrawer"
    v-model="drawerOpen"
    direction="rtl"
    size="320px"
    :with-header="false"
    class="context-rail-drawer"
  >
    <div class="context-rail__header">
      <span class="context-rail__title">{{ panelTitle }}</span>
      <button class="context-rail__close" title="关闭" @click="drawerOpen = false">
        <el-icon :size="16"><Close /></el-icon>
      </button>
    </div>
    <div class="context-rail__body">
      <component :is="panel" :key="panelKey" />
    </div>
  </el-drawer>

  <!-- 宽屏：固定右栏，可收起为把手 -->
  <aside v-else class="context-rail" :class="{ 'context-rail--closed': !ui.railOpen }">
    <template v-if="ui.railOpen">
      <div class="context-rail__header">
        <span class="context-rail__title">{{ panelTitle }}</span>
        <button class="context-rail__close" title="收起上下文栏" @click="ui.toggleRail()">
          <el-icon :size="16"><ArrowRight /></el-icon>
        </button>
      </div>
      <div class="context-rail__body">
        <component :is="panel" :key="panelKey" />
      </div>
    </template>

    <button
      v-else
      class="context-rail__handle"
      title="展开上下文栏"
      @click="ui.toggleRail()"
    >
      <el-icon :size="16"><ArrowLeft /></el-icon>
    </button>
  </aside>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Close } from '@element-plus/icons-vue'
import { useUiStore } from '@/stores/ui'
import RailHealth from '@/components/rail/RailHealth.vue'
import RailDeviceSummary from '@/components/rail/RailDeviceSummary.vue'
import RailTopicFilter from '@/components/rail/RailTopicFilter.vue'
import RailQuickLinks from '@/components/rail/RailQuickLinks.vue'

const route = useRoute()
const ui = useUiStore()

const PANELS = {
  Dashboard: { component: RailHealth, title: '平台健康度' },
  DeviceList: { component: RailDeviceSummary, title: '设备概览' },
  DeviceDetail: { component: RailDeviceSummary, title: '设备摘要' },
  MessageMonitor: { component: RailTopicFilter, title: 'Topic 过滤' },
  HistoryQuery: { component: RailHealth, title: '平台健康度' },
  ApiKeyManagement: { component: RailQuickLinks, title: '使用说明' },
  WebhookManagement: { component: RailQuickLinks, title: '使用说明' }
}

const DEFAULT_PANEL = { component: RailHealth, title: '平台健康度' }

watch(
  () => route.name,
  (name) => ui.setRail(name || ''),
  { immediate: true }
)

const drawerOpen = computed({
  get: () => ui.railDrawerOpen,
  set: (value) => {
    if (value !== ui.railDrawerOpen) ui.toggleRailDrawer()
  }
})

const panelKey = computed(() => ui.railName)
const resolved = computed(() => PANELS[panelKey.value] || DEFAULT_PANEL)
const panel = computed(() => resolved.value.component)
const panelTitle = computed(() => resolved.value.title)
</script>

<style scoped>
.context-rail {
  width: var(--rail-width);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border-left: 1px solid var(--border-color);
  overflow: hidden;
}

.context-rail--closed {
  width: 40px;
  align-items: center;
  justify-content: flex-start;
}

.context-rail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  flex-shrink: 0;
}

.context-rail__title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.context-rail__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: var(--border-radius-sm);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.context-rail__close:hover {
  background: var(--color-bg);
  color: var(--color-primary);
}

.context-rail__body {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg);
}

.context-rail__handle {
  margin-top: var(--spacing-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--border-color);
  background: var(--color-white);
  border-radius: var(--border-radius-sm);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: color var(--transition-fast), border-color var(--transition-fast);
}

.context-rail__handle:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
</style>

<style>
.context-rail-drawer .el-drawer__body {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>