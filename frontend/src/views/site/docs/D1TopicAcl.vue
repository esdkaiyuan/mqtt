<template>
  <DocArticle
    eyebrow="协议规范"
    title="MQTT 主题与 ACL"
    desc="设备、平台与网关的身份边界、主题模板、上下行主题与 ACL 判定规则"
  >
    <p>
      本页是平台 MQTT 主题与 ACL 的权威规范，按 <code>AclEvaluator</code> /
      <code>TopicPrefixResolver</code> / <code>MqttMessageHandler</code> 的实际代码整理。
      设备接入载荷格式见 <router-link to="/docs/protocol/d2">D2 · 设备接入协议规范</router-link>。
    </p>

    <h2>身份与认证</h2>
    <table>
      <thead>
        <tr><th>身份</th><th>用户名</th><th>凭据</th></tr>
      </thead>
      <tbody>
        <tr><td>平台后端</td><td><code>PLATFORM</code></td><td><code>PLATFORM_SECRET</code></td></tr>
        <tr><td>设备（一机一密）</td><td><code>{productKey}.{deviceKey}</code></td><td>设备密钥明文</td></tr>
        <tr><td>设备（X.509）</td><td><code>{productKey}.{deviceKey}</code></td><td>TLS 客户端证书（CN 必须等于用户名）</td></tr>
        <tr><td>设备（JWT）</td><td><code>{productKey}.{deviceKey}</code></td><td>设备 JWT（password 位）</td></tr>
        <tr><td>前端只读（兼容）</td><td><code>FRONTEND_READONLY</code></td><td>仅直连模式启用，只能订阅 <code>device/+/data</code></td></tr>
      </tbody>
    </table>
    <p>
      产品停用或设备禁用时，认证与 ACL 同时拒绝；不会出现「认证拒绝、授权放行」的分叉。
    </p>

    <h2>主题模板</h2>
    <p>
      产品字段 <code>topic_prefix</code> 定义设备命名空间，默认
      <code>device/{deviceKey}</code>。模板只允许两个占位符：
      <code>{productKey}</code> 与 <code>{deviceKey}</code>。
    </p>
    <ul>
      <li>长度 ≤ 128；禁止 <code>+</code>、<code>#</code>、空白与控制字符；尾部 <code>/</code> 会被规范化。</li>
      <li><strong>模板必须包含 <code>{deviceKey}</code></strong>，否则同产品设备共享命名空间，ACL 隔离失效。</li>
      <li><strong><code>{deviceKey}</code> 必须是最后一个占位符</strong>：平台按「倒数第二段 = deviceKey、最后一段 = 消息类型」解析上行。</li>
      <li>修改模板后设备需重连；平台共享订阅在启动时按启用产品生成。</li>
    </ul>

    <table>
      <thead>
        <tr><th>模板</th><th>上行 data</th><th>下行命令</th></tr>
      </thead>
      <tbody>
        <tr>
          <td><code>device/{deviceKey}</code></td>
          <td><code>device/sensor-01/data</code></td>
          <td><code>device/sensor-01/cmd/down</code></td>
        </tr>
        <tr>
          <td><code>{productKey}/{deviceKey}</code></td>
          <td><code>env/sensor-01/data</code></td>
          <td><code>env/sensor-01/cmd/down</code></td>
        </tr>
        <tr>
          <td><code>iot/{productKey}/{deviceKey}</code></td>
          <td><code>iot/env/sensor-01/data</code></td>
          <td><code>iot/env/sensor-01/cmd/down</code></td>
        </tr>
      </tbody>
    </table>

    <h2>Topic 清单</h2>
    <table>
      <thead>
        <tr><th>方向</th><th>Topic</th><th>QoS</th><th>说明</th></tr>
      </thead>
      <tbody>
        <tr><td>上行</td><td><code>{prefix}data</code></td><td>1</td><td>属性 / 事件上报</td></tr>
        <tr><td>上行</td><td><code>{prefix}heartbeat</code></td><td>0</td><td>心跳</td></tr>
        <tr><td>上行</td><td><code>{prefix}lwt</code></td><td>1</td><td>遗言</td></tr>
        <tr><td>上行</td><td><code>{prefix}reply</code></td><td>1</td><td>命令回执</td></tr>
        <tr><td>上行</td><td><code>{prefix}ota</code></td><td>1</td><td>OTA 进度</td></tr>
        <tr><td>下行</td><td><code>{prefix}cmd/down</code></td><td>1</td><td>属性设置 / 服务 / OTA / 点表</td></tr>
      </tbody>
    </table>
    <p>
      平台以共享订阅接收：<code>$share/{group}/{prefix 中 deviceKey 代入 +}{messageType}</code>；
      多副本的 <code>MQTT_SHARED_GROUP</code> 必须一致。
    </p>

    <h2>ACL 判定</h2>
    <p>入口：<code>POST /internal/emqx/acl</code>，请求体与响应如下。</p>
    <CodeBlock language="json" :code="aclSample" />

    <h3>设备自有命名空间</h3>
    <table>
      <thead>
        <tr><th>动作</th><th>规则</th></tr>
      </thead>
      <tbody>
        <tr><td>publish</td><td>以自身 <code>{prefix}</code> 开头，且不得进入 <code>{prefix}cmd/</code></td></tr>
        <tr><td>subscribe</td><td>只允许 <code>{prefix}cmd/</code> 下的主题</td></tr>
      </tbody>
    </table>

    <h3>网关代理子设备</h3>
    <p>主题不在网关自身命名空间时进入委托判定，必须同时满足：</p>
    <ol>
      <li>当前设备 <code>node_type=GATEWAY</code>；</li>
      <li>目标设备存在、<code>node_type=SUB</code>、未被禁用；</li>
      <li>目标设备 <code>parent_id</code> 等于当前网关设备 ID；</li>
      <li>目标主题落在目标设备自身 <code>{prefix}</code>，并按目标命名空间执行 publish / subscribe 语义。</li>
    </ol>

    <h3>平台后端账号</h3>
    <table>
      <thead>
        <tr><th>动作</th><th>规则</th></tr>
      </thead>
      <tbody>
        <tr><td>subscribe</td><td>匹配任一启用产品的前缀（设备位为 <code>+</code>）</td></tr>
        <tr><td>publish</td><td>匹配启用产品前缀，且路径包含 <code>/cmd/</code></td></tr>
      </tbody>
    </table>

    <h3>明确拒绝</h3>
    <ul>
      <li>用户名格式非法、产品不存在 / 停用、设备不存在 / 禁用；</li>
      <li>设备 publish 进入 <code>cmd/</code>，或 subscribe 到 <code>cmd/</code> 之外；</li>
      <li>网关代理目标不是当前网关的子设备、已解绑或被禁用；</li>
      <li>平台 publish 不含 <code>/cmd/</code>，或访问停用产品命名空间；</li>
      <li>未知 action、用户名 / 主题 / 动作为空。</li>
    </ul>
    <p>
      <code>ACCESS_CONTROL_ENFORCE_AUTH=false</code> 时全部放行，仅用于迁移期双轨运行。
    </p>

    <h2>命令回执</h2>
    <p>设备收到下行命令后向 <code>{prefix}reply</code> 发布：</p>
    <CodeBlock language="json" :code="replySample" />
    <p>
      <code>id</code> 必须等于下行命令 id；<code>code=200</code> 表示成功，其它值 / 缺失按失败处理。
      命令状态为 <code>PENDING → SENT → ACKED / FAILED / TIMEOUT</code>，离线属性设置先进入 <code>QUEUED</code>。
    </p>

    <h2>源码位置</h2>
    <table>
      <thead>
        <tr><th>规则</th><th>源码</th></tr>
      </thead>
      <tbody>
        <tr><td>模板校验 / 解析</td><td><code>service/TopicPrefixResolver.java</code></td></tr>
        <tr><td>ACL 判定</td><td><code>service/AclEvaluator.java</code></td></tr>
        <tr><td>用户名解析</td><td><code>service/impl/DeviceSecretServiceImpl.java</code></td></tr>
        <tr><td>上行订阅 / 解析</td><td><code>mqtt/MqttMessageHandler.java</code></td></tr>
        <tr><td>下行主题 / 载荷</td><td><code>service/impl/DeviceCommandServiceImpl.java</code></td></tr>
      </tbody>
    </table>
  </DocArticle>
</template>

<script setup>
import DocArticle from '@/components/docs/DocArticle.vue'
import CodeBlock from '@/components/docs/CodeBlock.vue'

const aclSample = `POST /api/internal/emqx/acl
X-Internal-Token: <INTERNAL_TOKEN>

{
  "username": "env.sensor-01",
  "topic": "env/sensor-01/data",
  "action": "publish"
}

// allow
{ "result": "allow" }

// deny
{ "result": "deny" }`

const replySample = `{
  "id": "6f1c2a4e-8b7d-4e2a-9c1f-3d5e7a9b0c1d",
  "code": 200,
  "data": { "handled": true }
}`
</script>
