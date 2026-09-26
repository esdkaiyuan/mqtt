# 快速开始指南

## 1. 安装依赖

```bash
cd frontend
npm install
```

## 2. 启动开发服务器

```bash
npm run dev
```

## 3. 访问应用

打开浏览器访问: http://localhost:3000

## 4. 测试应用

### 方法1: 使用Demo数据生成器（推荐）

1. 打开浏览器开发者工具 (F12)
2. 在Console标签中粘贴 `demo-generator.js` 的内容
3. 运行以下命令：
```javascript
generator.start()
```

这将自动生成模拟的传感器数据，包括：
- 正常运动数据
- 偶尔的摔倒事件
- 设备信息

### 方法2: 连接真实设备

1. 确保ESP32设备已启动
2. 确保后端服务正在运行
3. 在应用中点击"连接设备"
4. 输入WebSocket地址（默认：ws://localhost:8080/ws）

## 主要功能

### 仪表板
- 查看统计数据
- 监控设备连接状态
- 查看最近摔倒事件

### 实时波形
- 6条曲线实时显示
- 可调整时间窗口
- 暂停/继续功能

### 3D姿态
- 立方体实时旋转
- 显示传感器数据
- 支持视角调整

### 数据标注
- 在波形图上选择数据范围
- 标记摔倒类型
- 添加备注和标签

### 数据导出
- CSV格式导出
- 自定义时间范围
- 选择导出列

## 常见问题

**Q: 页面空白？**
A: 检查是否安装了所有依赖，运行 `npm install`

**Q: 波形图不更新？**
A: 使用Demo数据生成器测试，或检查设备连接

**Q: 3D场景不显示？**
A: 确保浏览器支持WebGL，尝试使用Chrome

**Q: 样式异常？**
A: 清除浏览器缓存，重新加载页面

## 开发命令

```bash
# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览生产版本
npm run preview
```

## 项目结构

```
frontend/
├── index.html          # 入口HTML
├── package.json        # 项目配置
├── vite.config.js      # Vite配置
├── demo-generator.js   # Demo数据生成器
├── README.md           # 详细文档
├── QUICK_START.md      # 快速开始
└── src/
    ├── main.js         # 应用入口
    ├── App.vue         # 根组件
    ├── router/         # 路由配置
    ├── stores/         # Pinia状态管理
    ├── services/       # WebSocket和API服务
    ├── views/          # 页面组件
    └── components/     # 通用组件
```
