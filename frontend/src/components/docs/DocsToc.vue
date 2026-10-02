<template>
  <aside v-if="chapter" class="docs-toc">
    <div class="docs-toc__title">本页目录</div>
    <router-link
      v-for="item in chapter.items"
      :key="item.path"
      :to="item.path"
      class="docs-toc__item"
      :class="{ 'is-active': route.path === item.path }"
    >
      {{ item.title }}
    </router-link>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { resolveChapter } from '@/data/docsNav'

const route = useRoute()
const chapter = computed(() => resolveChapter(route.path))
</script>

<style scoped>
.docs-toc {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-lg) 0;
}

.docs-toc__title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--spacing-sm);
}

.docs-toc__item {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  text-decoration: none;
  padding: 2px 0;
  border-left: 2px solid var(--border-color);
  padding-left: var(--spacing-md);
  transition: color var(--transition-fast), border-color var(--transition-fast);
}

.docs-toc__item:hover {
  color: var(--color-primary);
}

.docs-toc__item.is-active {
  color: var(--color-primary);
  border-left-color: var(--color-primary);
}
</style>