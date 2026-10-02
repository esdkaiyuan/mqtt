<template>
  <div class="code-block">
    <div class="code-block__header">
      <span class="code-block__lang">{{ language }}</span>
      <button class="code-block__copy" type="button" @click="handleCopy">
        {{ copied ? '已复制' : '复制' }}
      </button>
    </div>
    <!-- highlight.js 输出已对源码做实体转义（& < > 均转义），渲染的是转义后的标记 -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <pre class="code-block__pre"><code v-html="highlighted" /></pre>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import cpp from 'highlight.js/lib/languages/cpp'
import http from 'highlight.js/lib/languages/http'

// 只注册文档实际用到的四种语言，走 lib/core 避免整包引入
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('cpp', cpp)
hljs.registerLanguage('http', http)

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

const highlighted = computed(() => {
  if (!hljs.getLanguage(props.language)) return escapeHtml(props.code)
  return hljs.highlight(props.code, { language: props.language, ignoreIllegals: true }).value
})

function escapeHtml(source) {
  return source.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

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
  margin: var(--spacing-lg) 0;
  border: 1px solid var(--code-border);
  border-radius: var(--border-radius);
  overflow: hidden;
  background: var(--code-bg);
}

/* 头部与代码同底，仅用一条分隔线区分，避免浅色带压在深色底上的断层感 */
.code-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-xs) var(--spacing-md);
  background: var(--code-header-bg);
  border-bottom: 1px solid var(--code-border);
}

.code-block__lang {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--code-muted);
}

.code-block__copy {
  padding: 2px 8px;
  border: none;
  background: transparent;
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family);
  font-size: var(--font-size-xs);
  color: var(--code-muted);
  cursor: pointer;
  transition: color var(--transition-fast), background-color var(--transition-fast);
}

.code-block__copy:hover {
  color: var(--code-text);
  background: var(--code-control-hover);
}

.code-block__pre {
  margin: 0;
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--code-bg);
  color: var(--code-text);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  overflow-x: auto;
}

/* 语法着色：class 名取自 highlight.js 四种语言的输出 */
.code-block__pre :deep(.hljs-comment) {
  color: var(--code-comment);
  font-style: italic;
}

.code-block__pre :deep(.hljs-punctuation) {
  color: var(--code-muted);
}

.code-block__pre :deep(.hljs-attr),
.code-block__pre :deep(.hljs-attribute) {
  color: var(--code-key);
}

.code-block__pre :deep(.hljs-string) {
  color: var(--code-string);
}

.code-block__pre :deep(.hljs-number) {
  color: var(--code-number);
}

.code-block__pre :deep(.hljs-keyword),
.code-block__pre :deep(.hljs-literal) {
  color: var(--code-keyword);
}

.code-block__pre :deep(.hljs-type) {
  color: var(--code-type);
}

.code-block__pre :deep(.hljs-title),
.code-block__pre :deep(.hljs-built_in) {
  color: var(--code-function);
}
</style>