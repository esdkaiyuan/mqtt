<template>
  <article class="doc-article">
    <header class="doc-article__head">
      <p class="doc-article__eyebrow">{{ eyebrow }}</p>
      <h1 class="doc-article__title">{{ title }}</h1>
      <p v-if="desc" class="doc-article__desc">{{ desc }}</p>
    </header>

    <div class="doc-article__body">
      <slot />
    </div>
  </article>
</template>

<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  desc: { type: String, default: '' }
})
</script>

<style scoped>
.doc-article {
  padding-bottom: var(--spacing-4xl);
}

.doc-article__head {
  padding-bottom: var(--spacing-2xl);
  margin-bottom: var(--spacing-2xl);
  border-bottom: 1px solid var(--color-border-light);
}

.doc-article__eyebrow {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--spacing-sm);
}

.doc-article__title {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  line-height: 1.3;
}

.doc-article__desc {
  margin-top: var(--spacing-md);
  font-size: var(--font-size-lg);
  color: var(--color-text-tertiary);
  line-height: 1.7;
}

.doc-article__body :deep(h2) {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: var(--spacing-3xl) 0 var(--spacing-md);
}

.doc-article__body :deep(h3) {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: var(--spacing-2xl) 0 var(--spacing-sm);
}

.doc-article__body :deep(p) {
  font-size: var(--font-size-md);
  color: var(--color-text-regular);
  line-height: 1.9;
  margin-bottom: var(--spacing-md);
}

.doc-article__body :deep(ul),
.doc-article__body :deep(ol) {
  margin: 0 0 var(--spacing-md);
  padding-left: var(--spacing-2xl);
  color: var(--color-text-regular);
  font-size: var(--font-size-md);
  line-height: 1.9;
}

.doc-article__body :deep(li) {
  margin-bottom: var(--spacing-xs);
}

.doc-article__body :deep(a) {
  color: var(--color-primary);
  text-decoration: none;
}

.doc-article__body :deep(a:hover) {
  text-decoration: underline;
}

/* 仅行内代码。排除 <pre> 内的代码，否则会给代码块的 <code> 套上浅灰底与圆角内边距，
   与 CodeBlock 的深色 <pre> 叠加成「深色框里嵌浅色块」的突兀观感 */
.doc-article__body :deep(:not(pre) > code) {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
  background: var(--color-bg);
  color: var(--color-text-primary);
}

.doc-article__body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: var(--spacing-lg);
  font-size: var(--font-size-sm);
}

.doc-article__body :deep(th),
.doc-article__body :deep(td) {
  text-align: left;
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--color-border-light);
  color: var(--color-text-regular);
}

.doc-article__body :deep(th) {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-semibold);
  background: var(--color-bg);
}
</style>