<template>
  <nav class="docs-nav" aria-label="文档目录">
    <section
      v-for="(chapter, chapterIndex) in docsNav"
      :key="chapter.group"
      class="docs-nav__group"
      :class="{ 'is-current': isCurrentChapter(chapter) }"
    >
      <header class="docs-nav__group-head">
        <span class="docs-nav__group-index" aria-hidden="true">
          {{ String(chapterIndex + 1).padStart(2, '0') }}
        </span>
        <h3 class="docs-nav__group-title">{{ chapter.group }}</h3>
      </header>

      <div class="docs-nav__items">
        <router-link
          v-for="(item, itemIndex) in chapter.items"
          :key="item.path"
          :to="item.path"
          class="docs-nav__item"
          :class="{ 'is-active': isActive(item, itemIndex) }"
          :aria-current="isActive(item, itemIndex) ? 'page' : undefined"
        >
          <span class="docs-nav__indicator" aria-hidden="true" />
          <span class="docs-nav__label">{{ item.title }}</span>
        </router-link>
      </div>
    </section>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { docsNav, resolveChapter } from '@/data/docsNav'

const route = useRoute()
const currentChapter = computed(() => resolveChapter(route.path))

function isCurrentChapter(chapter) {
  return currentChapter.value?.group === chapter.group
}

function isActive(item, index) {
  if (route.path === item.path) return true
  const current = route.path.replace(/\/+$/, '')
  return index === 0 && current === item.path.replace(/\/[^/]+$/, '')
}
</script>

<style scoped>
.docs-nav {
  display: flex;
  flex-direction: column;
  gap: 26px;
  padding: var(--spacing-xl) 0 var(--spacing-3xl);
}

.docs-nav__group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.docs-nav__group-head {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  padding: 0 var(--spacing-md);
}

.docs-nav__group-index {
  flex-shrink: 0;
  font-family: var(--font-family-mono);
  font-size: 10px;
  line-height: 1;
  letter-spacing: 0.08em;
  color: var(--color-text-tertiary);
  opacity: 0.6;
}

.docs-nav__group-title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  letter-spacing: 0.06em;
  margin: 0;
}

.docs-nav__group.is-current .docs-nav__group-title {
  color: var(--color-primary);
}

.docs-nav__group.is-current .docs-nav__group-index {
  color: var(--color-primary);
  opacity: 1;
}

.docs-nav__items {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-left: 28px;
  padding-left: var(--spacing-sm);
  border-left: 1px solid var(--border-color-light);
}

.docs-nav__item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 6px 10px;
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  line-height: 1.5;
  color: var(--color-text-regular);
  text-decoration: none;
  transition: color var(--transition-fast), background-color var(--transition-fast);
}

.docs-nav__indicator {
  flex-shrink: 0;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-border);
  transition: background-color var(--transition-fast), box-shadow var(--transition-fast);
}

.docs-nav__label {
  min-width: 0;
  overflow-wrap: anywhere;
}

.docs-nav__item:hover {
  color: var(--color-primary);
  background: var(--color-bg);
}

.docs-nav__item:hover .docs-nav__indicator {
  background: var(--color-primary);
}

.docs-nav__item.is-active {
  color: var(--color-primary);
  background: var(--color-primary-light);
  font-weight: var(--font-weight-medium);
}

.docs-nav__item.is-active .docs-nav__indicator {
  background: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.12);
}
</style>