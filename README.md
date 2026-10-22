# MQTT云平台

> 可自部署的 MQTT 物联网平台：设备接入、物模型、命令与影子、规则告警、场景联动、OTA、看板与快速上位机。

## 技术栈

- **前端：** Vue 3 + Vite + Element Plus + Vue Flow + ECharts
- **后端：** Spring Boot 4.1 + MyBatis-Plus + Flyway
- **消息：** EMQX（MQTT over TCP / TLS / WebSocket）
- **存储：** MySQL 8（业务数据）+ Redis（会话 / 在线状态 / 缓存）
- **部署：** Docker Compose（MySQL / Redis / EMQX / backend / frontend）

## 功能特性

| 能力 | 说明 |
|---|---|
| 设备接入 | 产品模板、物模型 TSL、一机一密、X.509 / JWT、HTTP 上报、多级网关代理 |
| 命令与影子 | 物模型校验、同步 / 异步命令、离线补发、desired / reported / delta、多设备影子聚合 |
| 数据查询 | 属性最新值、事件、属性时序分桶、消息历史、设备统一日志、SSE 实时推送、CSV 导出 |
| 组织与权限 | 分组树、标签、批量操作、分组成员共享（VIEW / OPERATE）、ADMIN / OPERATOR / VIEWER |
| 规则与告警 | 多条件多动作规则、四档作用域；阈值 / 离线 / 事件告警、窗口聚合、抑制、升级、分派、多渠道通知 |
| 场景联动 | 属性 / 事件 / 定时 / 上下线 / 影子变更触发；分支、循环、并行汇合；表单与画布双视图；版本回滚 |
| OTA | 固件包、按产品 / 分组 / 标签 / 设备下发、灰度分批、暂停 / 恢复 / 终止、MD5 完整性校验 |
| 可视化 | 可保存看板、公开只读大屏与轮播、设备属性趋势、快速上位机（HMI）Web 组态 |
| 运维调试 | 设备调试中心、模拟上行、设备端 / 上位机端属性代码、平台侧 Modbus TCP 主站（多从站 / 64 位整型） |

## 快速开始

### 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 4GB+ 内存
- 端口 `80`、`1883`、`8083`、`8883`、`18083`、`3306`、`6379`、`8080` 可用（可在 `.env` 调整宿主映射）

### 一键部署

```bash
git clone <repository-url>
cd MQTT自建站点

cp .env.example .env
# 编辑 .env：数据库密码、JWT_SECRET、对外地址等
# JWT_SECRET 留空时 deploy.sh 会自动生成（Base64 且解码后不少于 32 字节）

bash scripts/deploy.sh
```

前后端均在容器内构建，本机无需 Maven / Node。首次启动会由 Flyway 自动执行 `V1`~`V30` 迁移。
`JWT_SECRET` 为必填项：Docker 部署必须写入 `.env`（`deploy.sh` 留空时会自动生成），本机直接启动后端也必须通过环境变量设置至少 32 字节的密钥。

### 访问入口

| 入口 | 地址 |
|---|---|
| 工作台 / 官网 | http://localhost |
| Swagger（管理端 API） | http://localhost/api/swagger-ui.html |
| 站内文档站 | http://localhost/docs |
| EMQX 控制台 | http://localhost:18083 |

默认账号：`admin / admin123`。**首次登录后请立即修改密码。**

### 常用运维

```bash
# compose 文件位于 docker/，需显式指定 --env-file
docker compose --env-file .env -f docker/docker-compose.yml ps
docker compose --env-file .env -f docker/docker-compose.yml logs -f backend
docker compose --env-file .env -f docker/docker-compose.yml down       # 保留数据卷
docker compose --env-file .env -f docker/docker-compose.yml down -v    # 清空数据卷
```

## 首次使用

### 1. 创建产品与物模型

进入「产品管理」创建产品，配置 `protocol_type` 与物模型（属性 / 事件 / 服务）。
物模型是命令校验、影子属性、告警阈值与属性趋势的共同依据，建议先完成建模再创建设备。

### 2. 创建设备与凭据

