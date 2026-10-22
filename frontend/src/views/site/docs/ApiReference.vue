<template>
  <DocArticle eyebrow="开放 API" :title="current.title" :desc="current.desc">
    <!-- API 密钥 -->
    <template v-if="section === 'api-keys'">
      <h2>创建与撤销</h2>
      <p>
        仅 <strong>ADMIN</strong> 可在工作台「开发接入 → API 密钥」创建与撤销密钥（后端
        <code>/api-keys/**</code> 统一要求管理员角色）。密钥明文仅在创建时展示一次，
        请妥善保存；泄露后可在同一页面立即撤销。
      </p>

      <h2>认证方式</h2>
      <p>所有开放 API 通过请求头 <code>X-API-Key</code> 认证：</p>
      <CodeBlock language="bash" :code="authSample" />

      <h2>调用流程</h2>
      <ol>
        <li>ADMIN 创建密钥，保存一次性明文，并立即配置到调用方环境变量。</li>
        <li>调用 <code>GET /devices</code> 获取设备列表与 <code>deviceKey</code>。</li>
        <li>用 <code>deviceKey</code> 读取属性、时序、消息或日志；需要控制时调用命令或影子端点。</li>
        <li>按响应头 <code>Retry-After</code> 处理限流，失败时结合业务码定位原因。</li>
      </ol>

      <h2>权限范围</h2>
      <table>
        <thead>
          <tr><th>范围</th><th>建议用途</th></tr>
        </thead>
        <tbody>
          <tr><td><code>device:read</code></td><td>读取设备、属性、事件、时序、消息、日志、规则、OTA、场景</td></tr>
          <tr><td><code>device:write</code></td><td>下发命令、写影子 desired</td></tr>
          <tr><td><code>stats:read</code></td><td>读取平台统计信息</td></tr>
        </tbody>
      </table>
      <p>
        兼容旧版别名：<code>data:read</code> 等同 <code>device:read</code>，
        <code>data:write</code> 等同 <code>device:write</code>；统计接口仍需显式的
        <code>stats:read</code>。
      </p>
      <DocCallout type="warning" title="scope 已生效">
        密钥绑定创建者账号，<code>/external/v1</code> 的访问边界是该账号名下设备；
        <code>permissions</code> 为 JSON 数组字符串，缺少对应 scope 的请求返回 403。
        留空表示全部权限；请按最小权限分发密钥，不要暴露在前端代码、日志或公开仓库。
      </DocCallout>

      <h2>密钥安全</h2>
      <ul>
        <li>明文只在创建时展示一次，平台仅保存哈希；遗失后只能撤销并重新创建。</li>
        <li>密钥等同于账号在开放 API 上的读写能力，泄露后立即撤销。</li>
        <li>生产环境通过 HTTPS 调用，避免密钥在网络中被截获。</li>
      </ul>
    </template>

    <!-- REST API 参考 -->
    <template v-else-if="section === 'rest'">
      <h2>通用约定</h2>
      <ul>
        <li><strong>Base URL：</strong><code>/api</code>；开放 API 统一在 <code>/api/external/v1</code> 下。</li>
        <li><strong>认证：</strong>开放 API 使用 <code>X-API-Key</code>；管理端使用登录 JWT（<code>Authorization: Bearer</code>）。</li>
        <li><strong>响应：</strong>统一为 <code>{ code, message, data }</code>；<code>code=200</code> 表示成功，其余见「错误码」。</li>
        <li><strong>分页：</strong>统一使用 <code>page</code> / <code>size</code>（默认 20、上限 100）；告警列表兼容旧版 <code>pageNum</code> / <code>pageSize</code>。返回 <code>records / total / current / size</code>。</li>
        <li><strong>时间：</strong>多数字段为 ISO-8601 本地时间（如 <code>2026-10-22T10:00:00</code>）；属性时序的桶起点 <code>time</code> 为 <code>yyyy-MM-dd HH:mm:ss</code>，时区由服务端配置决定。</li>
        <li><strong>数据范围：</strong>开放 API 严格限定为密钥所属用户；访问他人设备返回 <code>2003</code>，越权资源返回 <code>403</code>。</li>
        <li><strong>读写边界：</strong>除命令下发与影子 desired 写入外，其余开放端点为只读；管理端完整功能以 Swagger 为准。</li>
      </ul>
      <p>
        管理端接口（<code>/api/**</code>）的完整参数与模型以本地 Swagger 为准：
        <code>http://localhost/api/swagger-ui.html</code>（经 Nginx 网关访问）。
      </p>

      <DocCallout type="note" title="开放 API 默认只读">
        除 <code>POST .../command</code> 与 <code>PUT .../shadow/desired</code> 外，本文档列出的开放端点为只读。
        所有请求只允许访问 API Key 所属用户名下的设备；跨账号访问返回 <code>2003</code>。
      </DocCallout>

      <div class="api-switcher" role="tablist" aria-label="API 分组">
        <button
          v-for="group in endpointGroups"
          :key="group.key"
          type="button"
          role="tab"
          :id="`api-tab-${group.key}`"
          :aria-selected="activeKey === group.key"
          :aria-controls="activeKey === group.key ? `api-panel-${group.key}` : undefined"
          :class="['api-switcher__item', { 'is-active': activeKey === group.key }]"
          @click="selectGroup(group.key)"
        >
          {{ group.title }}
          <span class="api-switcher__count">{{ group.endpoints.length }}</span>
        </button>
      </div>

      <div class="api-groups">
        <section
          v-for="group in visibleGroups"
          :id="`api-panel-${group.key}`"
          :key="group.title"
          class="api-group"
          role="tabpanel"
          :aria-labelledby="`api-tab-${group.key}`"
          tabindex="0"
        >
          <h2>{{ group.title }}</h2>
          <p v-if="group.summary" class="api-group__summary">{{ group.summary }}</p>
          <div class="api-group__list">
            <ApiEndpoint
              v-for="endpoint in group.endpoints"
              :key="endpoint.method + endpoint.path"
              :method="endpoint.method"
              :path="endpoint.path"
              :desc="endpoint.desc"
              :example="endpoint.example"
              :response="endpoint.response"
              :params="endpoint.params || []"
              :auth="endpoint.auth || 'X-API-Key'"
              :note="endpoint.note || ''"
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

      <h2>请求头与签名</h2>
      <p>
        回调使用 <code>POST</code>，请求头包含 <code>X-Event-Type</code>；配置签名密钥后还会携带
        <code>X-Signature</code>，格式为 <code>sha256=&lt;hex&gt;</code>，使用 <code>HMAC-SHA256</code>
        对原始请求体计算。接收方用同一密钥计算并比对，可防止伪造请求。
      </p>
      <CodeBlock language="bash" :code="signSample" />

      <h3>载荷结构</h3>
      <p>
        设备事件的外层统一为 <code>event / deviceKey / deviceName / deviceType / topic / status /
        payload / timestamp</code>。告警事件的业务字段位于 <code>payload</code> 中，
        由平台序列化为 JSON 字符串，接收方需再解析一次。
      </p>

      <h3>投递语义</h3>
      <ul>
        <li>回调异步投递，连接超时 5s、读取超时 10s，不阻塞 MQTT 摄取线程。</li>
        <li>非 2xx 响应或网络异常只记录日志，<strong>不会自动重试</strong>；需要补偿时请以平台数据为准做对账。</li>
        <li>接收方应快速返回 2xx，并对同一事件做幂等处理；测试按钮会同步返回目标状态码。</li>
      </ul>
      <DocCallout type="note" title="重试与超时字段">
        控制台 Webhook 表单中的「重试次数 / 超时」目前为预留配置，尚未参与实际投递；
        当前版本请以本节语义为准。
      </DocCallout>
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
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DocArticle from '@/components/docs/DocArticle.vue'
import DocCallout from '@/components/docs/DocCallout.vue'
import CodeBlock from '@/components/docs/CodeBlock.vue'
import ApiEndpoint from '@/components/docs/ApiEndpoint.vue'
import { endpointGroups, errorCodes, webhookEvents } from '@/data/apiEndpoints'

const route = useRoute()
const router = useRouter()

const sections = {
  'api-keys': { title: 'API 密钥', desc: '创建/撤销、认证方式与权限范围' },
  rest: { title: 'REST API 参考', desc: '开放 API 端点、认证、分页、请求与响应示例' },
  webhook: { title: 'Webhook 回调', desc: '事件类型、签名方式与投递语义' },
  errors: { title: '错误码', desc: '统一响应体与常见错误码处置' }
}

const section = computed(() => route.params.section || 'rest')
const current = computed(() => sections[section.value] || sections.errors)

const groupKeys = endpointGroups.map((group) => group.key)

/** 以 ?group= 保留分组，刷新或分享链接后仍定位到同一组。 */
function normalizeGroup(value) {
  const key = Array.isArray(value) ? value[0] : value
  return groupKeys.includes(key) ? key : groupKeys[0]
}

