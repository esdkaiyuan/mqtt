# MQTT自建站点 · 项目通览（2026-10-10）

## 一句话
自部署 MQTT 物联网云平台：EMQX 5 Broker + Spring Boot 4.1 后端 + Vue 3 前端 + MySQL 8 + Redis 7，Docker Compose 一键部署，功能已从「设备管理」扩展到「物模型 / 命令 / 影子 / 告警 / 规则 / 场景 / OTA / 看板 / 开放接入」。

## 技术栈
| 层 | 技术 |
|---|---|
| 前端 | Vue 3.4 + Vite 5 + Element Plus 2.6 + Pinia + vue-router + ECharts 6 + Vue Flow（拖拽编排）+ TanStack Query + Vitest |
| 后端 | Spring Boot 4.1.1 + MyBatis Plus + Flyway + Paho(MQTT) + j2mod 3.4.0(Modbus) + JUnit5/ArchUnit |
| 基础设施 | EMQX 5.0.26、MySQL 8.0、Redis 7-alpine、Nginx（只反代 /api，屏蔽 /api/internal/*） |

## 目录结构
- `backend/` — 435 个 Java 源文件，114 个测试文件
  - `controller/`（31 个对外 Controller）+ `controller/internal/`（EMQX 认证/授权回调）
  - `service/` + `service/impl/`（约 90 个 Service）、`service/notification`、`service/scope`
  - `ingest/` 摄取管线（Dispatcher / Pipeline / Worker / 死信）、`mqtt/`、`collect/`（PointCollector SPI）
  - `filter/` 六个过滤器：JWT、ApiKey 鉴权与限流、设备凭据、内部 Token
  - `resources/db/migration/` Flyway V1 ~ V18
- `frontend/src/` — 111 个组件
  - `views/site/`（官网首页 + 5 个文档页）、`views/workbench/`（12 个业务分区）、`views/auth/`
  - `api/` 20 个接口模块、`composables/` 24 个组合式函数、`stores/`（auth / device / ui）
- `docker/` — docker-compose.yml + 前后端 Dockerfile + nginx.conf + emqx-init
- `scripts/` — deploy.sh、health-check.sh、emqx-init.sh、mvnw.sh、api-test.ps1、r5-trigger-check
- `docs/` — 56 篇文档，T-01 ~ T-31 设计/实施计划、ARCHITECTURE（v1.23）、DEPLOYMENT、路线图（v2.7）、头部平台对标缺口清单
- `sketch_jul21a/` — 独立的前端草稿/实验目录（非主构建）

## 架构要点（摘自 ARCHITECTURE.md v1.23）
1. **实时通道收敛后端**：浏览器不直连 Broker，改订阅 SSE `GET /api/realtime/stream`，后端按归属与角色过滤。
2. **EMQX 认证/授权外置**：设备连接与 Topic 读写回调后端 `/api/internal/emqx/auth|acl`，按「产品→设备→一机一密」裁决；网关侧 Nginx 直接 403。
3. **摄取链路**：平台以共享订阅 `$share/{group}/device/+/data` 消费 → 有界队列 → worker 批量落库；同时 PUBLISH 到 Redis Pub/Sub，各副本扇出给本副本 SSE。队列满/落库失败进 `ingest_dead_letter`。
4. **命令链路**：`cmd/down` 下发（QoS1）→ `reply` 回执 → 共享订阅旁路更新状态；同步调用轮询命令记录表，超时由 Sweeper 兜底。
5. **状态乱序**：摘要指标用 `updateStatusGuarded` 时间戳守卫，严格有序待 R5（Kafka 分区）解决。

## 功能交付进度
- 一期主线 T-14~T-18（物模型/命令/影子/告警/分组标签）✅
- 二期 T-19~T-21（规则引擎/设备日志/属性时序与看板）✅
- 三期 T-22~T-25（OTA/场景联动/多协议 HTTP 上报/认证方式扩展）✅
- 四期 T-26~T-29（网关子设备/通知渠道/规则告警作用域/可视化拖拽编排）✅
- T-30 商用化重构：数据底座已建（`device_point_table` / `device_point_run` / `product.protocol_type`）
- **T-31 开放接入执行侧进行中**：P1 SPI 抽象 ✅、P2 点表管理接口+前端 ✅、P3 路线A 点表下发通道 ✅；**P4 Modbus 只读采集（路线B）与 P5 D1/D2 协议文档未开始**

## 下一步（按 T-31 实施计划）
1. P4：Modbus 主站轮询（j2mod）、专用线程池、`ModbusPointCollector`、值换算 `raw*scale+offset`、`device_point_run` 落库、结果经 `IngestPipeline.submit()` 入既有链路
2. P5：D1《MQTT 主题与 ACL 规范》、D2《设备接入协议规范》，需从 `AclEvaluator` / `TopicPrefixResolver` 实际代码提取并标注源码位置
3. P6：端到端验收（stub PointCollector 跑通全链路）+ 执行方隔离用例 + 门禁（1128 项后端测试 0 失败、前端 lint/test/build）+ DEPLOYMENT 补 `app.modbus.*`

## 风险与注意
- **零回归红线**：不改 `IngestPipeline` / `IngestDispatcher` / `DeviceCommandServiceImpl` / `ThingModelInterpretServiceImpl` 既有行为与签名；新增 `collect` 包需确认不违反 ArchUnit 分层（service 接口包禁依赖 mapper）。
- **执行方隔离**：`device_point_table.executor`（EDGE/PLATFORM）是唯一定分支依据，必须先按 executor 过滤，防同设备双采。
- **Modbus 连接不得常驻**，按轮询周期建/复用/关，禁止挂到 `ScheduleConfig` 的 poolSize=3 巡检池（阻塞 IO）。
- git 历史曾丢失过一次（已恢复 T-24~T-30 并重建 main 引用），提交粒度需保持按阶段拆分。
- `.env` 已含实际配置，勿外泄；默认账号 admin/admin123 仅限本地。