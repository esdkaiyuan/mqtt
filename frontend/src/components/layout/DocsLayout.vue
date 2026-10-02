<template>
  <div class="docs-layout">
    <SiteNav :toc-toggle="navAsDrawer" @toggle-toc="navDrawerOpen = true" />

    <div class="docs-layout__body">
      <aside class="docs-layout__side">
        <DocsNav />
      </aside>

      <main class="docs-layout__main">
        <div class="docs-layout__content">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </main>

      <aside class="docs-layout__toc">
        <DocsToc />
      </aside>
    </div>

    <!-- <1024px：左导航收起为抽屉，由站点头「目录」按钮唤起 -->
    <el-drawer
      v-if="navAsDrawer"
      v-model="navDrawerOpen"
      direction="ltr"
      size="260px"
      :with-header="false"
      class="docs-nav-drawer"
    >
      <div class="docs-nav-drawer__header">
        <span class="docs-nav-drawer__title">文档目录</span>
        <button
          class="docs-nav-drawer__close"
          type="button"
          title="关闭"
          @click="navDrawerOpen = false"
        >
          <el-icon :size="16"><Close /></el-icon>
        </button>
      </div>
      <div class="docs-nav-drawer__body">
        <DocsNav />
      </div>
    </el-drawer>

    <SiteFooter />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import SiteNav from '@/components/site/SiteNav.vue'
import SiteFooter from '@/components/site/SiteFooter.vue'
import DocsNav from '@/components/docs/DocsNav.vue'
import DocsToc from '@/components/docs/DocsToc.vue'

const NAV_DRAWER_QUERY = '(max-width: 1023px)'
const navQuery = window.matchMedia(NAV_DRAWER_QUERY)

const navAsDrawer = ref(navQuery.matches)
const navDrawerOpen = ref(false)

function syncNavMode() {
  navAsDrawer.value = navQuery.matches
  if (!navQuery.matches) navDrawerOpen.value = false
}

onMounted(() => navQuery.addEventListener('change', syncNavMode))
onUnmounted(() => navQuery.removeEventListener('change', syncNavMode))

const route = useRoute()

// 抽屉内点击章节会跳转路由，跳转后收起抽屉，避免遮挡正文
watch(
  () => route.path,
  () => {
    if (navAsDrawer.value) navDrawerOpen.value = false
  }
)
</script>

<style scoped>
.docs-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
  padding-top: var(--site-nav-height);
}

.docs-layout__body {
  flex: 1;
  display: grid;
  grid-template-columns: var(--nav-width) minmax(0, 1fr) 200px;
  gap: var(--spacing-3xl);
  width: 100%;
  max-width: var(--site-max-width);
  margin: 0 auto;
  padding: 0 var(--content-padding);
}

.docs-layout__side {
  border-right: 1px solid var(--border-color-light);
  position: sticky;
  top: var(--site-nav-height);
  align-self: start;
  max-height: calc(100vh - var(--site-nav-height));
  overflow-y: auto;
}

.docs-layout__main {
  min-width: 0;
  padding: var(--spacing-3xl) 0;
}

/* 左右内边距由三栏容器提供，此处只保留阅读栏上限，避免双重内边距 */
.docs-layout__content {
  max-width: 960px;
  margin: 0 auto;
}

.docs-layout__toc {
  position: sticky;
  top: var(--site-nav-height);
  align-self: start;
  max-height: calc(100vh - var(--site-nav-height));
  overflow-y: auto;
}

.docs-nav-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  flex-shrink: 0;
}

.docs-nav-drawer__title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.docs-nav-drawer__close {
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

.docs-nav-drawer__close:hover {
  background: var(--color-bg);
  color: var(--color-primary);
}

.docs-nav-drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--spacing-lg);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-base);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 1279px) {
  .docs-layout__toc {
    display: none;
  }
  .docs-layout__body {
    grid-template-columns: 220px minmax(0, 1fr);
  }
}

@media (max-width: 1023px) {
  .docs-layout__side {
    display: none;
  }
  .docs-layout__body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

<style>
.docs-nav-drawer .el-drawer__body {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>