<template>
  <div class="site-layout">
    <SiteNav />
    <main class="site-layout__main">
      <slot />
    </main>
    <SiteFooter />
  </div>
</template>

<script setup>
import SiteNav from '@/components/site/SiteNav.vue'
import SiteFooter from '@/components/site/SiteFooter.vue'
</script>

<style scoped>
.site-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
}

.site-layout__main {
  flex: 1;
  padding-top: var(--site-nav-height);
}
</style>