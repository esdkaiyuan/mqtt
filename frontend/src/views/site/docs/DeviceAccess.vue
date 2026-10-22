<template>
  <DocArticle eyebrow="设备接入" :title="current.title" :desc="current.desc">
    <!-- 设备身份 -->
    <template v-if="section === 'identity'">
      <h2>一机一密</h2>
      <p>
        平台采用「一机一密」：每台设备拥有独立的 <code>device_secret</code>，
        数据库只保存其 BCrypt 哈希，明文仅在「创建设备」与「重置密钥」时返回一次。
      </p>

      <h2>凭据格式</h2>
      <table>
        <thead>
          <tr><th>字段</th><th>取值</th></tr>
        </thead>
        <tbody>
          <tr><td>用户名</td><td><code>{productKey}.{deviceKey}</code>，如 <code>esp32-fall-detect.sensor-01</code></td></tr>
          <tr><td>密码</td><td><code>device_secret</code> 明文（32 位 base62 随机串）</td></tr>
        </tbody>
      </table>

      <h2>认证回调</h2>
      <p>
        EMQX 通过 HTTP 回调请求后端 <code>POST /internal/emqx/auth</code> 校验设备身份，
        该接口仅容器网络内可达，外部访问被 Nginx 拒绝。
      </p>
      <CodeBlock language="json" :code="authResponse" />

      <h3>平台账号</h3>
      <p>
        后端自身连接 Broker 使用保留用户名 <code>PLATFORM</code>，密码取自平台密钥配置；
        产品标识与设备标识的命名规则禁用该名称。
      </p>
    </template>

    <!-- MQTT 连接参数 -->
    <template v-else-if="section === 'mqtt'">
      <h2>连接参数</h2>
      <table>
        <thead>
          <tr><th>参数</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr><td>Broker 地址</td><td>部署服务器 IP 或域名</td></tr>
          <tr><td>端口</td><td>1883（MQTT TCP）；8883（TLS）；8083（WebSocket，默认均随 compose 暴露）</td></tr>
          <tr><td>ClientId</td><td>建议使用 <code>{deviceKey}</code>，保证唯一</td></tr>
          <tr><td>用户名</td><td><code>{productKey}.{deviceKey}</code></td></tr>
          <tr><td>密码</td><td><code>device_secret</code></td></tr>
          <tr><td>QoS</td><td>数据 1、心跳 0、遗言 1</td></tr>
          <tr><td>保活</td><td>建议 60s，需小于遗言触发窗口</td></tr>
        </tbody>
      </table>

      <h2>连接示例</h2>
      <CodeBlock language="bash" :code="mqttSample" />
      <p>设备需设置遗言（LWT）到 <code>device/{deviceKey}/lwt</code>，用于离线判定。</p>
    </template>

    <!-- HTTP 上报 -->
    <template v-else-if="section === 'http'">
      <h2>端点</h2>
      <p>
        不具备 MQTT 能力的设备可直接用 HTTP 上报，端点与方法如下：
      </p>
      <table>
        <thead>
          <tr><th>方法</th><th>路径</th></tr>
        </thead>
        <tbody>
          <tr><td>POST</td><td><code>/api/ingest/{deviceKey}/{messageType}</code></td></tr>
        </tbody>
      </table>
      <p>
        请求体为设备原始载荷，后端<strong>不做预解析</strong>，原样交回摄取管线。
        与 MQTT 一致，HTTP 上报同样经过鉴权、转换、分片与落库，最终属性最新值与 MQTT 完全等价。
      </p>

      <h2>鉴权</h2>
      <p>
        使用 HTTP Basic 鉴权，用户名与密码沿用设备凭据：
      </p>
      <table>
        <thead>
          <tr><th>字段</th><th>取值</th></tr>
        </thead>
        <tbody>
          <tr><td>用户名</td><td><code>{productKey}.{deviceKey}</code></td></tr>
          <tr><td>密码</td><td><code>device_secret</code></td></tr>
          <tr><td>请求头</td><td><code>Authorization: Basic base64("{productKey}.{deviceKey}:{device_secret}")</code></td></tr>
        </tbody>
      </table>

      <h2>messageType</h2>
      <table>
        <thead>
          <tr><th>取值</th><th>含义</th><th>合成 Topic</th><th>QoS</th></tr>
        </thead>
        <tbody>
          <tr><td><code>data</code></td><td>属性上报</td><td><code>device/{deviceKey}/data</code></td><td>1</td></tr>
          <tr><td><code>heartbeat</code></td><td>心跳</td><td><code>device/{deviceKey}/heartbeat</code></td><td>0</td></tr>
          <tr><td><code>lwt</code></td><td>遗言</td><td><code>device/{deviceKey}/lwt</code></td><td>1</td></tr>
        </tbody>
      </table>
      <p>其它取值一律返回 <code>400</code>（错误码 <code>6247</code>）。<code>heartbeat</code> 与 <code>lwt</code> 允许空请求体。</p>

      <h2>请求示例</h2>
      <CodeBlock language="bash" :code="httpSample" />

      <h2>成功响应</h2>
      <CodeBlock language="json" :code="httpSuccessResponse" />
      <p><code>accepted</code> 恒为 <code>true</code>，表示记录已进入摄取管线。</p>

      <h2>错误码</h2>
      <table>
        <thead>
          <tr><th>HTTP</th><th>错误码</th><th>含义</th></tr>
        </thead>
        <tbody>
          <tr><td>401</td><td><code>6246</code></td><td>设备凭据无效（缺失、非 Basic、用户名不符、设备不存在/停用/禁用、密钥错误）</td></tr>
          <tr><td>400</td><td><code>6247</code></td><td>messageType 不在白名单</td></tr>
          <tr><td>413</td><td><code>6248</code></td><td>请求体超过 <code>max-payload-bytes</code> 限制</td></tr>
        </tbody>
      </table>

      <h2>安全提示</h2>
      <p>
        生产环境<strong>必须启用 TLS</strong>（HTTPS），Basic 凭据在明文信道下会泄露。
        建议在 Nginx 侧统一终止 TLS，并仅对设备网段开放该端点。
      </p>
    </template>

    <!-- Topic 与 ACL -->
    <template v-else>
      <h2>主题规划</h2>
      <table>
        <thead>
          <tr><th>主题</th><th>方向</th><th>QoS</th></tr>
        </thead>
        <tbody>
          <tr><td><code>{topic_prefix}/data</code></td><td>设备上行数据</td><td>1</td></tr>
          <tr><td><code>{topic_prefix}/heartbeat</code></td><td>心跳</td><td>0</td></tr>
          <tr><td><code>{topic_prefix}/lwt</code></td><td>遗言</td><td>1</td></tr>
          <tr><td><code>{topic_prefix}/cmd/#</code></td><td>平台下行命令</td><td>1</td></tr>
        </tbody>
      </table>
      <p>默认 <code>topic_prefix = device/{deviceKey}</code>。</p>

      <h2>ACL 规则</h2>
      <table>
        <thead>
          <tr><th>身份</th><th>publish</th><th>subscribe</th></tr>
        </thead>
        <tbody>
          <tr><td>设备</td><td>自身 <code>{prefix}</code> 下，且不得进入 <code>{prefix}cmd/</code></td><td>仅 <code>{prefix}cmd/</code></td></tr>
          <tr><td>平台后端账号</td><td>启用产品前缀下且路径含 <code>/cmd/</code></td><td>启用产品前缀（设备位为 <code>+</code>）</td></tr>
          <tr><td>网关代理子设备</td><td>目标须为当前网关的 <code>SUB</code> 子设备</td><td>同上，按目标设备命名空间判定</td></tr>
        </tbody>
      </table>
      <p>
        授权由 <code>POST /internal/emqx/acl</code> 判定。完整规则（含自定义模板、网关委托与拒绝清单）见
        <router-link to="/docs/protocol/d1">D1 · MQTT 主题与 ACL</router-link>。
      </p>
    </template>
  </DocArticle>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DocArticle from '@/components/docs/DocArticle.vue'
