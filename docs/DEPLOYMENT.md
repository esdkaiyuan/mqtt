# MQTT云平台 - 部署文档

## 1. 环境要求

### 1.1 最低配置
- CPU：2核
- 内存：4GB
- 磁盘：20GB可用空间
- 操作系统：Linux（推荐Ubuntu 20.04+）/ macOS / Windows（WSL2）

### 1.2 软件依赖
- Docker 20.10+
- Docker Compose 2.0+
- Git（可选，用于克隆代码）

### 1.3 端口要求
| 服务 | 端口 | 用途 | 可修改 |
|------|------|------|--------|
| MySQL | 3306 | 数据库 | 是（.env中DB_PORT） |
| Redis | 6379 | 缓存 | 是（.env中REDIS_PORT） |
| EMQX | 1883 | MQTT协议 | 是（.env中MQTT_PORT） |
| EMQX WebSocket | 8083 | WebSocket连接 | 是 |
| EMQX Dashboard | 18083 | EMQX管理后台 | 是 |
| 后端API | 8080 | HTTP服务 | 是（.env中BACKEND_PORT） |
| 前端 | 80 | HTTP服务 | 是（.env中FRONTEND_PORT） |

## 2. 快速部署（推荐）

### 步骤1：克隆代码
```bash
git clone <repository-url>
cd MQTT自建站点
```

### 步骤2：配置环境变量
```bash
cp .env.example .env
# 编辑.env，修改密码等配置
nano .env
```

### 步骤3：执行部署
```bash
bash scripts/deploy.sh
```

### 步骤4：验证部署
```bash
bash scripts/health-check.sh
```

### 步骤5：访问系统
- 前端：http://localhost
- API文档：http://localhost:8080/api/swagger-ui.html
- EMQX后台：http://localhost:18083
- 默认账号：admin / admin123

### 步骤6：可观测端点（Actuator）
- 应用健康：`http://localhost:8080/api/actuator/health`（公开）
- Prometheus 指标：`http://localhost:8080/api/actuator/prometheus`（公开，供抓取）
- `info` / `metrics` 需 ADMIN 角色 JWT 访问

> 指标由 `spring-boot-starter-actuator` + `micrometer-registry-prometheus` 提供；暴露范围与鉴权见 `application.yml` 的 `management.*` 与 `SecurityConfig`。

### 步骤7：接入访问控制（设备接入与身份体系）

设备接入的认证与主题授权由后端裁决，EMQX 通过 HTTP 回调请求后端，相关配置全部来自 `.env`：

| 变量 | 作用 | 默认 |
|------|------|------|
| `INTERNAL_TOKEN` | 内部接口共享令牌。后端 `InternalTokenFilter` 校验，`emqx-init` 下发回调时以 `X-Internal-Token` 携带，两者必须一致 | 无，`deploy.sh` 自动生成 |
| `PLATFORM_SECRET` | 平台账号密码。后端自身连接 Broker 使用（MQTT 用户名固定为 `PLATFORM`） | 无，`deploy.sh` 自动生成 |
| `FRONTEND_SECRET` | 迁移期前端直连 Broker 的受限账号密码，配合 `DIRECT_FRONTEND_ENABLED` 使用 | 空 |
| `ACCESS_CONTROL_ENFORCE_AUTH` | `false`=双轨期，认证/授权回调一律放行（存量设备老账号仍可用）；`true`=仅一机一密凭据可接入 | `false` |
| `DIRECT_FRONTEND_ENABLED` | 是否保留前端直连 Broker 的受限账号；前端已改用 SSE，确认后可置 `false` | `true` |

`emqx-init` 服务（容器名 `mqtt-emqx-init`，`restart: "no"`）在 `emqx` 与 `backend` 均健康后执行一次，
通过 EMQX REST API 下发 HTTP 认证源（回调 `/api/internal/emqx/auth`）与 HTTP 授权源
（回调 `/api/internal/emqx/acl`），并把未匹配时的默认策略设为 `deny`。脚本幂等，可重复执行。

