<template>
  <DocArticle
    eyebrow="协议规范"
    title="设备接入协议"
    desc="Alink 属性 / 事件信封、命令回执、OTA 进度、点表下发与 Modbus 主站采集口径"
  >
    <p>
      本页是设备接入载荷格式的权威规范，按 <code>ThingModelInterpretServiceImpl</code> /
      <code>DeviceCommandServiceImpl</code> / <code>PointTablePayloadBuilder</code> /
      <code>ModbusValueConverter</code> 的实际代码整理。主题与 ACL 见
      <router-link to="/docs/protocol/d1">D1 · MQTT 主题与 ACL</router-link>。
    </p>

    <h2>传输方式</h2>
    <table>
      <thead>
        <tr><th>方式</th><th>上行类型</th><th>认证</th></tr>
      </thead>
      <tbody>
        <tr><td>MQTT</td><td>data / heartbeat / lwt / reply / ota</td><td>SECRET / X.509 / JWT</td></tr>
        <tr><td>HTTP</td><td>data / heartbeat / lwt</td><td>HTTP Basic，最大 64KB</td></tr>
        <tr><td>平台 Modbus 主站</td><td>平台代为组装 data</td><td>产品 driver_config</td></tr>
      </tbody>
    </table>
    <p>
      HTTP 合成 Topic 固定为 <code>device/{deviceKey}/{messageType}</code>，不随产品模板变化。
    </p>

    <h2>通用 Alink 信封</h2>
    <CodeBlock language="json" :code="envelopeSample" />
    <table>
      <thead>
        <tr><th>字段</th><th>必填</th><th>说明</th></tr>
      </thead>
      <tbody>
        <tr><td><code>id</code></td><td>回执必填</td><td>属性 / 事件可省略；回执必须等于下行命令 id</td></tr>
        <tr><td><code>version</code></td><td>否</td><td>建议 <code>1.0</code>；解析不校验</td></tr>
        <tr><td><code>method</code></td><td>属性 / 事件必填</td><td><code>thing.event.property.post</code> / <code>thing.event.post</code></td></tr>
        <tr><td><code>params</code></td><td>是</td><td>属性映射 / 事件体，必须是对象</td></tr>
      </tbody>
    </table>
    <p>
      <strong>时间口径</strong>：平台使用消息到达时间作为属性 / 事件时间，<strong>不读取 payload 内的 <code>ts</code></strong>。
    </p>

    <h2>属性上报</h2>
    <CodeBlock language="json" :code="propertySample" />
    <table>
      <thead>
        <tr><th>TSL 类型</th><th>JSON 值</th></tr>
      </thead>
      <tbody>
        <tr><td>int</td><td>整数 number，不允许小数</td></tr>
        <tr><td>float / double</td><td>number</td></tr>
        <tr><td>bool</td><td>boolean，或 0/1</td></tr>
        <tr><td>text</td><td>string，受长度约束</td></tr>
        <tr><td>enum</td><td>string，必须命中枚举键</td></tr>
        <tr><td>date</td><td>标量文本，建议 <code>yyyy-MM-dd HH:mm:ss</code></td></tr>
        <tr><td>struct / array</td><td>object / array</td></tr>
      </tbody>
    </table>
    <p>
      <strong>数值必须用 JSON number，不能用字符串承载</strong>；未知 identifier 或非法值会被忽略，
      同一报文中其它合法字段继续生效。
    </p>

    <h2>事件上报</h2>
    <CodeBlock language="json" :code="eventSample" />
    <p>
      <code>params.eventId</code> 必须在 TSL 中存在；<code>params.value</code> 原样写入事件
      <code>output_data</code>。事件类型取自 TSL，不取载荷。
    </p>

    <h2>命令回执</h2>
    <CodeBlock language="json" :code="replySample" />
    <p>
      <code>id</code> 为下行命令 id；<code>code=200</code> 成功，其它值 / 缺失按失败处理；
      回执只更新命令记录，不进入物模型解析。
    </p>

    <h2>OTA 进度</h2>
    <CodeBlock language="json" :code="otaSample" />
    <p>
      <code>version</code> 与 <code>status</code> 必填；<code>status</code> 仅允许
      downloading / flashing / verifying / success / failed；进度单调不减；<code>fileMd5</code>
      为可选 32 位十六进制，回传且不一致判失败。
    </p>

    <h2>平台下行命令</h2>
    <CodeBlock language="json" :code="commandSample" />
    <table>
      <thead>
        <tr><th>命令</th><th>method</th></tr>
      </thead>
      <tbody>
        <tr><td>属性设置</td><td><code>thing.service.property.set</code></td></tr>
        <tr><td>服务调用</td><td><code>thing.service.{identifier}</code></td></tr>
        <tr><td>OTA（保留）</td><td><code>thing.service.ota_upgrade</code></td></tr>
        <tr><td>点表下发（保留）</td><td><code>thing.service.point_table_dispatch</code></td></tr>
      </tbody>
    </table>
    <p>
      Topic 为 <code>{prefix}cmd/down</code>，QoS 1；属性设置同时写影子 desired，离线设备入队补发。
    </p>

    <h2>点表下发载荷</h2>
    <CodeBlock language="json" :code="pointTableSample" />
    <table>
      <thead>
        <tr><th>字段</th><th>说明</th></tr>
      </thead>
      <tbody>
        <tr><td><code>deviceId</code></td><td>设备 ID，防止点表串设备</td></tr>
        <tr><td><code>version</code></td><td>单调递增；Redis INCR，退化进程内 <code>1e12+n</code>（仅单副本单调）</td></tr>
        <tr><td><code>dispatchedAt</code></td><td>Unix 毫秒</td></tr>
        <tr><td><code>points[]</code></td><td>启用中的 EDGE 点位；空点表拒绝下发</td></tr>
        <tr><td><code>unitId</code></td><td>从站地址 0~247；留空继承驱动配置，多从站点位可混合下发</td></tr>
        <tr><td><code>scale</code> / <code>offset</code></td><td>字符串承载，保留 DECIMAL 精度</td></tr>
        <tr><td><code>readIntervalMs</code></td><td>采集周期；边缘侧应有默认值</td></tr>
      </tbody>
    </table>
    <p>
      设备必须按 <code>version</code> 丢弃旧载荷；回执向 <code>{prefix}reply</code> 发布
      <code>{ "id": "...", "code": 200 }</code>。
    </p>

    <h2>Modbus 主站采集</h2>
    <p>产品 <code>protocol_type=MODBUS</code>，点表 <code>executor=PLATFORM</code>。</p>
    <CodeBlock language="json" :code="driverConfigSample" />
    <table>
      <thead>
        <tr><th>dataType</th><th>寄存器宽度</th><th>解释</th></tr>
      </thead>
      <tbody>
        <tr><td>int</td><td>1</td><td>有符号 16 位</td></tr>
        <tr><td>int32 / uint32</td><td>2</td><td>有符号 / 无符号 32 位</td></tr>
        <tr><td>int64 / uint64</td><td>4</td><td>有符号 / 无符号 64 位；uint64 输出无符号十进制</td></tr>
        <tr><td>float</td><td>2</td><td>32 位 IEEE-754</td></tr>
        <tr><td>double</td><td>4</td><td>64 位 IEEE-754</td></tr>
        <tr><td>bool</td><td>1</td><td>非 0 为 true</td></tr>
        <tr><td>string</td><td>1</td><td>2 字节 ASCII，遇 NUL 截断</td></tr>
      </tbody>
    </table>
    <p>
      工程值 = 原始值 × scale + offset；bool / string 不换算。32 / 64 位数值由产品
      <code>driver_config.wordOrder</code> 决定字序，支持 <code>ABCD</code> / <code>CDAB</code> /
      <code>BADC</code> / <code>DCBA</code>，16 位类型不受影响。平台采集成功后组装 Alink 属性信封投入
      同一摄取管线，单点失败只写 <code>device_point_run=FAILED</code>。
    </p>
    <p>
      多从站：点位级 <code>unitId</code> 非空时覆盖驱动默认值；采集器按
      <code>(pointType, unitId)</code> 分组批量读取，避免同一次请求跨从站。点表下发载荷会原样携带
      <code>unitId</code>，网关侧应按该字段切换目标从站。
    </p>

    <h2>与旧文档的差异</h2>
    <table>
      <thead>
        <tr><th>旧文档</th><th>以本规范为准</th></tr>
      </thead>
      <tbody>
        <tr><td>平铺 <code>{"temperature":26.5}</code></td><td>必须使用 Alink 信封 <code>method + params</code></td></tr>
        <tr><td>payload <code>ts</code> 作为时间</td><td>平台使用服务端收到时间</td></tr>
        <tr><td>下行固定 <code>device/{key}/cmd/down</code></td><td>按产品模板解析为 <code>{prefix}cmd/down</code></td></tr>
        <tr><td>自定义模板只影响订阅</td><td>同时影响上行解析与下行发布</td></tr>
      </tbody>
    </table>

    <h2>源码位置</h2>
    <table>
      <thead>
        <tr><th>规则</th><th>源码</th></tr>
      </thead>
      <tbody>
        <tr><td>属性 / 事件解析</td><td><code>service/impl/ThingModelInterpretServiceImpl.java</code></td></tr>
        <tr><td>类型校验</td><td><code>service/impl/ThingModelParamValidator.java</code></td></tr>
        <tr><td>下行命令</td><td><code>service/impl/DeviceCommandServiceImpl.java</code></td></tr>
        <tr><td>命令回执</td><td><code>service/impl/CommandReplyServiceImpl.java</code></td></tr>
        <tr><td>点表载荷</td><td><code>collect/PointTablePayloadBuilder.java</code></td></tr>
        <tr><td>Modbus 换算</td><td><code>collect/modbus/ModbusValueConverter.java</code></td></tr>
      </tbody>
    </table>
  </DocArticle>
