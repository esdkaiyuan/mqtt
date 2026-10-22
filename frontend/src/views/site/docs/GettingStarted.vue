<template>
  <DocArticle eyebrow="快速开始" :title="current.title" :desc="current.desc">
    <!-- 平台简介 -->
    <template v-if="section === 'overview'">
      <h2>平台能力</h2>
      <p>
        MQTT Cloud 是一套开源自部署的物联网设备管理平台。基于 EMQX 提供 MQTT 接入，
        后端负责设备身份认证、权限控制、物模型解析、数据落库与开放 API，前端提供工作台、官网与公开文档站。
      </p>
      <ul>
        <li>设备接入：产品 / 物模型、一机一密、X.509 / JWT、HTTP 上报、多级网关代理与平台侧 Modbus 主站。</li>
        <li>控制与影子：物模型校验的命令下发、desired / reported / delta 影子、离线补发与多设备聚合。</li>
        <li>数据与可观测：属性时序、消息历史、设备统一日志、SSE 实时推送与 CSV 导出。</li>
        <li>规则与告警：多条件多动作规则、四档作用域；阈值窗口聚合、离线 / 事件告警、升级、分派与多渠道通知。</li>
        <li>场景联动：属性 / 事件 / 定时 / 上下线 / 影子变更触发，分支、循环、并行汇合与版本回滚。</li>
        <li>运维与可视化：OTA 灰度分批、看板与公开大屏、快速上位机、设备调试中心。</li>
        <li>开放 API：API Key 认证的只读集成与命令 / 影子写能力，Webhook 回调。</li>
      </ul>

      <h2>架构一览</h2>
      <table>
        <thead>
          <tr><th>组件</th><th>职责</th></tr>
        </thead>
        <tbody>
          <tr><td>EMQX</td><td>MQTT Broker，负责连接、认证回调与授权回调</td></tr>
          <tr><td>backend</td><td>Spring Boot 服务：认证授权、设备管理、消息落库、开放 API</td></tr>
          <tr><td>MySQL</td><td>设备、产品、消息等持久化存储</td></tr>
          <tr><td>Redis</td><td>设备认证元数据缓存、在线状态、命令配额与会话相关键</td></tr>
          <tr><td>frontend</td><td>Vue 3 工作台、官网与文档站，Nginx 承载静态资源与反向代理</td></tr>
          <tr><td>Modbus 现场设备</td><td>可选；由平台主站按点表轮询，或由边缘网关转换后走 HTTP / MQTT</td></tr>
        </tbody>
      </table>

      <h2>名词表</h2>
      <table>
        <thead>
          <tr><th>名词</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr><td>产品（Product）</td><td>设备模板，定义协议与 Topic 约定</td></tr>
          <tr><td>设备（Device）</td><td>一台具体设备，拥有唯一 deviceKey 与一机一密</td></tr>
          <tr><td>deviceKey</td><td>设备唯一标识，用于 MQTT ClientId 与 Topic 拼接</td></tr>
          <tr><td>Topic</td><td>消息通道，设备上报与平台下发各有约定模板</td></tr>
        </tbody>
      </table>
    </template>

    <!-- 登录与工作台 -->
    <template v-else-if="section === 'workbench'">
      <h2>账号体系</h2>
      <p>平台使用 JWT 进行登录鉴权，账号由管理员创建或自助注册。角色分为：</p>
      <table>
        <thead>
          <tr><th>角色</th><th>权限范围</th></tr>
        </thead>
        <tbody>
          <tr><td>ADMIN</td><td>全部功能，含产品、设备、规则、场景、OTA、API 密钥、Webhook 与用户管理</td></tr>
          <tr><td>OPERATOR</td><td>设备管理、命令下发、规则 / 场景 / OTA 运维、实时消息与历史查询</td></tr>
          <tr><td>VIEWER</td><td>只读查看设备、数据、日志与看板；不能下发命令 / 写影子</td></tr>
        </tbody>
      </table>

      <h2>工作台导览</h2>
      <ul>
        <li><strong>顶栏</strong>：折叠导航、品牌入口、面包屑、通知中心与用户菜单。</li>
        <li><strong>左导航</strong>：按「监控 / 自动化 / 告警 / 设备运维 / 开发接入」分组的功能入口，可折叠为图标态。</li>
        <li><strong>内容区</strong>：当前页面主体，统一由页头 + 内容卡片构成。</li>
        <li><strong>右上下文栏</strong>：随路由切换的辅助面板（健康度、设备摘要、Topic 过滤、快捷链接）。</li>
        <li><strong>底部状态栏</strong>：实时通道状态、Broker 状态、在线设备数与版本。</li>
      </ul>
      <p>登录入口为 <code>/login</code>，登录成功后进入 <code>/workbench/dashboard</code>。</p>

      <h2>按角色上手</h2>
      <ul>
        <li><strong>ADMIN</strong>：先创建产品与物模型，再配置设备、规则 / 场景、OTA 与开放 API；负责成员与密钥管理。</li>
        <li><strong>OPERATOR</strong>：日常看设备状态、下发命令、处理告警、执行 / 推进 OTA 任务、查看日志。</li>
        <li><strong>VIEWER</strong>：查看实时数据、历史趋势、设备日志与看板；可导出 CSV，但不能写设备。</li>
      </ul>
    </template>

    <!-- 接入第一个设备 -->
    <template v-else>
      <h2>六步接入</h2>
      <ol>
        <li>登录工作台，进入「产品管理」。</li>
        <li>创建产品并录入物模型（属性 / 事件 / 服务），确定 Topic 约定。</li>
        <li>在产品下创建设备，系统生成唯一 deviceKey。</li>
        <li>获取设备的一机一密凭据（用户名 / 密码），或按产品认证方式使用 X.509 / JWT。</li>
        <li>设备使用凭据连接 Broker 并发布数据，工作台即可看到实时消息。</li>
        <li>按需配置点表、分组、规则 / 告警 / 场景、OTA 与看板。</li>
      </ol>

      <h3>1. 建产品与物模型</h3>
      <p>
        产品是设备模板。物模型定义属性、事件与服务，是命令校验、影子、告警阈值与趋势图的共同依据；
        未建模的字段不会进入派生数据。
      </p>

      <h3>2. 建设备</h3>
      <p>在「设备管理 → 创建设备」中填写设备名称、标识与类型，保存后设备状态为「离线」。</p>

      <h3>3. 取一机一密</h3>
      <p>在设备详情中点击「查看凭据」，复制该设备专属的 MQTT 用户名与密码。密钥只展示一次。</p>

      <h3>4. 连接并上报</h3>
      <CodeBlock language="bash" :code="connectSample" />

      <h3>5. 验证</h3>
      <p>
        连接成功后，设备状态变为「在线」；在「实时消息」中可看到 <code>device/&lt;deviceKey&gt;/data</code>
        的实时上报，属性趋势与设备日志也会出现对应记录。
      </p>

      <h3>6. 其他接入方式</h3>
      <ul>
        <li><strong>HTTP 上报</strong>：<code>POST /api/ingest/{deviceKey}/{messageType}</code>，HTTP Basic 设备凭据。</li>
        <li><strong>网关代理</strong>：网关以自身凭据代连子设备；多级子网关由 <code>SUB</code> 继续向下委托。</li>
        <li><strong>平台 Modbus</strong>：产品配置 <code>driver_config</code>，设备点表执行方设为 <code>PLATFORM</code>，默认关闭。</li>
      </ul>
      <p>
        按芯片分类的完整示例见
        <router-link to="/docs/examples/general">通用接入流程</router-link>，以及
        <router-link to="/docs/examples/esp32">ESP32 / ESP8266</router-link>、
        <router-link to="/docs/examples/stm32">STM32 / 裸机 MCU</router-link>、
        <router-link to="/docs/examples/linux">Linux / 树莓派</router-link>、
        <router-link to="/docs/examples/cellular">4G Cat.1 / NB-IoT</router-link>。
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
  overview: { title: '平台简介', desc: '了解平台能力、整体架构与核心名词' },
  workbench: { title: '登录与工作台', desc: '账号角色与工作台导览' },
  'first-device': { title: '接入第一个设备', desc: '从建设备到看到数据的最短路径' }
}

const section = computed(() => route.params.section || 'overview')
const current = computed(() => sections[section.value] || sections['first-device'])

const connectSample = `# 用户名：{productKey}.{deviceKey}，密码：device_secret
mosquitto_pub \\
  -h your-server -p 1883 \\
  -u "esp32-fall-detect.sensor-temp-001" -P "<device_secret>" \\
  -t "device/sensor-temp-001/data" \\
  -m '{"temperature": 26.5, "ts": 1750000000}' \\
  -q 1`
</script>