import CodeBlock from '@/components/docs/CodeBlock.vue'

const route = useRoute()

const sections = {
  identity: { title: '设备身份', desc: '一机一密、凭据格式与认证回调' },
  mqtt: { title: 'MQTT 连接参数', desc: 'Broker 地址、ClientId、QoS 与保活约定' },
  http: { title: 'HTTP 上报', desc: 'REST 端点、Basic 鉴权与 messageType' },
  'topic-acl': { title: 'Topic 与 ACL', desc: '上报/下发主题模板与权限边界' }
}

const section = computed(() => route.params.section || 'identity')
const current = computed(() => sections[section.value] || sections['topic-acl'])

const authResponse = `// 认证成功
{"result": "allow", "is_superuser": false}

// 认证失败
{"result": "deny"}`

const mqttSample = `# 用户名：{productKey}.{deviceKey}
# 密码：  device_secret
mosquitto_sub \\
  -h your-server -p 1883 \\
  -u "esp32-fall-detect.sensor-01" -P "<device_secret>" \\
  -t "device/sensor-01/cmd/#" \\
  -q 1`

const httpSample = `# 冒号前为 {productKey}.{deviceKey}，冒号后为 device_secret
curl -X POST "https://your-server/api/ingest/sensor-temp-001/data" \\
  -u "esp32-fall-detect.sensor-temp-001:<device_secret>" \\
  -H "Content-Type: application/json" \\
  -d '{"method":"thing.event.property.post","params":{"temperature":26.5}}'`

const httpSuccessResponse = `{
  "code": 200,
  "message": "成功",
  "data": {
    "deviceKey": "sensor-temp-001",
    "messageType": "data",
    "topic": "device/sensor-temp-001/data",
    "receivedAt": "2026-10-04T12:00:00",
    "accepted": true
  }
}`
</script>