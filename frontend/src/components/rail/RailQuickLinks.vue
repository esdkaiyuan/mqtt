<template>
  <div class="rail-links">
    <section class="rail-links__section">
      <h4 class="rail-links__heading">使用说明</h4>
      <ul class="rail-links__list">
        <li>在请求头携带 <code>X-API-Key</code> 调用开放接口。</li>
        <li>密钥仅在创建时展示一次，请妥善保存。</li>
        <li>按最小权限原则为不同应用分配独立密钥。</li>
        <li>Webhook 回调使用 HMAC-SHA256 签名校验来源。</li>
      </ul>
    </section>

    <section class="rail-links__section">
      <h4 class="rail-links__heading">调用示例</h4>
      <pre class="rail-links__code"><code>curl -H "X-API-Key: $KEY" \
  {{ apiBase }}/devices</code></pre>
    </section>

    <section class="rail-links__section">
      <h4 class="rail-links__heading">相关文档</h4>
      <div class="rail-links__links">
        <a class="rail-links__link" href="/docs/api/rest" target="_blank" rel="noopener">REST API 参考</a>
        <a class="rail-links__link" href="/docs/api/webhook" target="_blank" rel="noopener">Webhook 回调</a>
      </div>
    </section>
  </div>
</template>

<script setup>
const apiBase = `${window.location.origin}/api`
</script>

<style scoped>
.rail-links__section + .rail-links__section {
  margin-top: var(--spacing-2xl);
}

.rail-links__heading {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.rail-links__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  line-height: 1.6;
}

.rail-links__list li {
  padding-left: var(--spacing-md);
  position: relative;
}

.rail-links__list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-primary);
}

.rail-links__list code {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  background: var(--color-bg);
  padding: 1px 4px;
  border-radius: var(--border-radius-sm);
  color: var(--color-text-primary);
}

.rail-links__code {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  line-height: 1.7;
  color: var(--color-text-primary);
  background: var(--color-bg);
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius-sm);
  padding: var(--spacing-md);
  overflow-x: auto;
  white-space: pre;
}

.rail-links__links {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.rail-links__link {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  text-decoration: none;
}

.rail-links__link:hover {
  color: var(--color-primary-hover);
}
</style>