# 开发指南

面向在本仓库上改代码的人：如何起开发环境、项目各部分职责、如何调试、如何扩展、
以及需要遵守的规范。

- [环境要求](#环境要求)
- [本地开发启动](#本地开发启动)
- [项目结构与职责](#项目结构与职责)
- [数据流与请求链路](#数据流与请求链路)
- [调试技巧](#调试技巧)
- [常见开发任务](#常见开发任务)
- [代码规范](#代码规范)
- [提交与验证](#提交与验证)

## 环境要求

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| Python | 3.11+ | 后端（镜像基于 `python:3.11-slim`） |
| Node.js | 20+ | 前端（镜像基于 `node:20-alpine`） |
| Docker | 任意近期版本 | 容器方式（含 Compose V2） |
| PlatformIO | 可选 | 编译/烧录固件（或 Arduino IDE 2.x） |

本地开发**不需要** PostgreSQL：后端默认 SQLite。

## 本地开发启动

两个终端，分别起后端与前端。

```bash
# 终端 1 —— 后端（SQLite，:8000，热重载）
cd backend
./start_backend.sh            # Windows: start_backend.bat

# 终端 2 —— 前端（Vite，:3000）
cd frontend
./start.sh                    # Windows: start.bat
```

前端以同源相对路径访问后端，由 Vite 代理转发：

| 前端请求 | 代理目标 |
| --- | --- |
| `/api/*` | `http://localhost:8000/api/*`（不 rewrite） |
| `/ws/*` | `ws://localhost:8000/ws/*`（`ws: true`） |

代理目标由 `VITE_PROXY_TARGET` 控制（`vite.config.js` 读的是 `process.env`，
需要在 shell 中导出，而不是写进 `.env`）：

```bash
VITE_PROXY_TARGET=http://192.168.1.100:8000 npm run dev
```

因此**不要**在 `.env` 里写死后端地址——保持 `VITE_API_BASE=/api`、`VITE_WS_BASE=` 留空即可，
这样开发和生产（Nginx 同源代理）行为一致，也不会有跨域问题。

首次启动后端会自动：从 `.env.example` 生成 `.env`、创建 `venv`、安装依赖。
数据库表由 `app/main.py` 的 `lifespan` 中 `Base.metadata.create_all` 在启动时创建。

## 项目结构与职责

```
sketch_jul21a/
├── backend/app/
│   ├── main.py                 # FastAPI 实例、lifespan 建表、CORS、挂载路由
│   ├── config.py               # pydantic-settings，所有可调参数
│   ├── database.py             # 异步引擎 / AsyncSessionLocal / get_db
│   ├── models.py               # SQLAlchemy 模型（唯一权威表结构）
│   ├── schemas.py              # Pydantic DTO（入参校验 / 出参形状）
│   ├── routers/
│   │   ├── api.py              # /api 数据查询、设备信息、统计、导出
│   │   ├── annotations.py      # /api/annotations 区间标注 CRUD
│   │   └── websocket.py        # /ws/motion/{id} 写入、/ws/view/{id} 只读
│   └── services/
│       ├── fall_detection.py   # 滑动窗口摔倒检测（有状态，按设备）
│       ├── data_service.py     # 统计与事件写入
│       └── connection_manager.py  # WebSocket 连接注册表（senders / viewers）
├── frontend/src/
│   ├── router/index.js         # 路由表（dashboard/waveform/3d-view/annotation/export）
│   ├── stores/motion.js        # Pinia：实时数据、连接状态、设备信息、摔倒事件
│   ├── services/api.js         # axios 封装（REST）
│   ├── services/websocket.js   # 单例 WebSocketService（含重连）
│   ├── views/                  # 5 个页面
│   └── components/             # 波形图、3D 场景、表格、统计卡、导航
├── esp32_firmware/
│   ├── include/config.h        # 全部可调参数集中于此
│   ├── include/mpu6500.h       # 传感器驱动接口
│   ├── include/websocket_client.h  # WebSocketManager（批量上报）
│   └── src/{main.cpp,mpu6500.cpp}
├── database/init.sql           # PostgreSQL 预置结构（镜像 models.py）
└── scripts/                    # backup / restore / generate_test_data
```

分层约定：

- **routers** 只做 HTTP/WS 协议适配（解析、校验、返回），不写业务逻辑。
- **services** 承载业务逻辑与状态，不依赖 FastAPI。
- **schemas** 定义边界处的数据形状；数据库行通过 `to_dict()` 序列化。
- **config** 是参数的唯一来源；不要在业务代码里硬编码阈值。

## 数据流与请求链路

```
ESP32 ──ws /ws/motion/{id}──► routers/websocket.py
                                 │  _parse_timestamp  归一化时间
                                 │  _persist_sample   入库
                                 │  FallDetectionService.detect  判定
                                 │  DataService.create_fall_event 命中则记事件
                                 ▼
                        connection_manager.broadcast_to_viewers
                                 │  {type: sensor_data} / {type: fall_detected}
                                 ▼
前端 ──ws /ws/view/{id}──► services/websocket.js ──► Pinia motion store ──► 图表 / 3D
前端 ──REST /api/*──────► services/api.js ──► routers/api.py, annotations.py
```

两条 WebSocket 通道由 `ConnectionManager` 的 `_senders` / `_viewers` 两个注册表隔离：
广播只发给 viewers，设备收不到自己产生的数据，viewers 也无法写入数据库。
`online` 状态取自 `_senders`（当前是否有上报连接），而非“最近有数据”。

**时间戳归一化**是链路里的关键点：固件上报 `millis()`（设备上电毫秒数），后端在
`_parse_timestamp` 中按设备首次上报时刻锚定到墙钟时间，偏移量存在 `_clock_offsets`，
设备断开时清理。改动这里要同时考虑 ISO 字符串与 Unix 秒/毫秒三种输入。

## 调试技巧

### 后端

```bash
cd backend
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload   # 热重载 + 日志
```

- 交互式调试接口：http://localhost:8000/docs
- 检查导入是否被破坏（改完配置/依赖后快速自检）：

  ```bash
  cd backend
  python -c "from app.main import app; print(len(app.routes), 'routes')"
  ```

- 依赖版本问题：`pip install -r requirements.txt` 已固定版本；升级请同步更新
  `requirements.txt` 与 `Dockerfile` 的构建依赖（`gcc`、`libpq-dev` 用于编译 asyncpg）。

### 数据库

本地 SQLite：

```bash
cd backend
python -c "import sqlite3;c=sqlite3.connect('fall_detection.db');print(c.execute('select count(*) from motion_data').fetchone())"
```

容器 PostgreSQL：

```bash
docker compose exec postgres psql -U fall_user -d fall_detection
```

```sql
\dt                                              -- 表
\d motion_data                                   -- 表结构
SELECT device_id, COUNT(*) FROM motion_data GROUP BY device_id;
SELECT * FROM fall_events ORDER BY detected_at DESC LIMIT 10;
SELECT * FROM data_stats;                        -- 汇总视图
SELECT * FROM fall_stats;
```

### 模拟设备上报

不必接硬件，直接往写入通道灌数据：

```bash
cd scripts
python generate_test_data.py --count 1000 --falls 5 --device ESP32_001 --server localhost:8000
```

或手动发一帧（需先 `pip install websockets`）：

```python
import asyncio, json, websockets

async def main():
    async with websockets.connect("ws://localhost:8000/ws/motion/test") as ws:
        await ws.send(json.dumps([
            {"timestamp": 12345, "ax": 0.12, "ay": 0.05, "az": 9.81,
             "gx": 1.23, "gy": -0.45, "gz": 0.67}
        ]))
        print(await ws.recv())   # {"status":"ok","count":1,...}

asyncio.run(main())
```

### REST

```bash
curl http://localhost:8000/health
curl "http://localhost:8000/api/stats"
curl "http://localhost:8000/api/data?device_id=ESP32_001&limit=5"
curl "http://localhost:8000/api/device/info?device_id=ESP32_001"
curl http://localhost:8000/ws/status          # 当前连接概况
```

接口参数与响应形状见 [api.md](api.md)。

### 前端

- **Network → WS**：选中 `/ws/view/...`，查看每帧推送的 `type`，确认
  `sensor_data` / `fall_detected` / `device_info` 是否按预期到达。
- **Vue Devtools**：查看 Pinia `motion` store 的 `data`、`connectionStatus`、`deviceInfo`。
- **代理不生效**：确认 `VITE_PROXY_TARGET` 是通过环境变量传入的（写进 `.env` 无效）。
- **构建自检**：

  ```bash
  cd frontend
  npm run build
  ```

### 固件

```bash
cd esp32_firmware
pio run                 # 编译
pio run -t upload       # 烧录
pio device monitor      # 串口日志（115200）
```

串口每 5 秒打印一次 WiFi/WebSocket 状态、发送成功/失败计数、缓冲区占用与实际采样率。
配置项与故障排除见 [`../esp32_firmware/CONFIGURATION.md`](../esp32_firmware/CONFIGURATION.md)。

## 常见开发任务

### 新增一个 REST 接口

1. 在 `schemas.py` 定义入参/出参 DTO（用 `Field` 加约束）。
2. 在 `routers/api.py`（或 `annotations.py`）加路由函数，用 `Depends(get_db)` 拿会话。
3. 业务逻辑放到 `services/`，不要在路由里直接拼复杂查询。
4. 更新 [api.md](api.md)。

### 调整摔倒检测

参数（阈值、窗口）走 `.env`，无需改代码：

```env
FALL_WINDOW_SIZE=50          # 100Hz 下约 500ms
FALL_ACCEL_THRESHOLD=2.5     # g
FALL_GYRO_THRESHOLD=300.0    # °/s
```

改算法则编辑 `services/fall_detection.py`。当前实现要点：

- 每设备维护两个 `deque(maxlen=window_size)`，存**幅值**序列（`sqrt(ax²+ay²+az²)`、
  `sqrt(gx²+gy²+gz²)`），而非原始分量。
- 窗口未填满（不足 `window_size` 个样本）时不判定。
- 判定条件：加速度峰值超阈值 **且**（角速度峰值超阈值 **或** 出现减速模式）。
- 减速模式：峰值不在窗口末尾 5 个样本内，且峰值之后的均值 < `accel_threshold * 0.6`。
- 置信度：加速度贡献 0–0.4、角速度贡献 0–0.4、减速模式加成 0.2，上限 1.0。
- **状态抑制**：命中后 `is_falling` 锁定，直到整个窗口的加速度最大值回落到阈值以下才复位，
  保证一次撞击只产生一个事件（这也是 `total_falls` 样本数大于 `total_fall_events` 事件数的原因）。
- `_classify_fall_type` 目前固定返回 `detected`；要区分方向需把原始 `ax/ay/az` 窗口
  传进来，而不是幅值序列。

> 检测服务是**有状态**的（按 device_id 持有缓冲区），且只在 `routers/websocket.py` 中
> 以模块级单例存在。多进程部署（多 worker）会让同一设备的数据分散到不同进程，
> 导致窗口不完整——需要多 worker 时，应把状态外移到 Redis 之类的共享存储。

### 新增前端页面

1. `src/views/` 新建组件。
2. `src/router/index.js` 加路由（`meta.title` 用于导航显示）。
3. `src/components/Sidebar.vue` 加菜单项。
4. 数据获取统一走 `services/api.js` / `services/websocket.js`，不要在组件里直接 `fetch`。

### 新增固件配置项

1. 在 `esp32_firmware/include/config.h` 定义，并加注释说明单位与取值范围。
2. 需要同步的 Arduino IDE 版本改 `arduino_firmware/ESP32_Raw_Data/ESP32_Raw_Data.ino`
   （两套固件配置各自独立维护）。
3. 更新 [`../esp32_firmware/CONFIGURATION.md`](../esp32_firmware/CONFIGURATION.md)。

## 代码规范

### Python

- 遵循 PEP 8；公开函数写类型注解与简短 docstring。
- 模块顶部写一行模块职责说明（现有文件均如此）。
- 边界处用 Pydantic 校验；内部函数信任调用方，不做冗余防御。
- 数据库会话用 `Depends(get_db)` 注入；不要在函数内自行创建引擎。
- 时区：当前统一用 naive UTC（`datetime.utcnow()`），新增代码保持一致。

### JavaScript / Vue

- Vue 3 `<script setup>` + Composition API。
- 全局状态放 Pinia（`stores/motion.js`），组件间不通过 props 层层透传实时数据。
- REST 走 `services/api.js`；WebSocket 走 `services/websocket.js` 单例。
- 缩进 2 空格，无分号风格，字符串用单引号（与现有代码一致）。

### C++（固件）

- 头文件放声明、`.cpp` 放实现；驱动与传输分离（`mpu6500` / `websocket_client`）。
- 所有可调参数集中在 `config.h`，代码中不出现魔数。
- 调试输出统一用 `DEBUG_PRINT` / `DEBUG_PRINTF` 宏，受 `DEBUG_ENABLE` 控制，
  便于生产关闭。
- 编译开启 `-Wall`，不留新告警。

### 文档

- 面向用户的说明放 `README.md` / `docs/`；不要新增一次性状态报告或"完成清单"类文件。
- 接口变更必须同步 [api.md](api.md)；配置项变更必须同步
  [deployment.md](deployment.md) 与固件 `CONFIGURATION.md`。

## 提交与验证

提交信息用语义化前缀：`feat:` / `fix:` / `docs:` / `style:` / `refactor:` / `test:` / `chore:`。

提交前自检：

```bash
# 后端：导入与路由挂载
cd backend && python -c "from app.main import app; print(len(app.routes), 'routes')"

# 前端：生产构建
cd frontend && npm run build

# 固件：编译（需装 PlatformIO）
cd esp32_firmware && pio run
```