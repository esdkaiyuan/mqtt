<template>
  <component
    :is="to ? 'router-link' : 'div'"
    :to="to || undefined"
    class="brand-logo"
    :class="{ 'brand-logo--link': !!to }"
  >
    <svg
      class="brand-logo__mark"
      :width="size"
      :height="size"
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <rect class="brand-logo__bg" width="32" height="32" rx="8" />
      <path
        class="brand-logo__ink"
        d="M8 16C8 11.6 11.6 8 16 8C20.4 8 24 11.6 24 16C24 20.4 20.4 24 16 24"
        stroke-width="2.5"
        stroke-linecap="round"
      />
      <circle class="brand-logo__ink-fill" cx="16" cy="16" r="3" />
      <path
        class="brand-logo__ink"
        d="M16 10V13M16 19V22M13 16H10M19 16H22"
        stroke-width="2"
        stroke-linecap="round"
      />
    </svg>
    <span
      v-if="showText"
      class="brand-logo__text"
      :style="{ fontSize: textSize + 'px' }"
    >{{ text }}</span>
  </component>
</template>

<script setup>
defineProps({
  size: {
    type: Number,
    default: 32
  },
  showText: {
    type: Boolean,
    default: true
  },
  text: {
    type: String,
    default: 'MQTT Cloud'
  },
  textSize: {
    type: Number,
    default: 20
  },
  to: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  text-decoration: none;
}

.brand-logo__mark {
  display: block;
  flex-shrink: 0;
}

.brand-logo__bg {
  fill: var(--color-primary);
}

.brand-logo__ink {
  stroke: var(--color-white);
  fill: none;
}

.brand-logo__ink-fill {
  fill: var(--color-white);
}

.brand-logo__text {
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  white-space: nowrap;
}

.brand-logo--link:hover .brand-logo__text {
  color: var(--color-primary);
}
</style>