<template>
  <DocArticle eyebrow="平台运维" :title="current.title" :desc="current.desc">
    <!-- 部署 -->
    <template v-if="section === 'deployment'">
      <h2>服务与端口</h2>
      <table>
        <thead>
          <tr><th>服务</th><th>端口</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr><td>nginx（frontend）</td><td>80（<code>FRONTEND_PORT</code>）</td><td>唯一入口，静态资源 + <code>/api</code> 反向代理</td></tr>
          <tr><td>backend</td><td>8080（仅容器网络）</td><td>不发布宿主机端口，统一经网关访问</td></tr>
          <tr><td>EMQX</td><td>1883</td><td>MQTT 接入</td></tr>
          <tr><td>EMQX Dashboard</td><td>18083</td><td>Broker 管理后台</td></tr>
        </tbody>
      </table>

      <h2>启动</h2>
      <CodeBlock language="bash" :code="deploySample" />
      <p>启动顺序由 <code>depends_on: service_healthy</code> 保证：<code>emqx → backend → emqx-init</code>。</p>

      <h3>健康检查</h3>
      <CodeBlock language="bash" :code="healthSample" />
    </template>

    <!-- 监控与巡检 -->
    <template v-else-if="section === 'monitoring'">
      <h2>健康检查</h2>
      <ul>
        <li>网关健康：<code>GET /api/health</code>（公开）。</li>
        <li>应用健康：<code>GET /api/actuator/health</code>。</li>
        <li>一键巡检：<code>bash scripts/health-check.sh</code>。</li>
      </ul>

      <h2>R5 触发条件巡检</h2>
      <p>
        脚本一次巡检覆盖设备数、上行 QPS、<code>message</code> 表行数、后端代码行数等量化条件，
        退出码 <code>0/1/2</code> 分别对应正常 / 警告 / 触发。
      </p>
      <CodeBlock language="bash" :code="r5Sample" />

      <h3>定时巡检</h3>
      <CodeBlock language="bash" :code="cronSample" />
      <p>Windows 可用计划任务调用 <code>scripts/r5-trigger-check.ps1</code>。</p>
    </template>

    <!-- 常见问题 -->
    <template v-else>
      <h2>设备连不上</h2>
      <ul>
        <li>确认用户名格式为 <code>{productKey}.{deviceKey}</code>，密码为最新 <code>device_secret</code>。</li>
        <li>确认产品处于启用状态且设备 <code>enabled=1</code>。</li>
        <li>查看后端日志中的认证拒绝记录。</li>
      </ul>

      <h2>消息不入库</h2>
      <ul>
        <li>确认设备发布到 <code>device/{deviceKey}/data</code> 等允许的主题。</li>
        <li>确认后端 MQTT 订阅正常（平台账号 <code>PLATFORM</code> 已连接）。</li>
        <li>查看后端日志中的消息处理异常。</li>
      </ul>

      <h2>SSE 断开</h2>
      <ul>
        <li>Nginx 必须为 <code>/api/realtime/stream</code> 单独配置：<code>proxy_buffering off</code>、<code>proxy_read_timeout 1h</code>、<code>Connection ''</code>。</li>
        <li>心跳间隔需小于网关空闲超时。</li>
        <li>多副本部署时确认跨副本实时广播已启用。</li>
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
  deployment: { title: '部署', desc: '容器编排、端口与健康检查' },
  monitoring: { title: '监控与巡检', desc: '健康检查与 R5 触发条件巡检' },
  faq: { title: '常见问题', desc: '设备连接、消息入库与 SSE 断开排查' }
}

const section = computed(() => route.params.section || 'deployment')
const current = computed(() => sections[section.value] || sections.deployment)

const deploySample = `docker compose --env-file .env -f docker/docker-compose.yml up -d --build`

const healthSample = `bash scripts/health-check.sh

# 网关健康
curl http://localhost/api/health`

const r5Sample = `bash scripts/r5-trigger-check.sh

# 自定义窗口与告警阈值
QPS_WINDOW_SECONDS=60 WARN_RATIO=80 bash scripts/r5-trigger-check.sh`

const cronSample = `# 每 6 小时巡检一次，触发时输出告警
0 */6 * * * cd /opt/mqtt && bash scripts/r5-trigger-check.sh >> /var/log/r5-check.log 2>&1 || echo "R5 触发条件变化，见 /var/log/r5-check.log"`
</script>