**启动顺序必须为 `emqx → backend → emqx-init`**，由 `docker-compose.yml` 的 `depends_on`（`service_healthy`）保证。
EMQX 的认证/授权源指向后端，若在后端就绪前下发，连接器会因回调失败进入 alarm，**alarm 期间 EMQX 不发起回调、
直接拒绝所有客户端连接**（含平台自身）。

`emqx-init` 写入后会 **GET 回读校验**，任一断言失败即非零退出，避免"以为写了、其实没写"：

```bash
docker logs mqtt-emqx-init --tail 20
# 期望输出：EMQX 认证与授权配置完成，且回读校验通过
# 校验项：HTTP 认证器 enable=true；HTTP 授权源 enable=true；file 授权源 enable=false；authorization.settings.no_match=deny
```

> 内置 `file` 授权源的 `acl.conf` 末尾是 `{allow, all}`，会在授权链中直接放行、短路其后的 HTTP 授权源，
> 因此脚本将其显式禁用（`enable=false`，rules 置为 `{deny, all}` 兜底），使授权决策完全由后端 HTTP 回调裁决。

**后端连接自检**：后端启动后由 `mqtt-connector` 守护线程负责建连与重连，指数退避（1s→2s→4s→…→上限 30s），
`connectionLost` 时立即唤醒重试。连接状态与次数通过 Prometheus 指标暴露：

```bash
# 期望：mqtt_connected 1.0（已连接）；attempt_total 与 failure_total 可对账重连过程
curl -s http://localhost:8080/api/actuator/prometheus | grep '^mqtt_'
```

> `docker restart mqtt-backend` 后无需人工干预即自动恢复订阅。恢复通常需约 60s：EMQX 认证连接器在后端停机期间
> 进入 alarm，需等其健康检查恢复后才重新发起认证回调，期间日志会连续出现 `Not authorized to connect`，
> 这是预期现象而非配置错误（连接成功后 `mqtt_connected` 回到 1.0）。

> 设备凭据（`{productKey}.{deviceKey}` / `deviceSecret`）在创建设备时一次性返回；
> 存量设备刷机完成后关闭双轨的步骤见 `scripts/migrate-device-secrets.md`。

### 步骤8：迁移期开关

设备接入改造采用双轨过渡，两个开关控制放宽范围，二者都必须**在存量设备全部刷机完成后**翻转。

| 变量 | 当前值 | 含义 | 翻转前置条件 | 翻转后验证 |
|------|--------|------|--------------|------------|
| `ACCESS_CONTROL_ENFORCE_AUTH` | `false` | `false` 时 EMQX 认证与授权回调一律放行，存量设备老凭据仍可接入 | 存量设备全部刷机为 `{productKey}.{deviceKey}` + 一机一密 | 旧凭据（如 `admin/public`）连接应被拒，设备凭据连接成功 |
| `DIRECT_FRONTEND_ENABLED` | `true` | 是否保留前端直连 Broker 的受限账号（影响后端 ACL 决策） | 前端已全部切 SSE，确认无直连依赖 | 置 `false` 后前端实时数据仍正常刷新 |

`ACCESS_CONTROL_ENFORCE_AUTH=false` 期间，后端每次启动都会打印迁移期告警，防止长期遗忘：

```bash
docker logs mqtt-backend 2>&1 | grep "迁移期"
# 期望：接入访问控制处于迁移期：ACCESS_CONTROL_ENFORCE_AUTH=false，认证与授权回调一律放行。存量设备全部刷机后必须置为 true。
```

翻转方式：修改 `.env` 中对应变量后 `docker compose -f docker/docker-compose.yml up -d --force-recreate backend`。
两个开关都由后端进程读取，无需重新执行 `emqx-init`。

