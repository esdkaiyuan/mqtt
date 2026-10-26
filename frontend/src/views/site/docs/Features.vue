<template>
  <DocArticle eyebrow="功能指南" :title="current.title" :desc="current.desc">
    <!-- 设备与物模型 -->
    <template v-if="section === 'devices'">
      <h2>概念关系</h2>
      <table>
        <thead>
          <tr><th>概念</th><th>作用</th><th>关键约束</th></tr>
        </thead>
        <tbody>
          <tr><td>产品 Product</td><td>设备模板，定义接入协议、认证方式与 Topic 前缀</td><td>同产品下设备共享物模型与认证策略</td></tr>
          <tr><td>物模型 TSL</td><td>定义属性、事件、服务及其数据类型</td><td>命令校验、影子、告警阈值与趋势图都依赖它</td></tr>
          <tr><td>设备 Device</td><td>一台具体设备，拥有唯一 deviceKey</td><td>deviceKey 在产品内唯一；密钥只展示一次</td></tr>
          <tr><td>点表 Point Table</td><td>寄存器 / 节点地址与物模型标识符的映射</td><td>EDGE 与 PLATFORM 执行方同一设备下必须一致</td></tr>
        </tbody>
      </table>

      <h2>推荐使用顺序</h2>
      <ol>
        <li>创建产品，选择接入协议与认证方式。</li>
        <li>录入物模型，先定义属性与数据类型。</li>
        <li>创建设备或批量注册，保存一次性返回的密钥。</li>
        <li>按接入方式连接并上报，确认实时消息与属性最新值。</li>
        <li>需要纯 Modbus 接入时，再配置点表并把执行方切到 PLATFORM。</li>
      </ol>

      <DocCallout type="tip" title="先建模，再接线">
        属性 identifier 一旦被历史数据、告警与场景引用，改名会造成历史断层。
        建议先在物模型里把命名与类型定稳，再创建设备。
      </DocCallout>

      <h2>网关与子设备</h2>
      <p>
        子设备无法直连 Broker 时，可由网关使用自身凭据代理接入。平台支持「网关 → 子设备」两级代理，
        并允许 <code>SUB</code> 子网关继续向下挂载，形成多级拓扑；每一级只对自己直接子设备负责。
      </p>
      <ul>
        <li>父子设备必须同产品；挂载前子设备必须是叶子节点。</li>
        <li>解绑带下级的子网关时，它会收敛为根 <code>GATEWAY</code>，子树保留。</li>
        <li>主题形状仍是 <code>device/{subKey}/...</code>，不新增自有 MQTT 主题。</li>
      </ul>

      <h2>设备台账导入与导出</h2>
      <p>
        设备管理页支持 CSV 台账迁移：<strong>导入</strong>适合从既有系统或 Excel 清单批量登记，
        <strong>导出</strong>按当前筛选条件导出设备清单（不含任何密钥）。
      </p>
      <ul>
        <li>
          导入列：<code>deviceKey</code>、<code>deviceName</code>、<code>productKey</code> 必填；
          <code>deviceType</code> 缺省 <code>sensor</code>，<code>topic</code> 缺省
          <code>device/{deviceKey}/data</code>，<code>firmwareVersion</code> 可选。
        </li>
        <li>
          单次最多 1000 行、2MB；逐行独立事务，失败行写入 <code>failedItems</code>，不阻断整批。
        </li>
        <li>
          每台新设备签发一次性密钥，仅导入响应返回；请立即「导出凭据 CSV」。
        </li>
        <li>导入需要 ADMIN / OPERATOR；导出沿用列表的数据范围（非 ADMIN 只能导出本人设备）。</li>
      </ul>

      <h2>Payload Codec（数据解析映射）</h2>
      <p>
        现场设备报文不是 Alink 信封时，可在产品表单的「Payload Codec」中配置声明式映射模板。
        平台会在物模型解析前先把原始 JSON 映射成 Alink，属性 / 事件 / 告警 / 规则 / 场景 / 历史链路无需改动。
      </p>
      <ul>
        <li>
          模板形如
          <code>{"template":{"method":"thing.event.property.post","params":{"temperature":"$.sensors.temp"}}}</code>；
        </li>
        <li>字符串以 <code>$</code> 开头表示路径，支持 <code>$.a.b</code>、<code>$[0]</code>、<code>$.a[0].b</code>；</li>
        <li>路径不存在时字段省略；<code>fallbackToRaw</code> 默认 true，映射失败回退原始载荷；</li>
        <li>不执行脚本、不支持函数与通配符；配置非法保存时返回 <code>6317</code>。</li>
      </ul>
    </template>

    <!-- 规则、告警与场景 -->
    <template v-else-if="section === 'automation'">
      <h2>三者怎么选</h2>
      <table>
        <thead>
          <tr><th>能力</th><th>适合做什么</th><th>不适合做什么</th></tr>
        </thead>
        <tbody>
          <tr><td>消息规则</td><td>单条上报的过滤、变换与转发（MQTT / HTTP / Kafka）</td><td>多步骤、带延时或人工介入的流程</td></tr>
          <tr><td>告警规则</td><td>持续监测某个属性 / 事件 / 离线状态，触发后通知与升级</td><td>跨设备动作编排</td></tr>
          <tr><td>场景联动</td><td>多设备、多步骤、带条件 / 分支 / 循环 / 并行的自动化</td><td>高频单条消息的轻量转发</td></tr>
        </tbody>
      </table>

      <h2>告警窗口聚合</h2>
      <p>
        阈值规则默认按最新值判断。把「统计窗口」设为大于 0 后，平台按窗口内样本聚合再比较，
        支持 <code>AVG</code> / <code>MIN</code> / <code>MAX</code> / <code>SUM</code> / <code>COUNT</code>，
        并可设置最少样本数。
      </p>
      <ul>
        <li>窗口按属性上报时间计算，乱序上报不会拉长窗口。</li>
        <li>样本不足时不触发，也不会把已触发告警误判为恢复。</li>
        <li>窗口只保存在当前副本内存，服务重启 / 副本切换后重新累计。</li>
        <li>抑制窗口与统计窗口是两件事：前者控制重复通知，后者控制判定口径。</li>
      </ul>

      <h2>场景步骤类型</h2>
      <table>
        <thead>
          <tr><th>类型</th><th>作用</th><th>注意</th></tr>
        </thead>
        <tbody>
          <tr><td>普通动作</td><td>更新属性 / 下发命令 / 转发 MQTT / 转发 HTTP / 转发 Kafka</td><td>命令与属性写入复用既有链路</td></tr>
          <tr><td><code>BRANCH</code></td><td>按属性条件向前跳转</td><td>只能向后面的步骤跳，避免死循环</td></tr>
          <tr><td><code>LOOP</code></td><td>有界计数循环</td><td>单循环 ≤20 次，展开 ≤200 步</td></tr>
          <tr><td><code>PARALLEL</code> / <code>JOIN</code></td><td>多条分支并发执行，全部完成后汇合</td><td>块内不能放分支 / 循环 / 嵌套并行</td></tr>
        </tbody>
      </table>

      <h2>数据桥接（Kafka）</h2>
      <p>
        规则与场景支持 <code>FORWARD_KAFKA</code> 动作，把命中后的 JSON 载荷投递到第三方 Kafka。
        默认关闭；开启需配置 <code>BRIDGE_KAFKA_*</code>（Bootstrap / Topic / 安全协议）。
        动作可指定 Topic 与分区 Key（建议按设备标识），发送失败按动作重试兜底。
      </p>

      <DocCallout type="note" title="先试运行">
        规则与场景都提供干跑：不写执行记录、不发动作，只返回触发 / 条件 / 步骤摘要。
        上线前先用真实设备当前值试运行，确认命中路径。
      </DocCallout>

      <h2>版本回滚</h2>
      <p>
        场景每次创建 / 保存 / 回滚都会生成快照，每场景保留最近 20 个版本。
        回滚会覆盖当前配置（含启用状态），并生成一个新的回滚版本，可再次撤销。
      </p>
    </template>

    <!-- 运维与可视化 -->
    <template v-else-if="section === 'operations'">
      <h2>OTA 升级</h2>
      <ol>
        <li>在「固件管理」上传固件，记录版本与 MD5。</li>
        <li>创建升级任务，目标可按产品 / 分组 / 标签 / 手选设备并集选择。</li>
        <li>需要灰度时开启分批，平台只下发首批，后续按批次间隔或手动推进。</li>
        <li>在任务详情查看逐台记录；失败可重投，任务可暂停 / 恢复 / 终止。</li>
      </ol>
      <DocCallout type="warning" title="设备级互斥">
        同一台设备同时只能处于一个非终态升级任务。建任务时按
        <code>OTA_DEVICE_CONFLICT_POLICY</code> 选择剔除或拒绝，下发前还会二次校验。
      </DocCallout>
      <p>
        设备 OTA 成功回传后，平台把 <code>firmwareVersion</code> 回写到设备档案（设备详情可见）。
        默认开启降级拦截：目标版本低于设备当前版本时，该设备在建任务阶段被跳过，
        并在任务详情显示「降级拦截」台数；需要回滚固件时设置
        <code>OTA_ALLOW_DOWNGRADE=true</code>。
      </p>

      <h2>看板与大屏</h2>
      <p>
        看板按登录用户私有保存，面板可绑定设备属性与图表类型。分享会生成公开只读链接，
        库中只存令牌哈希；停用、删除或轮换后旧链接立即失效。
        大屏支持多看板轮播、全屏和面板定时刷新。
      </p>

      <h2>快速上位机（HMI）</h2>
      <p>
        HMI 与看板分离：一个 HMI 固定绑定一台设备，按物模型一键生成控件，
        适合现场看值与简单控值。控件数量与每用户 HMI 数量都有上限。
        需要桌面端、离线演示或本地外设联动时，可基于
        <router-link to="/docs/hmi/qt">Qt 快速上位机模板</router-link> 二次开发。
      </p>

      <h2>日志、调试与导出</h2>
      <ul>
        <li><strong>设备日志</strong>：消息 / 事件 / 命令 / 连接状态统一时间线，支持实时刷新与 CSV。</li>
        <li><strong>调试中心</strong>：查看连接参数、模拟上行、生成设备端与上位机端属性代码；仅 ADMIN / OPERATOR。</li>
        <li><strong>导出</strong>：日志、属性趋势、命令记录、告警记录、设备台账均支持 CSV；导出有行数上限并标注截断。</li>
      </ul>
    </template>

    <!-- 权限与开放接入 -->
    <template v-else>
      <h2>角色边界</h2>
      <table>
        <thead>
          <tr><th>角色</th><th>典型权限</th><th>写设备</th></tr>
        </thead>
        <tbody>
          <tr><td>ADMIN</td><td>全部管理能力，含用户、API 密钥、Webhook 与系统配置</td><td>可以</td></tr>
          <tr><td>OPERATOR</td><td>设备、规则、场景、OTA、告警与消息运维</td><td>可以</td></tr>
          <tr><td>VIEWER</td><td>只读查看设备、数据、日志、看板与导出</td><td>不可以</td></tr>
        </tbody>
      </table>

      <h2>分组共享</h2>
      <p>
        分组所有者可按用户名或邮箱添加成员，授予 <code>VIEW</code> 或 <code>OPERATE</code>。
        权限沿分组祖先链继承：父分组授权自动覆盖整棵子树。
      </p>
      <ul>
        <li><code>VIEW</code>：查看分组子树、组内设备、属性 / 事件 / 命令记录 / 影子。</li>
        <li><code>OPERATE</code>：在 VIEW 基础上可下发命令、写影子；仍需平台 ADMIN / OPERATOR 角色。</li>
        <li>分组结构、设备关联与成员管理始终仅所有者可用。</li>
        <li>共享分组只出现在分组管理页，不会进入场景 / 规则 / OTA 的目标选择器。</li>
      </ul>

      <h2>审计日志</h2>
      <p>
        平台对管理端与开放 API 的写操作统一留痕：谁、何时、从哪个 IP、调用了什么路径、
        操作对象与结果。登录、登出与注册也会记录，连续登录失败是账号被盗用的第一信号。
      </p>
      <ul>
        <li>入口：工作台「系统管理 → 审计日志」，仅 <strong>ADMIN</strong> 可见。</li>
        <li>可按用户、操作类型、资源类型、来源、结果、关键字与时间窗过滤，并导出 CSV。</li>
        <li>只记录写操作（POST / PUT / PATCH / DELETE）与认证事件；读请求不进审计表。</li>
        <li>请求体不落库，避免把设备密钥、API Key、证书私钥写进审计日志。</li>
        <li>默认保留 180 天，由 <code>AUDIT_LOG_RETENTION_DAYS</code> 调整；<code>0</code> 表示不清理。</li>
      </ul>

      <h2>多因素认证（MFA）</h2>
      <p>
        登录账号可在「开发接入 → 安全设置」绑定 TOTP 动态口令（authenticator 生成 6 位码）。
        启用后登录需密码 + 动态口令；密钥只在绑定时展示一次，平台仅保存加密后的密钥。
      </p>
      <ul>
        <li>支持标准 authenticator（Google Authenticator / 1Password / Microsoft Authenticator 等）；</li>
        <li>动态口令容忍 ±30 秒时钟偏差；停用需再次输入动态口令；</li>
        <li>临时关闭 <code>MFA_ENABLED=false</code> 时 MFA 端点返回 404 且登录不要求二步验证，已绑定密钥保留；</li>
        <li>轮换 <code>JWT_SECRET</code> 且未配置 <code>MFA_ENCRYPTION_KEY</code> 时，已绑定用户需重新绑定。</li>
      </ul>

      <h2>开放 API</h2>
      <p>
        API Key 仅 <strong>ADMIN</strong> 可创建与撤销，绑定创建者账号；<code>/external/v1</code>
        只能访问该账号名下设备。除命令下发与影子 desired 写入外，其余端点为只读。
      </p>
      <DocCallout type="danger" title="密钥等同于账号权限">
        API Key 明文只展示一次，平台只保存哈希。不要写进前端代码、日志或公开仓库；
        泄露后立即在「开发接入 → API 密钥」撤销。
      </DocCallout>

      <h2>继续阅读</h2>
      <ul>
        <li><router-link to="/docs/api/api-keys">API 密钥与认证</router-link></li>
        <li><router-link to="/docs/api/rest">REST API 参考</router-link></li>
        <li><router-link to="/docs/api/errors">错误码与处置</router-link></li>
      </ul>
    </template>
  </DocArticle>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DocArticle from '@/components/docs/DocArticle.vue'
import DocCallout from '@/components/docs/DocCallout.vue'

const route = useRoute()

const sections = {
  devices: { title: '设备与物模型', desc: '产品、物模型、设备、点表与多级网关的概念和使用顺序' },
  automation: { title: '规则、告警与场景', desc: '三种自动化能力的边界、告警窗口与场景流程' },
  operations: { title: '运维与可视化', desc: 'OTA、看板大屏、快速上位机、日志与导出' },
  access: { title: '权限与开放接入', desc: '角色、分组成员共享、API Key 与安全边界' }
}

const section = computed(() => route.params.section || 'devices')
const current = computed(() => sections[section.value] || sections.access)
</script>