在「设备管理」下创建设备，系统生成唯一 `deviceKey`。设备密钥只在创建 / 重置时展示一次，
平台仅保存哈希；遗失后请重置，不要尝试从数据库还原。

### 3. 选择接入方式

| 方式 | 适用场景 | 关键配置 |
|---|---|---|
| MQTT | 直连设备 / 网关 | Broker `1883`（TLS `8883`），用户名 `{productKey}.{deviceKey}`，密码为设备密钥 |
| HTTP 上报 | 低功耗 / 第三方系统 | `POST /api/ingest/{deviceKey}/{messageType}`，HTTP Basic 设备凭据 |
| 平台 Modbus 主站 | 纯 Modbus 设备 | 产品 `driver_config` + 设备点表，`executor=PLATFORM`，默认关闭 |
| 网关代理 | 子设备无法直连 | 网关凭据代连；支持多级 `SUB` 子网关 |

MQTT 主题、Alink JSON 载荷与 ACL 规则见 [D1](docs/D1_MQTT主题与ACL规范.md) / [D2](docs/D2_设备接入协议规范.md)。
按芯片分类的完整接入示例见站内文档：`/docs/examples/general`（通用流程）、`/docs/examples/esp32`、`/docs/examples/stm32`、`/docs/examples/linux`、`/docs/examples/cellular`。
Qt 桌面上位机模板见 `qt-hmi-template/`，一键生成工程：

```bash
node scripts/create-hmi.mjs --out ../line1-hmi --device sensor-temp-001
# 可选：直接使用产品页导出的物模型 TSL 生成 model.json
node scripts/create-hmi.mjs --out ../line1-hmi --device sensor-temp-001 --tsl ./product-tsl.json
```

使用说明见 `/docs/hmi/qt` 与 `qt-hmi-template/README.md`。

### 4. 组织设备

在「分组管理」创建分组树、标签，把设备加入分组；批量命令 / 启停 / 打标支持按分组（含子分组）或标签。
分组所有者可通过「共享」把分组子树以 `VIEW`（只读）或 `OPERATE`（可命令 / 写影子）共享给其他用户。

### 5. 配置自动化

- **消息规则**：按属性 / 事件、多条件、多动作过滤与转发；
- **告警规则**：阈值（支持 AVG / MIN / MAX / SUM / COUNT 窗口）、离线、事件；配置抑制、升级与通知渠道；
- **场景联动**：属性 / 事件 / 定时 / 上下线 / 影子变更触发，支持条件组、分支、循环、并行汇合与版本回滚。

所有规则与场景均可先「试运行」干跑，确认命中结论后再启用。

### 6. 运维与可视化

- **OTA**：上传固件 → 建升级任务（产品 / 分组 / 标签 / 手选设备）→ 观察批次与逐台记录；
- **看板 / 大屏**：保存多面板看板，生成公开只读分享链接并在 `/display/{token}` 轮播；
- **设备调试 / HMI**：调试中心查看连接参数、模拟上行、生成设备端与上位机端代码；快速上位机按物模型生成 Web 组态；
- **设备日志**：单设备统一时间线（消息 / 事件 / 命令 / 状态），支持实时刷新与 CSV 导出。

## API 使用

### 两类入口

| 入口 | 认证 | 用途 |
|---|---|---|
| `/api/**` | 登录 JWT（`Authorization: Bearer`） | 工作台管理端，Swagger 可查 |
| `/api/external/v1/**` | API Key（`X-API-Key`） | 第三方只读集成 + 命令 / 影子写 |

API Key 仅 ADMIN 可在「开发接入 → API 密钥」创建与撤销，明文只展示一次。密钥绑定创建者账号，
`/external/v1` 的数据范围严格限定为该账号名下设备。

```bash
curl -X GET "https://your-server/api/external/v1/devices" \
  -H "X-API-Key: your-api-key"
```

> `permissions` 已按 scope 生效：读取需要 `device:read`，写入需要 `device:write`，统计需要 `stats:read`；
> 留空表示全部权限。请按最小权限分发密钥；不要把密钥暴露在前端代码或公开仓库。