## 3. 手动部署（不推荐，用于理解流程）

> 提示：compose 文件在 `docker/` 子目录、`.env` 在仓库根目录，compose 默认只在 compose 文件所在目录查找 `.env`，因此下面命令统一在**仓库根目录**执行并显式指定 `--env-file .env`。

### 步骤1：准备环境变量
```bash
cp .env.example .env
# JWT_SECRET 必须填写（Base64，解码后不少于 32 字节），否则 compose 会因 :? 校验直接报错
openssl rand -base64 48
```

### 步骤2：构建后端（可选，容器内也会构建）
```bash
cd backend
mvn clean package -DskipTests
```

### 步骤3：构建前端（可选，容器内也会构建）
```bash
cd frontend
npm install
npm run build
```

### 步骤4：启动服务（在仓库根目录执行）
```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
```

### 步骤5：验证
```bash
bash scripts/health-check.sh
```

## 4. 常见问题

### Q1: 端口冲突怎么办？
A: 修改`.env`文件中的对应端口号。

### Q2: 数据库连接失败？
A: 检查MySQL容器是否正常启动：`docker logs mqtt-mysql`。

### Q3: 前端无法访问？
A: 检查前端容器是否正常启动：`docker logs mqtt-frontend`。

### Q4: MQTT连接失败？
A: 确认EMQX容器运行正常：`docker logs mqtt-emqx`。

### Q5: 如何备份数据？
A: 使用 `docker exec mqtt-mysql mysqldump` 备份MySQL。

### Q6: 如何升级版本？
A: 重新执行 `bash scripts/deploy.sh`。

### Q7: compose 报 `required variable JWT_SECRET is missing a value`？
A: `.env` 中 `JWT_SECRET` 为空。手动执行 `openssl rand -base64 48` 生成后写入 `.env`，或直接运行 `bash scripts/deploy.sh`（会自动生成）。

### Q8: 进入 `docker/` 目录执行 `docker compose up` 报变量缺失/配置未生效？
A: compose 只会在 compose 文件所在目录查找 `.env`，而本项目 `.env` 位于仓库根目录。请在**仓库根目录**执行，并显式指定配置文件与变量文件：
`docker compose --env-file .env -f docker/docker-compose.yml up -d --build`。

### Q9: `emqx-init` 容器显示 `Exited` 是失败吗？
A: 不是。它是**一次性初始化任务**（`restart: "no"`），执行完即退出属正常。用 `docker logs mqtt-emqx-init` 确认输出「EMQX 认证与授权配置完成」；若日志提示 EMQX 未就绪，可单独重跑：
`docker compose --env-file .env -f docker/docker-compose.yml up emqx-init`。

### Q10: 设备连接被拒（`not authorised`）？
A: 依次排查：1) `docker logs mqtt-emqx-init` 确认认证/授权源已下发；2) 用户名是否为 `{productKey}.{deviceKey}`、密码是否为该设备的 `deviceSecret`；3) 产品是否为 `ENABLED`、设备的 `enabled` 是否为 1；4) 双轨期确认 `ACCESS_CONTROL_ENFORCE_AUTH=false`，一旦置为 `true`，老账号 `admin` / `public` 将不再可用。密钥丢失可用 `POST /api/devices/{id}/reset-secret` 重置（旧密钥立即失效）。

### Q11: 重启后端后日志刷 `Not authorized to connect`，且设备连不上？
A: 这是 EMQX 认证连接器进入 alarm 的预期现象，非配置错误。后端停机期间 EMQX 回调失败 → 连接器 alarm → alarm 期间 EMQX 直接拒绝所有连接、不发起回调。后端 `mqtt-connector` 会按指数退避持续重试，约 60s 后（连接器健康检查恢复）自动连上，订阅随之恢复，无需人工干预。确认方式：`curl -s http://localhost:8080/api/actuator/prometheus | grep '^mqtt_'`，`mqtt_connected` 回到 `1.0` 即恢复完成；若长时间仍为 `0.0`，再按 Q10 排查。