const activeKey = ref(normalizeGroup(route.query.group))
const activeGroup = computed(
  () => endpointGroups.find((group) => group.key === activeKey.value) || endpointGroups[0]
)
const visibleGroups = computed(() => [activeGroup.value])

watch(
  () => route.query.group,
  (value) => {
    activeKey.value = normalizeGroup(value)
  }
)

function selectGroup(key) {
  activeKey.value = key
  router.replace({ query: { ...route.query, group: key } })
}

const authSample = `curl -X GET \\
  https://your-server/api/external/v1/devices \\
  -H "X-API-Key: your-api-key"`

const signSample = `# 用回调密钥对原始请求体计算签名，并与 X-Signature 比对
SIGNATURE="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" -hex | sed 's/^.*= //')"
printf '%s\\n' "$SIGNATURE"`

const errorBody = `{
  "code": 404,
  "message": "资源不存在",
  "data": null
}`
</script>

<style scoped>
.api-switcher {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  margin: var(--spacing-lg) 0 var(--spacing-2xl);
}

.api-switcher__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--color-white);
  color: var(--color-text-tertiary);
  font-family: var(--font-family);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: color var(--transition-fast), border-color var(--transition-fast),
    background-color var(--transition-fast);
}

.api-switcher__item:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.api-switcher__item.is-active {
  color: var(--color-white);
  border-color: var(--color-primary);
  background: var(--color-primary);
}

.api-switcher__count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-bg);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  text-align: center;
}

.api-switcher__item.is-active .api-switcher__count {
  background: rgba(255, 255, 255, 0.2);
  color: var(--color-white);
}

.api-groups {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3xl);
}

.api-group__summary {
  margin: -4px 0 var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.api-group__list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}
</style>