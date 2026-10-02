<template>
  <div class="code-block">
    <div class="code-block__header">
      <span class="code-block__lang">{{ language }}</span>
      <button class="code-block__copy" type="button" @click="handleCopy">
        {{ copied ? '已复制' : '复制' }}
      </button>
    </div>
    <pre class="code-block__pre"><code>{{ code }}</code></pre>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  code: {
    type: String,
    required: true
  },
  language: {
    type: String,
    default: 'bash'
  }
})

const copied = ref(false)
let timer = null

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.code)
    copied.value = true
    ElMessage.success('已复制到剪贴板')
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}
</script>

<style scoped>
.code-block {
  border-radius: var(--border-radius-sm);
  overflow: hidden;
  border: 1px solid var(--border-color-light);
}

.code-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-xs) var(--spacing-md);
  background: var(--color-bg);
  border-bottom: 1px solid var(--border-color-light);
}

.code-block__lang {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  text-transform: uppercase;
}

.code-block__copy {
  border: none;
  background: transparent;
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
  transition: background-color var(--transition-fast);
}

.code-block__copy:hover {
  background: var(--color-primary-light);
}

.code-block__pre {
  margin: 0;
  padding: var(--spacing-md);
  background: var(--color-text-primary);
  color: var(--color-primary-light);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  overflow-x: auto;
}
</style>