### Q12: 导出设备凭据（`POST /api/devices/export-credentials`）有什么风险？
A: **高危运维操作。** 该接口会**重置全部设备的密钥**并返回一次性明文，调用成功的那一刻，所有存量设备的旧凭据立即失效，未刷入新密钥的设备将无法接入。执行前务必确认已具备下发新凭据的通道，并做好备份。该操作已按页（500 条）独立事务处理，避免全表长事务；后续计划改为「生成待生效密钥 + 二次确认」，不再直接作废在用凭据。

## 5. 生产环境建议

1. **更换JWT密钥：** 修改`.env`中的`JWT_SECRET`为强随机字符串
2. **启用HTTPS：** 在Nginx配置中添加SSL证书
3. **配置防火墙：** 仅开放必要端口（80/443）
4. **设置日志轮转：** 配置Docker日志大小限制
5. **定期备份：** 设置自动备份脚本
6. **监控告警：** 后端已内置 Actuator + Micrometer，可直接由 Prometheus 抓取 `http://<host>:8080/api/actuator/prometheus`，再接入 Grafana 展示
7. **开启设备接入强校验：** 全部设备刷机完成后，将 `ACCESS_CONTROL_ENFORCE_AUTH` 置为 `true`、`DIRECT_FRONTEND_ENABLED` 置为 `false` 并重建 backend 与 emqx-init（见 `scripts/migrate-device-secrets.md`）
8. **保护内部回调接口：** `INTERNAL_TOKEN` / `PLATFORM_SECRET` 必须为强随机值（`scripts/deploy.sh` 会自动生成）；`/api/internal/*` 已由 Nginx 拒绝外部访问，请勿在网关层放开

## 6. 摄取管线运维

上行消息不再由 MQTT 回调线程直接落库，而是「回调线程入队 → worker 批量落库」。相关可调项（`.env` → 环境变量 → `application.yml` 的 `app.ingest`）：

| 变量 | 默认 | 作用 |
|------|------|------|
| `INGEST_ENABLED` | `true` | `false` 时回退为回调线程同步落库，**仅用于故障回滚** |
| `INGEST_BATCH_SIZE` | `500` | 单批最大条数 |
| `INGEST_FLUSH_INTERVAL_MS` | `200` | 攒批最长等待（毫秒） |
| `INGEST_QUEUE_CAPACITY` | `20000` | 单 worker 队列容量（总容量 = 该值 × worker 数） |
| `INGEST_WORKER_COUNT` | `4` | worker 数，同时决定 deviceKey 的路由分片数 |
| `INGEST_MAX_ATTEMPTS` | `3` | 落库失败重试次数 |
| `INGEST_SHUTDOWN_DRAIN_TIMEOUT_MS` | `10000` | 停机排空队列的最长等待（毫秒） |

**停机排空约束：** `INGEST_SHUTDOWN_DRAIN_TIMEOUT_MS` 必须**小于** compose 中 `backend` 的 `stop_grace_period`（当前 `30s`）。否则容器会被强杀，未排空的消息来不及转死信。停机日志：

```bash
docker logs mqtt-backend 2>&1 | grep "摄取管线"
# 期望：摄取管线已停止: 待排空 N 条，已排空 N 条，转死信 0 条
# 「转死信」非 0 说明排空超时，应调大 INGEST_SHUTDOWN_DRAIN_TIMEOUT_MS（仍须 < stop_grace_period）
```

**指标：** 由 `GET /api/actuator/prometheus` 暴露（公开抓取），关键项：

