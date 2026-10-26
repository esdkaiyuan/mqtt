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
          <tr><td>backend</td><td>8080（仅容器网络）</td><td>不发布宿主机端口；Swagger 经 <code>http://localhost/api/swagger-ui.html</code> 访问</td></tr>
          <tr><td>EMQX</td><td>1883 / 8883 / 8083</td><td>MQTT TCP / TLS / WebSocket 接入</td></tr>
          <tr><td>EMQX Dashboard</td><td>18083</td><td>Broker 管理后台</td></tr>
          <tr><td>MySQL</td><td>3306（<code>DB_PORT</code>）</td><td>业务库，生产环境建议仅内网可达</td></tr>
          <tr><td>Redis</td><td>6379（<code>REDIS_PORT</code>）</td><td>默认仅绑定 <code>127.0.0.1</code>，供后端使用</td></tr>
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
        <li>存活探针：<code>GET /api/health</code>（公开，进程能响应即 UP）。</li>
        <li>就绪探针：<code>GET /api/health/ready</code>（依赖或积压异常返回 HTTP 503）。</li>
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

      <h2>审计与接入治理</h2>
      <ul>
        <li><code>AUDIT_LOG_ENABLED</code>：审计日志总开关；<code>AUDIT_LOG_RETENTION_DAYS</code> 设置保留天数（默认 180，0 不清理）。</li>
        <li><code>AUDIT_LOG_SWEEP_INTERVAL_MS</code> / <code>AUDIT_LOG_PURGE_BATCH_SIZE</code>：清理巡检间隔与单批删除行数。</li>
        <li><code>HTTP_INGEST_RATE_LIMIT_ENABLED</code>：HTTP 上报设备级限流（默认关闭）；<code>HTTP_INGEST_RATE_LIMIT_PER_MINUTE</code> 默认 600。</li>
        <li><code>HTTP_INGEST_IDEMPOTENCY_TTL_SECONDS</code>：设备携带 <code>Idempotency-Key</code> 时同一键的有效期（默认 300 秒）。</li>
        <li><code>DEVICE_IMPORT_MAX_FILE_BYTES</code> / <code>DEVICE_IMPORT_MAX_ROWS</code>：设备 CSV 导入的文件大小与行数上限。</li>
        <li><code>OTA_ALLOW_DOWNGRADE</code>：是否允许固件降级（默认 <code>false</code>，建任务跳过当前版本更高的设备）。</li>
        <li><code>BRIDGE_KAFKA_*</code>：Kafka 数据桥接开关与连接参数（默认关闭，规则 / 场景 <code>FORWARD_KAFKA</code> 使用）。</li>
      </ul>
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
        <li>共享分组设备不会进入 SSE 推送；这是按设备归属过滤的预期行为，请改用历史 / 详情接口查看。</li>
      </ul>

      <h2>开放 API 调用失败</h2>
      <ul>
        <li><code>401 / 3501</code>：<code>X-API-Key</code> 缺失、无效或已撤销；重新创建并更新调用方配置。</li>
        <li><code>403 / 2003</code>：设备不属于密钥所属账号；开放 API 不做跨账号共享。</li>
        <li><code>429 / 4002</code>：按 <code>Retry-After</code> 退避后重试，避免高频轮询。</li>
      </ul>

      <h2>Webhook 未收到或延迟</h2>
      <ul>
        <li>先在控制台点击测试，确认目标地址可达且返回 2xx。</li>
        <li>回调为异步单次投递，非 2xx 或网络异常只记录日志，不自动重试；需以平台数据对账补偿。</li>
        <li>若配置了签名密钥，校验原始请求体上的 <code>X-Signature: sha256=&lt;hex&gt;</code>，不要先反序列化再计算。</li>
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
const current = computed(() => sections[section.value] || sections.faq)

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