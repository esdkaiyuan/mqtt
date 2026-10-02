<template>
  <!-- 宽屏 / 紧凑 / 平板：固定左导航（平板档强制图标态） -->
  <aside
    v-if="!ui.navAsDrawer"
    class="left-nav"
    :class="{ 'left-nav--collapsed': collapsed }"
  >
    <NavMenu :collapsed="collapsed" />
  </aside>

  <!-- 手机：抽屉式导航，点击菜单后自动收起 -->
  <el-drawer
    v-else
    v-model="drawerOpen"
    direction="ltr"
    size="240px"
    :with-header="false"
    class="left-nav-drawer"
  >
    <NavMenu :collapsed="false" />
  </el-drawer>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import NavMenu from './NavMenu.vue'

const route = useRoute()
const ui = useUiStore()

// 平板档（768–1023）固定为 64px 图标态，其余档位跟随用户折叠偏好
const collapsed = computed(() => ui.isNarrow || ui.navCollapsed)

const drawerOpen = computed({
  get: () => ui.navDrawerOpen,
  set: (value) => {
    if (value !== ui.navDrawerOpen) ui.toggleNavDrawer()
  }
})

// 抽屉内点击菜单会跳转路由，跳转后收起抽屉，避免遮挡内容
watch(
  () => route.path,
  () => {
    if (ui.navAsDrawer && ui.navDrawerOpen) ui.toggleNavDrawer()
  }
)
</script>

<style scoped>
.left-nav {
  width: var(--nav-width);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  border-right: 1px solid var(--border-color);
  transition: width var(--transition-base);
  overflow: hidden;
}

.left-nav--collapsed {
  width: var(--nav-width-collapsed);
}
</style>

<style>
.left-nav-drawer .el-drawer__body {
  padding: 0;
}
</style>