| 指标 | 含义 |
|------|------|
| `ingest_submitted_total` | 入队消息数 |
| `ingest_persisted_total` | 成功落库消息数 |
| `ingest_dropped_total{reason}` | 丢弃数（`queue_full` / `unknown_device`） |
| `ingest_deadletter_total{reason}` | 转死信数（`queue_full` / `persist_failed`） |
| `ingest_batch_failures_total` | 重试耗尽失败的批次数 |
| `ingest_persist_duration_seconds` | 单批落库耗时 |
| `ingest_queue_depth{worker}` | 各 worker 队列当前深度 |

健康判据：稳态下 `ingest_submitted_total ≈ ingest_persisted_total`，`ingest_dropped_total` 与 `ingest_deadletter_total` 保持为 0；`ingest_queue_depth` 不应持续接近 `INGEST_QUEUE_CAPACITY`。

**死信处置：** 队列溢出或落库重试耗尽的消息落入 `ingest_dead_letter` 表（`status=PENDING`），由管理员查询：

```bash
GET /api/admin/dead-letters?status=PENDING&pageNum=1&pageSize=20   # 需 ADMIN 角色 JWT
```

## 7. 实时通道（SSE）运维

前端实时数据由后端 `GET /api/realtime/stream`（SSE）按「设备归属 + 管理员」过滤推送。该通道是**长连接**，必须保证网关不因空闲而断开：

- **nginx 必须为 SSE 单独开 location**（`docker/nginx.conf` 的 `location = /api/realtime/stream`）：`proxy_buffering off`、`proxy_cache off`、`chunked_transfer_encoding off`、`proxy_read_timeout 1h`。若沿用通用 `location /api`（`proxy_read_timeout 60s`），空闲 60 秒即被网关断开，且无 `proxy_buffering off` 时数据会被缓冲、延迟下发。
- **服务端心跳**：后端每 `app.realtime.heartbeat-interval-ms` 下发一个 SSE 注释帧（`:hb`），既穿透网关空闲超时，也用于探测并摘除已死连接（发送失败即摘除，无订阅者时停止心跳，避免空转）。

相关可调项（`.env` → 环境变量 → `application.yml` 的 `app.realtime`）：

| 变量 | 默认 | 作用 |
|------|------|------|
| `STREAM_TIMEOUT_MS` | `1800000` | SSE 连接超时（毫秒，默认 30 分钟）；`0` 表示不超时。用于回收客户端异常掉线后滞留的 emitter |
| `STREAM_HEARTBEAT_INTERVAL_MS` | `15000` | 服务端心跳间隔（毫秒）；必须**小于**网关空闲超时（nginx 侧为 `1h`），`0` 表示关闭心跳 |

**验证长连接不断开：**

```bash
# 保持空闲 5 分钟，连接应存活并持续收到 :hb 注释帧（15s 一个，约 19~20 个）
TOKEN=$(curl -s -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .data.token)
curl -s -N --max-time 330 -H "Authorization: Bearer $TOKEN" http://localhost/api/realtime/stream
```

**订阅数观测：** Gauge `realtime_sse_subscribers` 暴露当前订阅连接数（含同一用户的多标签/多端连接），用于确认「断开后订阅数回落」：

```bash
curl -s http://localhost:8080/api/actuator/prometheus | grep '^realtime_sse_subscribers'
# 空闲 0.0；打开 N 个标签页应为 N.0；关闭标签页后在一个心跳周期内回落
```

> 迁移期若仍保留前端直连 Broker（`DIRECT_FRONTEND_ENABLED=true`），前端应已切至 SSE；直连账号仅作过渡。

## 8. 遗留表处置计划

- **`history_record`**：已停止写入（历史查询改由 `message` 表承载，条件 `direction='SUBSCRIBE' AND topic LIKE '%/data'`），当前保留只读。计划在 T-12 上线后一个版本周期（约 30 天）后，通过新增迁移脚本删除该表；删除前需确认：
  1. 历史查询功能完全切换至 `message` 表，无功能回归
  2. 数据备份已完成
  3. 无其他进程仍写入该表（`SELECT COUNT(*)` 在业务高峰前后不再增长）
