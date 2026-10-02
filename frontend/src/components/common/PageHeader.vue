<template>
  <header class="app-page-header">
    <div class="app-page-header__main">
      <div v-if="$slots.title" class="app-page-header__title">
        <slot name="title" />
      </div>
      <h1 v-else class="app-page-header__title">{{ title }}</h1>
      <p v-if="desc" class="app-page-header__desc">{{ desc }}</p>
    </div>
    <div v-if="$slots.actions" class="app-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  desc: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.app-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--grid-gutter);
  margin-bottom: var(--content-padding);
}

.app-page-header__main {
  min-width: 0;
}

.app-page-header__title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  line-height: 1.4;
  color: var(--color-text-primary);
}

.app-page-header__desc {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.app-page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
}
</style>