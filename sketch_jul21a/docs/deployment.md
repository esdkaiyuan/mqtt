# 部署与运维

本系统由三个服务组成：PostgreSQL、FastAPI 后端、Vue 3 前端（Nginx）。
开发时后端可退化为 SQLite，无需数据库容器。

- [部署方式对比](#部署方式对比)
- [方式一：Docker Compose](#方式一docker-compose)
- [方式二：本地开发](#方式二本地开发)
- [环境变量](#环境变量)
- [数据库](#数据库)
- [局域网与多设备](#局域网与多设备)
- [生产环境](#生产环境)
- [备份与恢复](#备份与恢复)
- [数据保留与清理](#数据保留与清理)
- [生成测试数据](#生成测试数据)
- [故障排除](#故障排除)

## 部署方式对比

| 方式 | 数据库 | 适用场景 | 入口 |
| --- | --- | --- | --- |
| Docker Compose | PostgreSQL 15 | 演示、验收、准生产 | `start.sh` / `start.bat` |
| 本地开发 | SQLite | 改代码、调试 | `backend/start_backend.*` + `frontend/start.*` |

## 方式一：Docker Compose

### 前置要求

- Docker Desktop（含 Compose V2；脚本会自动回退到 `docker-compose`）

### 启动

```bash
# Linux / macOS
./start.sh

# Windows
start.bat
```

脚本会：准备 `backend/.env`（首次运行从 `.env.example` 复制）→ `docker compose up -d --build`
→ 轮询 `http://localhost:8000/health`（最多 60 秒）→ 打印状态与访问地址。

### 服务与端口

| 服务 | 容器名 | 端口 | 说明 |
| --- | --- | --- | --- |
| postgres | `fall_detection_db` | 5432 | 数据卷 `postgres_data`，首次启动执行 `database/init.sql` |
| backend | `fall_detection_backend` | 8000 | 依赖 postgres 健康检查通过 |
| frontend | `fall_detection_frontend` | 3000 | Nginx，依赖 backend 健康检查通过 |

| 地址 | 用途 |
| --- | --- |
| http://localhost:3000 | 前端界面 |
| http://localhost:8000 | 后端 API |
| http://localhost:8000/docs | Swagger |
| http://localhost:8000/redoc | ReDoc |
| http://localhost:8000/health | 健康检查 |

### Compose 细节

- **启动顺序**：`postgres` 用 `pg_isready` 健康检查，`backend` 用 `/health` 健康检查；
  `depends_on: condition: service_healthy` 保证依赖就绪后才启动。
- **数据库连接**：由 `docker-compose.yml` 的 `environment` 内联注入，**不读取** `backend/.env`。
  异步引擎使用 `postgresql+asyncpg://`，同步迁移使用 `postgresql://`。
- **初始化脚本**：`database/init.sql` 以只读方式挂载到 `docker-entrypoint-initdb.d/`，
  **仅在数据卷为空时执行一次**。已存在的数据卷不会重新执行。
- **CORS**：容器内 `CORS_ORIGINS=["http://localhost:3000"]`。
- **非 root 运行**：后端容器以 `appuser`(uid 1000) 运行，`/app/data` 保持可写。
- **重启策略**：backend / frontend 为 `unless-stopped`。
- **网络**：三者同处 `fall_detection_network` 桥接网络，服务间用容器名互访
  （Nginx 中 `proxy_pass http://backend:8000`）。

### 常用命令

```bash
docker compose ps                 # 状态
docker compose logs -f backend    # 后端日志
docker compose logs -f frontend
docker compose logs -f postgres
docker compose restart backend
docker compose down               # 停止（保留数据卷）
docker compose down -v            # 停止并删除数据卷（清空数据库）
docker compose up -d --build      # 重新构建并启动
```

> `down -v` 会删除 `postgres_data`，下次启动会重新执行 `init.sql`。属于破坏性操作，请谨慎。

## 方式二：本地开发

后端默认使用 SQLite（`backend/fall_detection.db`），无需 Docker 与 PostgreSQL。
表结构由启动时的 `Base.metadata.create_all` 自动创建。

### 后端

```bash
cd backend
./start_backend.sh        # Windows: start_backend.bat
```

脚本会创建 `.env`（从 `.env.example`）、创建并激活 `venv`、安装依赖，然后以 `--reload`
启动在 `http://localhost:8000`。

手动等价步骤：

```bash
cd backend
python -m venv venv
source venv/bin/activate          # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 前端

```bash
cd frontend
./start.sh                # Windows: start.bat
```

首次运行自动 `npm install`，随后 `npm run dev` 启动在 `http://localhost:3000`。

前端默认走**同源相对路径**：`VITE_API_BASE=/api`、`VITE_WS_BASE` 留空。
开发服务器把 `/api` 与 `/ws` 代理到 `VITE_PROXY_TARGET`（默认 `http://localhost:8000`），
因此前后端同源，无需处理跨域。

```bash
# 后端不在默认地址时
VITE_PROXY_TARGET=http://192.168.1.100:8000 npm run dev   # Windows PowerShell: $env:VITE_PROXY_TARGET="..."
```

### 生产构建

```bash
cd frontend
npm run build             # 产物在 dist/，sourcemap 关闭
```

## 环境变量

### backend/.env

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `DATABASE_URL` | `sqlite+aiosqlite:///./fall_detection.db` | 异步引擎连接串 |
| `DATABASE_URL_SYNC` | `sqlite:///./fall_detection.db` | 同步连接串（Alembic） |
| `APP_NAME` | `Fall Detection System` | 服务名 |
| `APP_VERSION` | `1.0.0` | 版本号 |
| `DEBUG` | `false`（`.env.example` 为 `true`） | 调试模式 |
| `CORS_ORIGINS` | `["*"]` | JSON 数组，允许的前端来源 |
| `FALL_WINDOW_SIZE` | `50` | 检测窗口样本数（100Hz 下约 500ms） |
| `FALL_ACCEL_THRESHOLD` | `2.5` | 加速度阈值（g） |
| `FALL_GYRO_THRESHOLD` | `300.0` | 角速度阈值（°/s） |
| `FALL_SAMPLE_RATE` | `100` | 采样率（Hz） |
| `DATA_RETENTION_DAYS` | `365` | 数据保留天数（供清理策略参考） |

PostgreSQL 连接串示例：

```env
DATABASE_URL=postgresql+asyncpg://fall_user:fall_password@localhost:5432/fall_detection
DATABASE_URL_SYNC=postgresql://fall_user:fall_password@localhost:5432/fall_detection
```

> 必须使用 `asyncpg` 驱动；`DATABASE_URL_SYNC` 用于 Alembic 等同步场景。

### frontend/.env

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE` | `/api` | REST 前缀 |
| `VITE_WS_BASE` | 空 | WebSocket 前缀；留空则用当前页面 origin |
| `VITE_PROXY_TARGET` | `http://localhost:8000` | 开发服务器代理目标（由 `vite.config.js` 读 `process.env`，需在 shell 中导出） |

## 数据库

SQLAlchemy 模型（`backend/app/models.py`）是**唯一权威**，后端启动时 `create_all` 建表。
`database/init.sql` 与之镜像，仅用于让 PostgreSQL 在首次启动时预置结构。

### 表与视图

| 对象 | 说明 |
| --- | --- |
| `motion_data` | 运动样本，索引 `(device_id, timestamp)` 与 `is_fall` |
| `fall_events` | 摔倒事件，索引 `device_id`、`detected_at DESC` |
| `annotations` | 区间标注，索引 `device_id`、`start_time` |
| `data_stats`（视图） | 按设备汇总样本数/摔倒数/首末时间 |
| `fall_stats`（视图） | 按设备汇总事件数/已确认数/首末时间 |
| `cleanup_old_data(retention_days)` | 按保留天数删除历史数据的函数 |

### Alembic 迁移

```bash
cd backend
alembic revision --autogenerate -m "描述"
alembic upgrade head
alembic downgrade -1
```

> 本地 SQLite 开发通常不需要迁移——`create_all` 已建表。Alembic 主要用于 PostgreSQL
> 的增量演进。

## 局域网与多设备

要让 ESP32 与其他电脑访问，需把三处地址统一改为运行后端的电脑局域网 IP：

```bash
ipconfig                                              # Windows：查找 "IPv4 地址"
ifconfig | grep "inet " | grep -v 127.0.0.1           # Linux / macOS
```

1. **固件** `esp32_firmware/include/config.h`：`WS_SERVER_HOST` 改为该 IP（不能用 `localhost`）。
2. **后端** `CORS_ORIGINS` 加入局域网来源：
   ```env
   CORS_ORIGINS=["http://192.168.1.100:3000","http://localhost:3000"]
   ```
3. **前端**：容器部署时 Nginx 已在同网络内代理到 `backend:8000`，无需改动；
   若前端直连后端，设 `VITE_API_BASE` / `VITE_WS_BASE` 为 `http://192.168.1.100:8000` / `ws://192.168.1.100:8000`。
4. **防火墙**放行 8000（必要时 3000）。

多设备：为每块 ESP32 设置唯一 `DEVICE_ID`，前端按设备筛选；后端按 device_id 隔离数据与
连接，无需额外配置。

验证：

```bash
curl http://<IP>:8000/health
curl http://<IP>:8000/ws/status
```

## 生产环境

```env
DEBUG=false
CORS_ORIGINS=["https://your-domain.com"]
DATABASE_URL=postgresql+asyncpg://secure_user:strong_password@db-host:5432/fall_detection
```

- **凭据**：修改 `docker-compose.yml` 中 `postgres` 的默认口令（`fall_user` / `fall_password`
  仅供本地演示）。
- **TLS / WSS**：ESP32 固件使用明文 `ws://`。若前端经 HTTPS 提供，页面内的 WebSocket 需为
  `wss://`，否则浏览器会因混合内容拦截。建议在前端 Nginx 前再加一层反向代理终结 TLS，
  并由 Nginx 转发 `/ws/` 到 `backend:8000`（配置见 `frontend/nginx.conf`，已设置
  `Upgrade` / `Connection` 头与 3600s 读写超时）。
- **健康检查**：后端镜像内置 `HEALTHCHECK` 调用 `/health`，编排系统可直接复用。
- **资源**：数据量增长主要来自 `motion_data`（100Hz ≈ 每秒 100 行/设备）。
  长期运行请配合数据保留策略与备份。

## 备份与恢复

```bash
# 备份（pg_dump + gzip，自动清理 30 天前的备份）
./scripts/backup.sh                       # 输出 ./backups/fall_detection_<时间戳>.sql.gz

# 恢复（覆盖现有数据，需交互确认）
./scripts/restore.sh ./backups/fall_detection_20260721_120000.sql.gz
```

脚本通过 `docker compose exec postgres` 操作，需在 `sketch_jul21a/` 目录下、容器运行时执行。
手动等价命令：

```bash
docker compose exec -T postgres pg_dump -U fall_user -d fall_detection > backup.sql
docker compose exec -T postgres psql -U fall_user -d fall_detection < backup.sql
```

> 备份脚本按 30 天保留**备份文件**，与数据库内的 `DATA_RETENTION_DAYS`（默认 365 天）
> 是两个独立策略。

## 数据保留与清理

```sql
-- 默认保留 365 天
SELECT cleanup_old_data();

-- 自定义保留天数
SELECT cleanup_old_data(90);
```

清理会同时删除 `motion_data` 与 `fall_events` 中超过保留期的记录（依据
`created_at` / `detected_at`），**不影响** `annotations`。

```bash
docker compose exec postgres psql -U fall_user -d fall_detection -c "SELECT cleanup_old_data(90);"
```

若需要定期自动清理，可将上述命令加入宿主机的计划任务（cron / 任务计划程序）。

## 生成测试数据

没有硬件时可模拟设备上报（数据经 WebSocket 写入，与真实设备同一条链路）：

```bash
cd scripts
pip install websockets
python generate_test_data.py --count 1000 --falls 5 --device ESP32_001 --server localhost:8000
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `--count` | 1000 | 目标数据点总数 |
| `--falls` | 5 | 插入的摔倒事件数 |
| `--device` | ESP32_001 | 设备 ID |
| `--server` | localhost:8000 | 后端地址（不含协议） |

脚本按“正常行走 → 摔倒 → 撞击”的时序构造波形，并逐点发往
`ws://<server>/ws/motion/<device>`。

## 故障排除

### 后端容器起不来 / 一直 unhealthy

```bash
docker compose logs backend
curl http://localhost:8000/health
```

常见原因：数据库未就绪（等 `postgres` healthy）、`DATABASE_URL` 缺少 `+asyncpg`、
端口 8000 被占用。

### 数据库连接失败

```bash
docker compose ps postgres
docker compose logs postgres
docker compose exec postgres psql -U fall_user -d fall_detection -c "\dt"
```

确认凭据与端口 5432；确认 `DATABASE_URL` 与 `DATABASE_URL_SYNC` 驱动前缀正确。

### 前端 502 / 接口 404

- 确认 `backend` 处于 healthy：`docker compose ps`
- 确认 Nginx 配置中 `proxy_pass http://backend:8000`（服务名而非 localhost）
- 确认请求路径带 `/api` 前缀——后端路由本身已含该前缀，Nginx 不做 rewrite

### 前端能打开但无实时数据

1. 浏览器开发者工具 → Network → WS，确认 `/ws/view/<device_id>` 已连接
2. 打开 `http://localhost:8000/ws/status`，确认 `active_devices` 含该设备
3. 若为空，检查固件 `WS_SERVER_HOST`、同网段与防火墙

### 改了 `init.sql` 但数据库没变化

`init.sql` 只在**空数据卷**上执行一次。需要重建时：

```bash
docker compose down -v && docker compose up -d --build
```

> 破坏性操作，会清空数据。请先备份。

### ESP32 连不上后端

见 [`../esp32_firmware/CONFIGURATION.md`](../esp32_firmware/CONFIGURATION.md#故障排除)
的「WebSocket 连接失败」一节。