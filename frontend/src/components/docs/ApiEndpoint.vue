<template>
  <div class="api-endpoint">
    <div class="api-endpoint__head">
      <span :class="['api-endpoint__method', method.toLowerCase()]">{{ method }}</span>
      <code class="api-endpoint__path">{{ path }}</code>
    </div>
    <p v-if="desc" class="api-endpoint__desc">{{ desc }}</p>

    <div v-if="example" class="api-endpoint__block">
      <div class="api-endpoint__label">请求示例</div>
      <CodeBlock :code="example" language="bash" />
    </div>

    <div v-if="response" class="api-endpoint__block">
      <div class="api-endpoint__label">响应示例</div>
      <CodeBlock :code="response" language="json" />
    </div>
  </div>
</template>

<script setup>
import CodeBlock from './CodeBlock.vue'

defineProps({
  method: {
    type: String,
    required: true
  },
  path: {
    type: String,
    required: true
  },
  desc: {
    type: String,
    default: ''
  },
  example: {
    type: String,
    default: ''
  },
  response: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.api-endpoint {
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius);
  padding: var(--spacing-lg);
  background: var(--color-white);
}

.api-endpoint__head {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.api-endpoint__method {
  padding: 3px 10px;
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-family-mono);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.api-endpoint__method.post {
  background: var(--color-success-light);
  color: var(--color-success);
}

.api-endpoint__method.put {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.api-endpoint__method.delete {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.api-endpoint__path {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.api-endpoint__desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  margin-bottom: var(--spacing-md);
}

.api-endpoint__block + .api-endpoint__block {
  margin-top: var(--spacing-md);
}

.api-endpoint__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-xs);
  font-weight: var(--font-weight-medium);
}
</style>