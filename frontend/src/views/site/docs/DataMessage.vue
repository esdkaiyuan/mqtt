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
        <li>过滤：ADMIN 可见全部设备，其他角色仅可见自己拥有的设备。</li>
        <li>心跳：通道定时发送心跳，客户端据此判断连接是否存活。</li>
        <li>重连：断线后按退避策略自动重连，状态变化通过回调上报。</li>
      </ul>

      <h2>事件结构</h2>
      <CodeBlock language="json" :code="streamEvent" />
      <p>
        工作台底部状态栏展示实时通道状态（连接中 / 已连接 / 重连中 / 已关闭），
        全站仅维持一条 SSE 连接。
      </p>
    </template>

    <!-- 历史数据查询 -->
    <template v-else-if="section === 'history'">
      <h2>查询接口</h2>
      <CodeBlock language="http" :code="historyApi" />

      <h2>查询参数</h2>
      <table>
        <thead>
          <tr><th>参数</th><th>必填</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr><td><code>deviceId</code></td><td>是</td><td>设备 ID</td></tr>
          <tr><td><code>topic</code></td><td>否</td><td>Topic 关键词过滤</td></tr>
          <tr><td><code>startTime</code></td><td>否</td><td>起始时间</td></tr>
          <tr><td><code>endTime</code></td><td>否</td><td>结束时间</td></tr>
          <tr><td><code>pageNum</code></td><td>否</td><td>页码，默认 1</td></tr>
          <tr><td><code>pageSize</code></td><td>否</td><td>每页条数</td></tr>
        </tbody>
      </table>

      <h2>返回结构</h2>
      <CodeBlock language="json" :code="historyResponse" />
    </template>

    <!-- 数据格式约定 -->
    <template v-else>
      <h2>载荷建议</h2>
      <p>设备上报的 <code>payload</code> 建议使用 UTF-8 编码的 JSON，便于平台解析与展示：</p>
      <CodeBlock language="json" :code="payloadSample" />

      <h2>约定</h2>
      <ul>
        <li>时间戳统一使用秒级或毫秒级 Unix 时间，字段名建议 <code>ts</code>。</li>
        <li>数值字段使用数字类型，避免以字符串承载数值。</li>
        <li>附加信息放入 <code>metadata</code>，不影响主数据结构。</li>
        <li>单条消息体建议不超过 64KB，超大数据请分片或走对象存储。</li>
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
const current = computed(() => sections[section.value] || sections.realtime)

const streamApi = `GET /api/realtime/stream
Accept: text/event-stream
Authorization: Bearer <token>`

const streamEvent = `{
  "deviceId": 1,
  "deviceKey": "sensor-01",
  "topic": "device/sensor-01/data",
  "payload": "{\\"temperature\\": 26.5}",
  "ts": "2026-09-28 10:00:00"
}`

const historyApi = `GET /api/history?deviceId=1&topic=data&pageNum=1&pageSize=20`

const historyResponse = `{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1001,
        "deviceId": 1,
        "deviceKey": "sensor-01",
        "topic": "device/sensor-01/data",
        "payload": "{\\"temperature\\": 26.5}",
        "createdAt": "2026-09-28 10:00:00"
      }
    ],
    "total": 17849,
    "pageNum": 1,
    "pageSize": 20
  }
}`

const payloadSample = `{
  "temperature": 26.5,
  "humidity": 61,
  "ts": 1750000000,
  "metadata": { "unit": "°C", "battery": 87 }
}`
</script>