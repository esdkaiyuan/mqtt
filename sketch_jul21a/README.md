# 摔倒检测数据采集系统

基于 ESP32-S3 + MPU6500 的摔倒检测数据采集系统：实时采集人体运动姿态数据，
自动检测摔倒事件，并提供标注与导出功能，用于构建机器学习训练数据集。

## 系统架构

```
┌─────────────┐    WebSocket     ┌──────────────────┐   SQLAlchemy   ┌──────────────┐
│  ESP32-S3   │ ───────────────► │   FastAPI 后端    │ ─────────────► │ SQLite /     │
│  + MPU6500  │  /ws/motion/{id} │    :8000         │                │ PostgreSQL   │
└─────────────┘  批量 JSON 数组   └──────────────────┘                └──────────────┘
                                          ▲   │
                          /ws/view/{id}   │   │  REST /api/*
                          实时只读推送     │   ▼
                                  ┌──────────────────┐
                                  │  Vue 3 前端 :3000 │
                                  └──────────────────┘
```

设备侧只写入、前端只读取，两条 WebSocket 通道相互隔离，避免回环与数据注入。

## 功能特性

**固件（ESP32-S3）**
- WiFi 自动重连；MPU6500 六轴数据 100Hz 采样
- 每 10 个样本打包为一个 JSON 数组经 WebSocket 上报
- 环形缓冲区（1000 样本）断线暂存，重连后按顺序补发，不重不漏
- LED 指示 WiFi / 连接 / 发送状态

**后端（FastAPI）**
- WebSocket 接入与只读推送双通道
- 基于滑动窗口阈值的摔倒检测（带状态抑制，单次撞击只产生一个事件）
- REST API：数据查询、统计、设备信息、摔倒事件、区间标注 CRUD、CSV 导出
- SQLite（本地开发）/ PostgreSQL（容器部署）双数据库支持

**前端（Vue 3）**
- 实时波形（6 条曲线）、3D 姿态可视化
- 数据标注（区间选择 + 类型/置信度/质量/标签）
- 数据导出（筛选 + 下载）与仪表板统计

## 目录结构

```
sketch_jul21a/
├── esp32_firmware/          # 固件（PlatformIO，推荐）
│   ├── include/             # config.h / mpu6500.h / websocket_client.h
│   ├── src/                 # main.cpp / mpu6500.cpp
│   ├── platformio.ini
│   └── CONFIGURATION.md     # 固件配置说明
├── arduino_firmware/        # Arduino IDE 版固件
│   └── ESP32_Raw_Data/      # 单文件版本，功能等价
├── backend/                 # FastAPI 后端
│   ├── app/
│   │   ├── main.py          # 应用入口（含 lifespan 建表）
│   │   ├── config.py        # 配置（pydantic-settings）
│   │   ├── database.py      # 异步引擎 / 会话
│   │   ├── models.py        # SQLAlchemy 模型
│   │   ├── schemas.py       # Pydantic DTO
│   │   ├── routers/         # api.py / annotations.py / websocket.py
│   │   └── services/        # fall_detection / data_service / connection_manager
│   ├── alembic/             # 数据库迁移
│   ├── Dockerfile
│   ├── .env.example
│   └── start_backend.sh|bat # 本地开发启动（SQLite）
├── frontend/                # Vue 3 前端
│   ├── src/{views,components,stores,services,router}
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── .env.example
│   └── start.sh|bat         # 本地开发启动（Vite）
├── database/init.sql        # PostgreSQL 初始化（表 / 视图 / 清理函数）
├── docs/                    # 详细文档
├── scripts/                 # 备份 / 恢复 / 测试数据生成
├── archive/                 # 归档的历史产物（见 archive/README.md）
├── docker-compose.yml
└── start.sh|bat             # Docker 一键启动
```

## 快速开始

### 前置要求

- Docker 与 Docker Compose（容器方式）
- 或：Python 3.11+、Node.js 20+
- PlatformIO 或 Arduino IDE（烧录固件）

### 方式一：Docker 一键启动（推荐）

```bash
# Windows
start.bat

# Linux / macOS
./start.sh
```

脚本会构建并启动 `postgres + backend + frontend`，并等待后端健康检查通过。

| 服务 | 地址 |
| --- | --- |
| 前端界面 | http://localhost:3000 |
| 后端 API | http://localhost:8000 |
| API 文档（Swagger） | http://localhost:8000/docs |
| PostgreSQL | localhost:5432 |

常用命令：

```bash
docker compose logs -f          # 查看日志
docker compose ps               # 查看状态
docker compose down             # 停止服务
docker compose up -d --build    # 重新构建并启动
```

### 方式二：本地开发（SQLite，无需 Docker）

```bash
# 后端：自动创建 .env 与虚拟环境，启动在 :8000
cd backend
./start_backend.sh        # Windows: start_backend.bat

# 前端：另开一个终端，启动在 :3000，/api 与 /ws 自动代理到 :8000
cd frontend
./start.sh                # Windows: start.bat
```

### 烧录固件

```bash
cd esp32_firmware
pio run -t upload         # PlatformIO
pio device monitor        # 串口日志，115200
```

烧录前必须修改 `esp32_firmware/include/config.h` 中的 WiFi 与后端地址：

```cpp
#define WIFI_SSID       "8202"
#define WIFI_PASSWORD   "88888888"
#define WS_SERVER_HOST  "192.168.1.100"  // 运行后端的电脑局域网 IP
#define DEVICE_ID       "ESP32_001"
```

> 硬件接线与完整配置项见 [`esp32_firmware/CONFIGURATION.md`](esp32_firmware/CONFIGURATION.md)。

## 数据流

1. ESP32 以 100Hz 采样，每 10 个样本打包为一个 JSON 数组发往 `/ws/motion/{device_id}`
2. 后端逐条入库并执行摔倒检测；命中则写入 `fall_events`
3. 后端通过 `/ws/view/{device_id}` 把新数据与摔倒事件推送给前端
4. 前端实时渲染波形与 3D 姿态；用户在标注页选择区间并保存标注
5. 在导出页按条件筛选并下载 CSV，用于模型训练

设备上报的 `timestamp` 可以是 ISO-8601 字符串、Unix 秒/毫秒，或设备上电以来的
毫秒计数（固件使用 `millis()`）——后端会按设备锚定到墙钟时间。

## 摔倒检测算法

基于滑动窗口阈值，参数可在 `backend/.env` 中调整：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `FALL_WINDOW_SIZE` | 50 | 检测窗口样本数（100Hz 下约 500ms） |
| `FALL_ACCEL_THRESHOLD` | 2.5 | 加速度幅值阈值（g） |
| `FALL_GYRO_THRESHOLD` | 300.0 | 角速度幅值阈值（°/s） |
| `FALL_SAMPLE_RATE` | 100 | 采样率（Hz） |

判定条件为窗口内加速度幅值与角速度幅值同时超过阈值。为避免同一次撞击在窗口
重叠期被反复触发，检测服务会锁定 `is_falling` 状态，直到加速度回落到阈值以下才复位。

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/api.md](docs/api.md) | REST 与 WebSocket 接口参考 |
| [docs/deployment.md](docs/deployment.md) | 部署、配置与运维（Docker / 本地 / 生产） |
| [docs/development.md](docs/development.md) | 开发环境、调试技巧、代码规范 |
| [docs/user-guide.md](docs/user-guide.md) | 各页面功能与典型使用场景 |
| [esp32_firmware/CONFIGURATION.md](esp32_firmware/CONFIGURATION.md) | 固件配置与故障排除 |
| [archive/README.md](archive/README.md) | 归档产物说明 |

## 许可证

MIT License