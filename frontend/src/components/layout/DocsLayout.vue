<template>
  <div class="docs-layout">
    <SiteNav />

    <div class="docs-layout__body">
      <aside class="docs-layout__side">
        <DocsNav />
      </aside>

      <main class="docs-layout__main">
        <div class="page--narrow docs-layout__content">
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

    <SiteFooter />
  </div>
</template>

<script setup>
import SiteNav from '@/components/site/SiteNav.vue'
import SiteFooter from '@/components/site/SiteFooter.vue'
import DocsNav from '@/components/docs/DocsNav.vue'
import DocsToc from '@/components/docs/DocsToc.vue'
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
  grid-template-columns: 240px minmax(0, 1fr) 200px;
  gap: var(--spacing-3xl);
  width: 100%;
  max-width: 1280px;
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

.docs-layout__content {
  margin: 0 auto;
}

.docs-layout__toc {
  position: sticky;
  top: var(--site-nav-height);
  align-self: start;
  max-height: calc(100vh - var(--site-nav-height));
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-base);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 1280px) {
  .docs-layout__toc {
    display: none;
  }
  .docs-layout__body {
    grid-template-columns: 220px minmax(0, 1fr);
  }
}

@media (max-width: 1024px) {
  .docs-layout__side {
    display: none;
  }
  .docs-layout__body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>