<template>
  <DocArticle eyebrow="快速开始" :title="current.title" :desc="current.desc">
    <!-- 平台简介 -->
    <template v-if="section === 'overview'">
      <h2>平台能力</h2>
      <p>
        MQTT Cloud 是一套开源自部署的物联网设备管理平台。基于 EMQX 提供 MQTT 接入，
        后端负责设备身份认证、权限控制、消息落库与开放 API，前端提供工作台与公开文档站。
      </p>
      <ul>
        <li>设备接入：一机一密身份体系，MQTT 认证与 Topic 级 ACL 授权。</li>
        <li>实时消息：基于 SSE 的实时通道，向工作台推送设备上下线与上报消息。</li>
        <li>历史数据：消息持久化到 MySQL，支持按设备、Topic、时间范围查询。</li>
        <li>开放 API：REST API 与 Webhook 回调，便于集成到现有业务系统。</li>
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
          <tr><td>Redis</td><td>设备认证元数据缓存（只缓存元数据，TTL 短）</td></tr>
          <tr><td>frontend</td><td>Vue 3 工作台与公开站，Nginx 承载静态资源与反向代理</td></tr>
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
          <tr><td>ADMIN</td><td>全部功能，含设备、消息、API 密钥、Webhook 与用户管理</td></tr>
          <tr><td>OPERATOR</td><td>设备管理、实时消息、历史查询</td></tr>
          <tr><td>USER</td><td>只读查看设备与数据</td></tr>
        </tbody>
      </table>

      <h2>工作台四区导览</h2>
      <ul>
        <li><strong>顶栏</strong>：折叠导航、品牌入口、面包屑、全局搜索、通知与用户菜单。</li>
        <li><strong>左导航</strong>：按「监控 / 开发接入 / 系统」分组的功能入口，可折叠为图标态。</li>
        <li><strong>内容区</strong>：当前页面主体，统一由页头 + 内容卡片构成。</li>
        <li><strong>右上下文栏</strong>：随路由切换的辅助面板（健康度、设备摘要、Topic 过滤、快捷链接）。</li>
        <li><strong>底部状态栏</strong>：实时通道状态、Broker 状态、在线设备数与版本。</li>
      </ul>
      <p>登录入口为 <code>/login</code>，登录成功后进入 <code>/workbench/dashboard</code>。</p>
    </template>

    <!-- 接入第一个设备 -->
    <template v-else>
      <h2>五步接入</h2>
      <ol>
        <li>登录工作台，进入「设备管理」。</li>
        <li>创建产品，确定设备类型与 Topic 约定。</li>
        <li>在产品下创建设备，系统生成唯一 deviceKey。</li>
        <li>获取设备的一机一密凭据（用户名 / 密码）。</li>
        <li>设备使用凭据连接 Broker 并发布数据，工作台即可看到实时消息。</li>
      </ol>

      <h3>1. 建设备</h3>
      <p>在「设备管理 → 创建设备」中填写设备名称、标识与类型，保存后设备状态为「离线」。</p>

      <h3>2. 取一机一密</h3>
      <p>在设备详情中点击「查看凭据」，复制该设备专属的 MQTT 用户名与密码。</p>

      <h3>3. 连接并上报</h3>
      <CodeBlock language="bash" :code="connectSample" />

      <h3>4. 验证</h3>
      <p>
        连接成功后，设备状态变为「在线」；在「实时消息」中可看到 <code>device/&lt;deviceKey&gt;/data</code>
        的实时上报。
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
  workbench: { title: '登录与工作台', desc: '账号角色与工作台四区导览' },
  'first-device': { title: '接入第一个设备', desc: '从建设备到看到数据的最短路径' }
}

const section = computed(() => route.params.section || 'overview')
const current = computed(() => sections[section.value] || sections.overview)

const connectSample = `# 使用设备凭据连接 Broker 并上报数据
mosquitto_pub \\
  -h your-server -p 1883 \\
  -u "deviceKey|secretKey" -P "deviceSecret" \\
  -t "device/sensor-temp-001/data" \\
  -m '{"temperature": 26.5, "ts": 1750000000}' \\
  -q 1`
</script>