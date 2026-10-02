<template>
  <section :id="id" class="landing-section" :class="sectionClasses">
    <div class="section-container">
      <div v-if="title" class="section-header">
        <h2 class="section-title">{{ title }}</h2>
        <p class="section-desc">{{ desc }}</p>
      </div>
      <slot />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  id: { type: String, default: '' },
  title: { type: String, default: '' },
  desc: { type: String, default: '' },
  theme: { type: String, default: 'white' },
  compact: { type: Boolean, default: false }
})

const sectionClasses = computed(() => [
  `is-${props.theme}`,
  { 'is-compact': props.compact }
])
</script>

<style scoped>
.landing-section {
  padding: 96px 0;
}

.landing-section.is-compact {
  padding: 80px 0;
}

.landing-section.is-white {
  background: var(--color-white);
}

.landing-section.is-gray {
  background: var(--color-bg);
}

.landing-section.is-dark {
  background: var(--color-text-primary);
}

.section-container {
  max-width: var(--site-max-width);
  margin: 0 auto;
  padding: 0 var(--content-padding);
}

.section-header {
  text-align: center;
  margin-bottom: 48px;
}

.section-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 16px;
}

.section-desc {
  font-size: 16px;
  color: var(--color-text-regular);
}
</style>
