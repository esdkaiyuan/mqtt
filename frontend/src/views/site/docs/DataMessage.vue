<template>
  <DocArticle eyebrow="消息与数据" :title="current.title" :desc="current.desc">
    <!-- 实时消息（SSE） -->
    <template v-if="section === 'realtime'">
      <h2>通道说明</h2>
      <p>
        平台使用 <strong>SSE</strong>（<code>text/event-stream</code>）向工作台推送实时消息，
        相比 WebSocket 更契合「设备数据单向展示」的场景。
      </p>
      <CodeBlock language="http" :code="streamApi" />
      <ul>
        <li>鉴权：复用 JWT，前端使用 <code>fetch</code> + <code>ReadableStream</code> 携带 <code>Authorization</code> 头。</li>
        <li>主题过滤：可选 <code>topics</code>，取值为 <code>device-data</code> / <code>device-log</code> / <code>property-trend</code>，可重复或逗号分隔；不传表示订阅全部。</li>
        <li>可见范围：ADMIN 接收全部设备；其他角色只接收自己名下设备。分组共享设备不进入实时通道，查看其数据请走属性历史或设备详情接口。</li>
        <li>心跳：通道定时发送 SSE 注释帧 <code>hb</code>，客户端据此判断连接是否存活。</li>
        <li>重连：断线后按退避策略自动重连，状态变化通过回调上报。</li>
      </ul>

      <h2>事件类型</h2>
      <table>
        <thead>
          <tr><th>事件名</th><th>用途</th><th>载荷</th></tr>
        </thead>
        <tbody>
          <tr><td><code>device-data</code></td><td>设备上行消息</td><td><code>deviceId / deviceKey / topic / payload</code></td></tr>
          <tr><td><code>device-log</code></td><td>设备日志增量</td><td><code>deviceId / deviceKey / occurredAt</code></td></tr>
          <tr><td><code>property-trend</code></td><td>属性趋势增量</td><td><code>deviceId / deviceKey / samples[]</code></td></tr>
        </tbody>
      </table>
      <p><code>payload</code> 是设备原始载荷字符串；<code>occurredAt</code> 为毫秒时间戳；<code>samples</code> 每项包含 <code>identifier / dataType / valueText / reportedAt</code>。</p>

      <h2>事件示例</h2>
      <CodeBlock language="http" :code="streamEvents" />
      <p>
        工作台底部状态栏展示实时通道状态（连接中 / 已连接 / 重连中 / 已关闭），
        全站仅维持一条 SSE 连接。
      </p>
    </template>

    <!-- 历史数据查询 -->
    <template v-else-if="section === 'history'">
      <h2>查询接口</h2>
      <CodeBlock language="http" :code="historyApi" />
      <p>
        该接口只返回当前登录用户名下设备的原始消息记录；分组共享设备请使用设备详情中的属性历史或日志。
      </p>

      <h2>查询参数</h2>
      <table>
        <thead>
          <tr><th>参数</th><th>必填</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr><td><code>deviceId</code></td><td>否</td><td>设备 ID；不传返回当前账号名下全部设备</td></tr>
          <tr><td><code>topic</code></td><td>否</td><td>Topic 关键词过滤</td></tr>
          <tr><td><code>startTime</code></td><td>否</td><td>起始时间，格式 <code>yyyy-MM-dd HH:mm:ss</code></td></tr>
          <tr><td><code>endTime</code></td><td>否</td><td>结束时间，格式 <code>yyyy-MM-dd HH:mm:ss</code></td></tr>
          <tr><td><code>pageNum</code></td><td>否</td><td>页码，默认 1</td></tr>
          <tr><td><code>pageSize</code></td><td>否</td><td>每页条数，默认 20</td></tr>
        </tbody>
      </table>

      <h2>返回结构</h2>
      <CodeBlock language="json" :code="historyResponse" />
    </template>

    <!-- 数据格式约定 -->
    <template v-else>
      <h2>载荷格式</h2>
      <p>
        属性 / 事件上报必须使用 Alink 信封（<code>method + params</code>）。平铺的
        <code>{"temperature":26.5}</code> 会被物模型解析判为无法解析。
        完整字段与类型规则见 <router-link to="/docs/protocol/d2">D2 · 设备接入协议规范</router-link>。
      </p>
      <CodeBlock language="json" :code="payloadSample" />

      <h2>约定</h2>
      <ul>
        <li>属性 / 事件时间以平台收到消息的时间为准，payload 内的 <code>ts</code> 仅作原始载荷保留。</li>
        <li>数值字段使用 JSON number，避免以字符串承载数值；布尔字段用 boolean。</li>
        <li>单条消息体建议不超过 64KB；HTTP 上报超过 <code>HTTP_INGEST_MAX_PAYLOAD_BYTES</code>（默认 65536）会返回 413。</li>
      </ul>
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
  realtime: { title: '实时消息（SSE）', desc: 'SSE 通道、事件结构与重连策略' },
  history: { title: '历史数据查询', desc: '查询参数、分页与返回结构' },
  format: { title: '数据格式约定', desc: 'JSON 载荷、时间戳与元数据建议' }
}

const section = computed(() => route.params.section || 'realtime')
const current = computed(() => sections[section.value] || sections.format)

const streamApi = `GET /api/realtime/stream?topics=device-data,device-log
Accept: text/event-stream
Authorization: Bearer <token>`

const streamEvents = `event: device-data
data: {"deviceId":1,"deviceKey":"sensor-01","topic":"device/sensor-01/data","payload":"{\\"method\\":\\"thing.event.property.post\\",\\"params\\":{\\"temperature\\":26.5}}"}

event: device-log
data: {"deviceId":1,"deviceKey":"sensor-01","occurredAt":1750000000000}

event: property-trend
data: {"deviceId":1,"deviceKey":"sensor-01","samples":[{"identifier":"temperature","dataType":"float","valueText":"26.5","reportedAt":"2026-10-22T10:00:00"}]}`

const historyApi = `GET /api/history?deviceId=1&topic=data&pageNum=1&pageSize=20`

const historyResponse = `{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1001,
        "deviceId": 1,
        "topic": "device/sensor-01/data",
        "payload": "{\\"method\\":\\"thing.event.property.post\\",\\"params\\":{\\"temperature\\":26.5}}",
        "timestamp": "2026-10-22T10:00:00"
      }
    ],
    "total": 17849,
    "size": 20,
    "current": 1,
    "pages": 893
  }
}`

const payloadSample = `{
  "id": "msg-20261021-0001",
  "version": "1.0",
  "method": "thing.event.property.post",
  "params": {
    "temperature": 26.5,
    "humidity": 61,
    "power": true
  }
}`
</script>