### 端点概览

- 设备与状态：`GET /devices`、`/devices/{deviceKey}`、`/devices/{deviceKey}/status`
- 命令与影子：`POST /devices/{deviceKey}/command`、`GET .../commands`、`GET .../shadow`、`PUT .../shadow/desired`
- 属性 / 事件 / 时序：`GET .../properties`、`.../events`、`.../properties/history`
- 消息 / 日志：`GET .../messages`、`.../logs`
- 规则 / 告警：`GET /rules`、`/alerts`、`/alerts/{id}`、`/alerts/unread-count`、`/alerts/rules`
- OTA：`GET /ota/firmwares`、`/ota/tasks`、`/ota/tasks/{id}`、`/ota/tasks/{id}/records`
- 场景：`GET /scenes`
- 统计：`GET /stats`
- HTTP 上报（设备凭据，非 API Key）：`POST /api/ingest/{deviceKey}/{messageType}`

完整路径、参数与响应示例见站内文档「开放 API → REST API 参考」（`/docs/api/rest`）。
功能使用说明见 `/docs/features/devices`、`/docs/features/automation`、`/docs/features/operations`、`/docs/features/access`。
管理端接口以 Swagger 为准；错误码与处置建议见 `/docs/api/errors`。

## 项目结构

```
MQTT自建站点/
├── backend/              # Spring Boot 后端（controller / service / mapper / entity / dto）
├── frontend/             # Vue 3 工作台、官网与文档站
│   └── src/
│       ├── api/          # 接口封装
│       ├── components/   # 通用与业务组件
│       ├── composables/  # 查询 / 变更组合式函数
│       ├── views/        # 工作台、公开站、文档站
│       └── router/       # 路由与角色守卫
├── docker/               # Docker Compose 与镜像配置
├── qt-hmi-template/      # Qt 5/6 桌面上位机模板（Open API + 物模型驱动）
├── scripts/              # 部署、健康检查、EMQX 初始化、上位机脚手架
├── docs/                 # 架构、部署、协议规范与 T-xx 任务文档
└── README.md
```

## 文档

- 站内文档站：`http://localhost/docs`（快速开始 / 功能指南 / 设备接入 / 协议规范 / 开放 API / 平台运维）
- [系统架构](docs/ARCHITECTURE.md)
- [部署文档](docs/DEPLOYMENT.md)
- [D1 · MQTT 主题与 ACL 规范](docs/D1_MQTT主题与ACL规范.md)
- [D2 · 设备接入协议规范](docs/D2_设备接入协议规范.md)
- [设备接入（ESP32）](docs/ESP32_接入手册.md)
- [总督促文档](docs/总督促文档.md)
- [平台功能链路路线图](docs/平台功能链路路线图.md)
- [头部平台对标与剩余缺口](docs/头部平台对标_尚未补齐功能项清单.md)

## 开发与测试

### 后端

```bash
cd backend
sh ../scripts/mvnw.sh -B verify          # 全量单测 + ArchUnit + 打包
sh ../scripts/mvnw.sh -B -DskipTests compile
```

> `scripts/mvnw.sh` 会自行切到 `backend/`，请在仓库根目录执行；本地需 Java 17。

### 前端

```bash
cd frontend
npm install
npm run dev
npx --no-install eslint src
npx --no-install vitest run
npx --no-install vite build
```

### 数据库迁移

新增表 / 列一律通过 `backend/src/main/resources/db/migration/V{n}__*.sql`，不要在 `init-mysql.sql` 手工补列。
应用启动时由 Flyway 自动执行；生产升级前请先备份，并确认迁移版本单调。

## 安全提示

- 首次部署后修改默认管理员密码，并设置强 `JWT_SECRET`；
- 设备密钥、API Key、Webhook 密钥只展示一次，不要写入日志、文档示例或前端代码；
- 生产环境建议启用 TLS（`8883` / HTTPS）、限制 `/api/internal/**` 仅容器网络访问；
- 平台侧 Modbus 主站默认关闭，开启前确认容器到现场网络的访问边界。

## 许可证

MIT License
