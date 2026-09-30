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
  background: #fff;
}

.landing-section.is-gray {
  background: #F5F7FA;
}

.landing-section.is-dark {
  background: #1D2129;
}

.section-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.section-header {
  text-align: center;
  margin-bottom: 48px;
}

.section-title {
  font-size: 36px;
  font-weight: 700;
  color: #1D2129;
  margin-bottom: 16px;
}

.section-desc {
  font-size: 16px;
  color: #4E5969;
}
</style>
