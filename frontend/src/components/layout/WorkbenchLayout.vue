<template>
  <div
    class="workbench"
    :class="{ 'workbench--no-rail': ui.railAsDrawer, 'workbench--no-nav': ui.navAsDrawer }"
  >
    <TopBar class="workbench__topbar" />
    <LeftNav class="workbench__nav" />

    <main class="workbench__content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <ContextRail class="workbench__rail" />
    <StatusBar class="workbench__statusbar" />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useUiStore } from '@/stores/ui'
import TopBar from './TopBar.vue'
import LeftNav from './LeftNav.vue'
import ContextRail from './ContextRail.vue'
import StatusBar from './StatusBar.vue'

const ui = useUiStore()

// 四档断点：≥1440 宽屏 / 1024–1439 紧凑（右栏默认收起）/ 768–1023 平板（左导航图标态、右栏抽屉）/ <768 手机（左右均抽屉）
const PHONE_QUERY = '(max-width: 767px)'
const NARROW_QUERY = '(max-width: 1023px)'
const COMPACT_QUERY = '(max-width: 1439px)'

const phoneQuery = window.matchMedia(PHONE_QUERY)
const narrowQuery = window.matchMedia(NARROW_QUERY)
const compactQuery = window.matchMedia(COMPACT_QUERY)

function resolveTier() {
  if (phoneQuery.matches) return 'phone'
  if (narrowQuery.matches) return 'narrow'
  if (compactQuery.matches) return 'compact'
  return 'wide'
}

function syncViewport() {
  ui.setViewport(resolveTier())
}

onMounted(() => {
  syncViewport()
  phoneQuery.addEventListener('change', syncViewport)
  narrowQuery.addEventListener('change', syncViewport)
  compactQuery.addEventListener('change', syncViewport)
})

onUnmounted(() => {
  phoneQuery.removeEventListener('change', syncViewport)
  narrowQuery.removeEventListener('change', syncViewport)
  compactQuery.removeEventListener('change', syncViewport)
})
</script>

<style scoped>
/* 五区骨架：顶栏 / 左导航 / 内容区 / 右上下文栏 / 底部状态栏
   内容区不提供内边距与宽度上限，统一由页面根容器 .page 承担 */
.workbench {
  display: grid;
  grid-template-areas:
    'topbar topbar topbar'
    'nav content rail'
    'statusbar statusbar statusbar';
  grid-template-columns: auto 1fr auto;
  grid-template-rows: var(--topbar-height) 1fr var(--statusbar-height);
  height: 100vh;
  overflow: hidden;
}

/* 平板及以下：右栏退出网格，改由抽屉承载 */
.workbench--no-rail {
  grid-template-areas:
    'topbar topbar'
    'nav content'
    'statusbar statusbar';
  grid-template-columns: auto 1fr;
}

/* 手机：左导航与右栏均退出网格，改由抽屉承载 */
.workbench--no-nav.workbench--no-rail {
  grid-template-areas:
    'topbar'
    'content'
    'statusbar';
  grid-template-columns: 1fr;
}

.workbench__topbar {
  grid-area: topbar;
  z-index: var(--z-topbar);
}

.workbench__nav {
  grid-area: nav;
  z-index: var(--z-nav);
}

.workbench__content {
  grid-area: content;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.workbench__rail {
  grid-area: rail;
  z-index: var(--z-rail);
}

.workbench__statusbar {
  grid-area: statusbar;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-base);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>