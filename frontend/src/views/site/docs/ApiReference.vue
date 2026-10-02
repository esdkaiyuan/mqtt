<template>
  <DocArticle eyebrow="开放 API" :title="current.title" :desc="current.desc">
    <!-- API 密钥 -->
    <template v-if="section === 'api-keys'">
      <h2>创建与撤销</h2>
      <p>
        在工作台「开发接入 → API 密钥」中创建密钥。密钥明文仅在创建时展示一次，
        请妥善保存；泄露后可在同一页面立即撤销。
      </p>

      <h2>认证方式</h2>
      <p>所有开放 API 通过请求头 <code>X-API-Key</code> 认证：</p>
      <CodeBlock language="bash" :code="authSample" />

      <h2>权限范围</h2>
      <table>
        <thead>
          <tr><th>范围</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr><td><code>device:read</code></td><td>读取设备列表、详情与状态</td></tr>
          <tr><td><code>device:write</code></td><td>向设备下发控制指令</td></tr>
          <tr><td><code>stats:read</code></td><td>读取平台统计信息</td></tr>
        </tbody>
      </table>
    </template>

    <!-- REST API 参考 -->
    <template v-else-if="section === 'rest'">
      <p>所有接口以 <code>/api</code> 为前缀，统一返回 <code>{ code, data }</code> 结构。</p>
      <div class="api-groups">
        <section v-for="group in endpointGroups" :key="group.title" class="api-group">
          <h2>{{ group.title }}</h2>
          <div class="api-group__list">
            <ApiEndpoint
              v-for="endpoint in group.endpoints"
              :key="endpoint.method + endpoint.path"
              :method="endpoint.method"
              :path="endpoint.path"
              :desc="endpoint.desc"
              :example="endpoint.example"
              :response="endpoint.response"
            />
          </div>
        </section>
      </div>
    </template>

    <!-- Webhook 回调 -->
    <template v-else-if="section === 'webhook'">
      <h2>事件类型</h2>
      <table>
        <thead>
          <tr><th>事件</th><th>说明</th><th>载荷字段</th></tr>
        </thead>
        <tbody>
          <tr v-for="event in webhookEvents" :key="event.event">
            <td><code>{{ event.event }}</code></td>
            <td>{{ event.desc }}</td>
            <td><code>{{ event.payload }}</code></td>
          </tr>
        </tbody>
      </table>

      <h2>签名校验</h2>
      <p>
        回调请求携带 <code>X-Signature</code> 头，使用 <code>HMAC-SHA256</code> 对请求体签名。
        接收方用同一密钥计算并比对，可防止伪造请求。
      </p>
      <CodeBlock language="bash" :code="signSample" />

      <h3>重试与超时</h3>
      <ul>
        <li>回调超时时间建议 5s，接收方需快速返回 2xx。</li>
        <li>非 2xx 响应将按退避策略重试，接收方需做幂等处理。</li>
      </ul>
    </template>

    <!-- 错误码 -->
    <template v-else>
      <h2>统一响应体</h2>
      <CodeBlock language="json" :code="errorBody" />

      <h2>错误码列表</h2>
      <table>
        <thead>
          <tr><th>code</th><th>含义</th><th>处置建议</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in errorCodes" :key="item.code">
            <td><code>{{ item.code }}</code></td>
            <td>{{ item.message }}</td>
            <td>{{ item.desc }}</td>
          </tr>
        </tbody>
      </table>
    </template>
  </DocArticle>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DocArticle from '@/components/docs/DocArticle.vue'
import CodeBlock from '@/components/docs/CodeBlock.vue'
import ApiEndpoint from '@/components/docs/ApiEndpoint.vue'
import { endpointGroups, errorCodes, webhookEvents } from '@/data/apiEndpoints'

const route = useRoute()

const sections = {
  'api-keys': { title: 'API 密钥', desc: '创建/撤销、认证方式与权限范围' },
  rest: { title: 'REST API 参考', desc: '设备管理、设备控制与统计端点' },
  webhook: { title: 'Webhook 回调', desc: '事件类型、签名校验与重试策略' },
  errors: { title: '错误码', desc: '统一响应体与常见错误码处置' }
}

const section = computed(() => route.params.section || 'rest')
const current = computed(() => sections[section.value] || sections.rest)

const authSample = `curl -X GET \\
  https://your-server/api/external/v1/devices \\
  -H "X-API-Key: your-api-key"`

const signSample = `# 用回调密钥对原始请求体计算签名
echo -n "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET"`

const errorBody = `{
  "code": 404,
  "message": "资源不存在",
  "data": null
}`
</script>

<style scoped>
.api-groups {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3xl);
}

.api-group__list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}
</style>