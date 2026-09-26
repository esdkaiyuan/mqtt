# 后端（FastAPI）

摔倒检测系统的后端服务：接收 ESP32 的 WebSocket 上报、入库、执行摔倒检测，
并通过 REST API 与只读 WebSocket 推送供前端使用。

## 技术栈

FastAPI · SQLAlchemy 2.x（异步）· Pydantic v2 · aiosqlite / asyncpg · Alembic

## 快速开始

```bash
cd backend
./start_backend.sh        # Windows: start_backend.bat
```

脚本会自动生成 `.env`、创建 `venv`、安装依赖，并在 `http://localhost:8000` 启动
（`--reload`，默认 SQLite，无需 PostgreSQL）。接口文档见 http://localhost:8000/docs。

手动等价步骤：

```bash
python -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## 结构

```
app/
├── main.py                 # 应用入口：lifespan 建表、CORS、挂载路由
├── config.py               # pydantic-settings 配置（含摔倒检测参数）
├── database.py             # 异步引擎 / AsyncSessionLocal / get_db
├── models.py               # SQLAlchemy 模型（表结构唯一权威）
├── schemas.py              # Pydantic DTO
├── routers/
│   ├── api.py              # /api 数据查询、设备信息、统计、CSV 导出
│   ├── annotations.py      # /api/annotations 区间标注 CRUD
│   └── websocket.py        # /ws/motion/{id} 写入、/ws/view/{id} 只读推送
└── services/
    ├── fall_detection.py   # 滑动窗口检测（有状态，按设备）
    ├── data_service.py     # 统计与摔倒事件写入
    └── connection_manager.py  # WebSocket 连接注册表
```

路由挂在 `/api` 下，WebSocket 使用绝对路径 `/ws/...`。设备上报与前端订阅由
`ConnectionManager` 的两个独立注册表隔离，广播不会回传给设备。

## 文档

接口参数、响应形状与 WebSocket 协议见 [../docs/api.md](../docs/api.md)；
环境变量、数据库、部署与运维见 [../docs/deployment.md](../docs/deployment.md)；
开发与调试见 [../docs/development.md](../docs/development.md)。