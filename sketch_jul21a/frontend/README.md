# 摔倒检测系统 - Vue 3 前端

基于 Vue 3 的实时摔倒检测监控系统前端应用。

## 功能特性

- **实时波形显示** - 6条曲线实时显示加速度和陀螺仪数据
- **3D姿态可视化** - Three.js 3D立方体实时旋转展示设备姿态
- **数据标注界面** - 选择时间范围、标记摔倒类型、添加备注
- **仪表板页面** - 统计信息、设备状态、最近事件
- **数据导出功能** - CSV格式数据导出，支持筛选和自定义列

## 技术栈

- Vue 3 + Composition API
- Vue Router 4
- Pinia 状态管理
- Chart.js 实时波形图
- Three.js 3D场景
- Element Plus UI组件库
- WebSocket 实时数据接收
- Axios HTTP客户端

## 项目结构

```
frontend/
├── index.html
├── package.json
├── vite.config.js
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── router/
│   │   └── index.js
│   ├── stores/
│   │   └── motion.js
│   ├── services/
│   │   ├── websocket.js
│   │   └── api.js
│   ├── views/
│   │   ├── Dashboard.vue
│   │   ├── Waveform.vue
│   │   ├── ThreeDView.vue
│   │   ├── DataAnnotation.vue
│   │   └── DataExport.vue
│   └── components/
│       ├── Navbar.vue
│       ├── Sidebar.vue
│       ├── StatsCards.vue
│       ├── RealTimeChart.vue
│       ├── ThreeScene.vue
│       └── DataTable.vue
```

## 安装和运行

### 前置要求

- Node.js 16+ 
- npm 或 yarn

### 安装步骤

1. 进入前端目录
```bash
cd frontend
```

2. 安装依赖
```bash
npm install
```

3. 启动开发服务器
```bash
npm run dev
```

4. 在浏览器中访问
```
http://localhost:3000
```

### 构建生产版本

```bash
npm run build
```

构建产物将生成在 `dist` 目录中。

## 页面说明

### 1. 仪表板 (Dashboard)
- 统计卡片：总数据量、摔倒次数、今日数据、今日摔倒
- 连接状态：实时显示设备连接状态和设备信息
- 实时数据预览：小型波形图预览
- 最近摔倒事件列表

### 2. 实时波形 (Waveform)
- 6条曲线同时显示：ax, ay, az, gx, gy, gz
- 可调整时间窗口：5秒、10秒、30秒、60秒
- 暂停/继续功能
- 数据截取功能
- 显示实时数据信息

### 3. 3D姿态 (ThreeDView)
- Three.js 3D立方体实时旋转
- 显示俯仰角、横滚角、偏航角
- 实时传感器数据面板
- 支持鼠标拖动旋转视角
- 坐标轴和网格显示

### 4. 数据标注 (DataAnnotation)
- 在波形图上选择时间范围
- 标记摔倒类型（前倒、后倒、侧倒等）
- 添加置信度和备注
- 批量标注功能
- 标注记录列表

### 5. 数据导出 (DataExport)
- 时间范围筛选
- 摔倒类型筛选
- 自定义导出列
- CSV格式导出
- 采样率选择
- 导出历史记录

## WebSocket 连接

应用通过 WebSocket 接收实时数据。连接地址格式：
```
ws://localhost:8080/ws
```

### 消息格式

**传感器数据**
```json
{
  "type": "sensor_data",
  "timestamp": 1697356800000,
  "ax": 0.12,
  "ay": -0.05,
  "az": 1.02,
  "gx": 1.5,
  "gy": -2.3,
  "gz": 0.8
}
```

**摔倒事件**
```json
{
  "type": "fall_detected",
  "fall_type": "forward_fall",
  "confidence": 0.95,
  "ax": 2.5,
  "ay": -1.2,
  "az": 0.8,
  "gx": 45.2,
  "gy": -23.1,
  "gz": 12.5
}
```

## 配置说明

### Vite 配置

开发服务器端口：3000
API 代理：`/api` -> `http://localhost:8080`
WebSocket 代理：`/ws` -> `ws://localhost:8080`

### 修改配置

编辑 `vite.config.js` 修改代理和端口配置。

## 开发说明

### 添加新页面

1. 在 `src/views/` 创建新的 Vue 组件
2. 在 `src/router/index.js` 添加路由配置
3. 在 `src/components/Sidebar.vue` 添加菜单项

### 添加新组件

1. 在 `src/components/` 创建新的 Vue 组件
2. 在需要的页面中导入并使用

### 状态管理

使用 Pinia 管理全局状态，主要 store：
- `motionStore` - 运动数据、连接状态、设备信息

## 常见问题

### Q: 无法连接到 WebSocket
A: 确保后端服务正在运行，并检查 WebSocket 地址是否正确。

### Q: 波形图不更新
A: 检查设备是否已连接，以及是否正在发送数据。

### Q: 3D 场景不显示
A: 确保浏览器支持 WebGL，尝试更新显卡驱动。

## 浏览器兼容性

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## 许可证

MIT License