</template>

<script setup>
import DocArticle from '@/components/docs/DocArticle.vue'
import CodeBlock from '@/components/docs/CodeBlock.vue'

const envelopeSample = `{
  "id": "msg-001",
  "version": "1.0",
  "method": "thing.event.property.post",
  "params": { "temperature": 26.5 }
}`

const propertySample = `{
  "id": "msg-20261021-0001",
  "version": "1.0",
  "method": "thing.event.property.post",
  "params": {
    "temperature": 26.5,
    "humidity": 61,
    "power": true,
    "mode": "auto"
  }
}`

const eventSample = `{
  "id": "evt-001",
  "version": "1.0",
  "method": "thing.event.post",
  "params": {
    "eventId": "overheat",
    "value": { "temperature": 91.2, "level": "critical" }
  }
}`

const replySample = `{
  "id": "6f1c2a4e-8b7d-4e2a-9c1f-3d5e7a9b0c1d",
  "code": 200,
  "data": { "handled": true }
}`

const otaSample = `{
  "version": "1.2.0",
  "status": "downloading",
  "progress": 42,
  "fileMd5": "d41d8cd98f00b204e9800998ecf8427e",
  "message": "downloading firmware"
}`

const commandSample = `{
  "id": "6f1c2a4e-8b7d-4e2a-9c1f-3d5e7a9b0c1d",
  "version": "1.0",
  "method": "thing.service.property.set",
  "params": { "power": true }
}`

const pointTableSample = `{
  "deviceId": 101,
  "version": 17,
  "dispatchedAt": 1790000000000,
  "points": [
    {
      "pointKey": "temperature",
      "pointType": "HOLDING_REGISTER",
      "address": "40001",
      "dataType": "float",
      "unitId": 1,
      "scale": "0.1",
      "offset": "0",
      "unit": "°C",
      "readIntervalMs": 5000
    }
  ]
}`

const driverConfigSample = `{
  "host": "192.168.10.20",
  "port": 502,
  "unitId": 1,
  "wordOrder": "ABCD",
  "connectTimeoutMs": 3000,
  "readTimeoutMs": 3000,
  "retry": 1
}`
</script>
