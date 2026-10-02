<template>
  <nav class="docs-nav">
    <div v-for="chapter in docsNav" :key="chapter.group" class="docs-nav__group">
      <div class="docs-nav__group-title">{{ chapter.group }}</div>
      <router-link
        v-for="item in chapter.items"
        :key="item.path"
        :to="item.path"
        class="docs-nav__item"
        :class="{ 'is-active': route.path === item.path }"
      >
        {{ item.title }}
      </router-link>
    </div>
  </nav>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { docsNav } from '@/data/docsNav'

const route = useRoute()
</script>

<style scoped>
.docs-nav {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg) 0;
}

.docs-nav__group-title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--spacing-sm);
  padding: 0 var(--spacing-md);
}

.docs-nav__item {
  display: block;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  text-decoration: none;
  border-left: 2px solid transparent;
  transition: all var(--transition-fast);
}

.docs-nav__item:hover {
  color: var(--color-primary);
  background: var(--color-bg);
}

.docs-nav__item.is-active {
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-left-color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}
</style>