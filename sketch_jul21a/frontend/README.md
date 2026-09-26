# 前端（Vue 3 + Vite）

摔倒检测系统的 Web 界面：实时波形、3D 姿态、数据标注、数据导出与统计仪表板。

## 技术栈

Vue 3（`<script setup>`）· Vue Router 4 · Pinia · Chart.js · Three.js · Element Plus · Axios

## 快速开始

```bash
cd frontend
./start.sh                # Windows: start.bat
```

首次运行自动 `npm install`，随后启动在 `http://localhost:3000`。

```bash
npm run dev               # 开发服务器（:3000）
npm run build             # 生产构建，产物在 dist/（sourcemap 关闭）
npm run preview           # 预览构建产物
```

## 与后端的连接

默认走**同源相对路径**，由开发服务器代理到后端，因此不需要处理跨域：

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE` | `/api` | REST 前缀 |
| `VITE_WS_BASE` | 空 | WebSocket 前缀；留空用当前页面 origin |
| `VITE_PROXY_TARGET` | `http://localhost:8000` | 代理目标，由 `vite.config.js` 读 `process.env`，需在 shell 中导出 |

`vite.config.js` 把 `/api` 与 `/ws` 代理到 `VITE_PROXY_TARGET`（`/api` 不做 rewrite，
因为后端路由本身已带该前缀）。切换后端地址：

```bash
VITE_PROXY_TARGET=http://192.168.1.100:8000 npm run dev
```

生产环境由 `nginx.conf` 承担同样的代理职责（`/api/` 与 `/ws/` → `backend:8000`），
并处理 Vue Router history 模式的 fallback。

## 结构

```
src/
├── router/index.js         # 5 个路由：/dashboard /waveform /3d-view /annotation /export
├── stores/motion.js        # Pinia：实时数据、连接状态、设备信息、摔倒事件、统计、标注
├── services/api.js         # axios 封装（REST，含 CSV 下载）
├── services/websocket.js   # WebSocketService 单例（自动重连，默认设备 ESP32_001）
├── views/                  # Dashboard / Waveform / ThreeDView / DataAnnotation / DataExport
└── components/             # Navbar / Sidebar / StatsCards / RealTimeChart / ThreeScene / DataTable
```

实时数据统一进 Pinia store：`services/websocket.js` 解析后端推送的
`sensor_data` / `fall_detected` / `device_info` 三类消息并写入 store，各页面从 store 读取，
组件之间不层层透传。实时缓冲区上限 500 点。

## 文档

页面功能与操作说明见 [../docs/user-guide.md](../docs/user-guide.md)；
WebSocket 推送格式见 [../docs/api.md](../docs/api.md#websocket前端实时订阅)；
开发规范见 [../docs/development.md](../docs/development.md)。