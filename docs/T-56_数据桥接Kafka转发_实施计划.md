# T-56 · 数据桥接：规则 / 场景转发到 Kafka

> 版本：v1.0　创建日期：2026-10-26　状态：**已交付**
> 定位：补齐对标清单 G-12-8（转发数据库 / Kafka）中的 Kafka 部分 —— 让规则与场景可以把命中数据投递到第三方 Kafka，进入数据湖 / 流处理链路。
> 关联：[T-19 消息规则](T-19_消息规则_规则引擎_实施计划.md)、[T-23 场景联动](T-23_自动化与场景联动_实施计划.md)、[T-19 规则引擎](T-19_消息规则_规则引擎_实施计划.md)。

---

## 1. 背景与目标

此前规则 / 场景的出站出口只有 `FORWARD_MQTT`（第三方 Broker）与 `FORWARD_HTTP`（Webhook）。
一线平台（ThingsBoard / EMQX / AWS IoT）的规则引擎核心价值是把数据送到 Kafka / 数据湖，
本任务先补齐最高频的 **Kafka** 出口。

目标：

1. 新增动作类型 `FORWARD_KAFKA`，规则与场景均可配置；
2. 复用既有动作执行、重试、载荷快照、`forward_payload` 排障能力；
3. 默认关闭，未配置 Kafka 时不影响既有动作；
4. 只依赖 `kafka-clients`，不引入 spring-kafka 自动配置。

---

## 2. 设计决策

### 2.1 复用动作框架，不新增子系统

- `RuleActionExecutorImpl` / `SceneActionExecutorImpl` 各加一个 `case`，复用 `renderPayload`、
  `updateForwardPayload`、退避重试与失败隔离；
- 发送使用同步 `get(timeout)`：失败由动作重试兜底，避免「投递线程池里无限阻塞」；
- Producer 懒创建、单例复用；配置变更需重启（与外部 MQTT 转发一致）。

### 2.2 配置集中在 `app.bridge.kafka`

规则与场景共用同一份 Kafka 配置，避免两处 Broker 配置漂移：

| 配置 | 默认 | 说明 |
|---|---|---|
| `enabled` | `false` | 关闭时不创建 Producer，动作直接失败（`6323`） |
| `bootstrap-servers` | 空 | `host:port` 逗号分隔；开启时必填 |
| `topic` | 空 | 缺省 Topic；动作未配置 topic 时回落 |
| `client-id` | `mqtt-cloud-bridge` | 出站客户端 ID 前缀 |
| `acks` | `all` | Kafka acks |
| `max-block-ms` | `5000` | 发送阻塞超时，超时按失败重试 |
| `security-protocol` | `PLAINTEXT` | PLAINTEXT / SASL_PLAINTEXT / SSL / SASL_SSL |
| `sasl-*` | 空 | SASL 机制与账号（SASL 协议时必填） |

> SSL 的高级参数（truststore 路径等）未在配置面暴露，SSL 场景需后续扩展；
> 当前完整支持 PLAINTEXT 与 SASL_PLAINTEXT/SASL_SSL（SCRAM/PLAIN）。

### 2.3 动作配置

```json
{
  "topic": "factory.line1.alert",
  "key": "device-001",
  "payloadTemplate": "{\"deviceKey\":\"${deviceKey}\",\"value\":\"${value}\"}"
}
```

- `topic`：为空回落 `app.bridge.kafka.topic`；
- `key`：可选，为空时 Kafka 按 `null` key 粘性分区（建议按 `deviceKey` 保证同设备有序）；
- `payloadTemplate`：为空使用与 MQTT / HTTP 一致的默认载荷；非空渲染后必须是合法 JSON。

---

## 3. 代码变更

| 组件 | 变更 |
|---|---|
| `backend/pom.xml` | 新增 `org.apache.kafka:kafka-clients`（版本由 Spring Boot BOM 托管） |
| `BridgeProperties` | 新增 `app.bridge.kafka.*` 配置 |
| `KafkaMessageForwarder` | 新增 `bridge` 包：懒创建 Producer、同步发送、SASL、关闭钩子 |
| `RuleConstants` / `SceneConstants` | 动作类型新增 `FORWARD_KAFKA`，转发集合同步 |
| `RuleActionExecutorImpl` | 新增 `FORWARD_KAFKA` case 与 `forwardKafka` |
| `SceneActionExecutorImpl` | 新增 `FORWARD_KAFKA` case 与 `forwardKafka` |
| `ResultCode` | 新增 `6323`（数据桥接失败） |

**无新增表、无迁移**。

---

## 4. 前端变更

- 规则编辑器新增「转发 Kafka」动作：Topic、分区 Key、载荷模板；附加动作同步支持；
- 场景步骤编辑器新增「转发 Kafka」：Topic、Key、载荷模板；表单 / 画布共用口径同步；
- `utils/rule.js` / `utils/scene.js` 增加文案、选项、摘要与校验。

---

## 5. 测试

- `KafkaMessageForwarderTest`：发送字段、缺省 Topic 回落、未启用 / 缺配置 / 发送失败的 `6323`；
- `RuleActionExecutorImplTest` / `SceneActionExecutorImplTest` / `SceneAbortRetryTest`：适配新依赖；
- 门禁命令：

```bash
sh scripts/mvnw.sh -B verify
cd frontend && npx --no-install eslint src && npx --no-install vitest run && npx --no-install vite build
```

门禁结果（2026-10-26）：

- T-56 定向测试 **84 项全绿**（含 5 项 Kafka 转发器测试）；
- 后端全量 `mvn -B verify`：**1647 项测试 0 失败 0 错误，BUILD SUCCESS**（含 ArchUnit）；
- 前端 `eslint` **0 error / 0 warning**；`vitest` **683 项 / 87 个文件全绿**；`vite build` 通过；
- 待现场实测：连接真实 Kafka 并消费。

---

## 6. 未做 / 后续

| 项 | 说明 |
|---|---|
| `FORWARD_DB`（数据库 / 时序库） | 需要数据源与 DDL 管理，另立任务 |
| SSL truststore 等高级参数 | 当前仅暴露协议与 SASL |
| 批量攒批 / 压缩 | Kafka Producer 默认已攒批；桥接侧不额外封装 |
| 死信化桥接失败 | 失败走动作重试与执行记录，未单设桥接死信 |
| 现场实测 | 连接真实 Kafka 并消费待现场验收 |

---

## 7. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-26 | 首次成文：规则 / 场景 `FORWARD_KAFKA`、`app.bridge.kafka` 配置与前端表单交付。 |