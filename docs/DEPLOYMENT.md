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

## 5. 生产环境建议

1. **更换JWT密钥：** 修改`.env`中的`JWT_SECRET`为强随机字符串
2. **启用HTTPS：** 在Nginx配置中添加SSL证书
3. **配置防火墙：** 仅开放必要端口（80/443）
4. **设置日志轮转：** 配置Docker日志大小限制
5. **定期备份：** 设置自动备份脚本
6. **监控告警：** 后端已内置 Actuator + Micrometer，可直接由 Prometheus 抓取 `http://<host>:8080/api/actuator/prometheus`，再接入 Grafana 展示
7. **开启设备接入强校验：** 全部设备刷机完成后，将 `ACCESS_CONTROL_ENFORCE_AUTH` 置为 `true`、`DIRECT_FRONTEND_ENABLED` 置为 `false` 并重建 backend 与 emqx-init（见 `scripts/migrate-device-secrets.md`）
8. **保护内部回调接口：** `INTERNAL_TOKEN` / `PLATFORM_SECRET` 必须为强随机值（`scripts/deploy.sh` 会自动生成）；`/api/internal/*` 已由 Nginx 拒绝外部访问，请勿在网关层放开
