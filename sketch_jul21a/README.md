# 摔倒检测数据采集系统

基于ESP32-S3 + MPU6500的摔倒检测数据采集系统，用于收集人体运动姿态数据以便后续训练机器学习模型。

## 系统架构

```
┌─────────────┐       WebSocket       ┌──────────────────┐
│  ESP32-S3   │ ─────────────────────► │   FastAPI Server │
│  + MPU6500  │   JSON 6-axis data    │  (Port 8000)     │
└─────────────┘                        └──────────────────┘
                                            │           │
                                            ▼           ▼
                                      ┌─────────┐ ┌─────────┐
                                      │PostgreSQL│ │  Vue3   │
                                      │   DB     │ │ Frontend│
                                      └─────────┘ └─────────┘
```

## 功能特性

### ESP32固件
- ✅ WiFi自动连接（SSID: 8202）
- ✅ MPU6500 6轴数据采集（100Hz）
- ✅ WebSocket实时数据传输
- ✅ 断线自动重连
- ✅ LED状态指示

### 后端服务
- ✅ WebSocket数据接收
- ✅ PostgreSQL数据存储
- ✅ RESTful API接口
- ✅ 摔倒事件自动检测
- ✅ 数据标注功能
- ✅ CSV数据导出

### 前端界面
- ✅ 实时波形显示（6条曲线）
- ✅ 3D姿态可视化（Three.js）
- ✅ 数据标注界面
- ✅ 仪表板统计
- ✅ 数据导出功能

## 快速开始

### 前置要求
- Docker 和 Docker Compose
- Arduino IDE 或 PlatformIO（用于ESP32固件）
- Node.js 20+（可选，用于本地开发前端）
- Python 3.11+（可选，用于本地开发后端）

### 1. 启动后端服务

```bash
# 克隆项目
git clone <repository-url>
cd fall-detection-system

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f backend
```

服务启动后：
- 后端API：http://localhost:8000
- 前端界面：http://localhost:3000
- PostgreSQL：localhost:5432

### 2. 编译和烧录ESP32固件

#### 使用PlatformIO（推荐）

```bash
cd esp32_firmware

# 安装PlatformIO CLI（如果没有）
pip install platformio

# 编译固件
pio run

# 烧录到ESP32
pio run -t upload

# 查看串口输出
pio device monitor
```

#### 使用Arduino IDE

1. 打开 `esp32_firmware/src/main.cpp`
2. 安装所需库：
   - WebSocketsClient
   - ArduinoJson
   - WiFi（内置）
   - Wire（内置）
3. 选择开发板：ESP32S3 Dev Module
4. 编译并上传

### 3. 配置ESP32

编辑 `esp32_firmware/include/config.h`：

```cpp
// WiFi配置
#define WIFI_SSID "8202"
#define WIFI_PASSWORD "88888888"

// WebSocket服务器地址（修改为你的电脑IP）
#define WS_SERVER "192.168.1.100"
#define WS_PORT 8000
#define WS_PATH "/ws/motion/ESP32_001"
```

### 4. 使用系统

1. **启动服务**：`docker-compose up -d`
2. **烧录固件**：将ESP32固件烧录到开发板
3. **连接设备**：ESP32会自动连接WiFi和WebSocket服务器
4. **查看数据**：打开 http://localhost:3000 查看实时波形
5. **标注数据**：在标注页面标记摔倒事件
6. **导出数据**：在导出页面下载CSV文件用于模型训练

## 项目结构

```
fall-detection-system/
├── esp32_firmware/           # ESP32固件
│   ├── include/
│   │   ├── config.h         # 配置文件
│   │   ├── mpu6500.h        # MPU6500驱动头文件
│   │   └── websocket_client.h
│   ├── src/
│   │   ├── main.cpp         # 主程序
│   │   ├── mpu6500.cpp      # MPU6500驱动实现
│   │   └── websocket_client.cpp
│   └── platformio.ini       # PlatformIO配置
├── backend/                  # FastAPI后端
│   ├── app/
│   │   ├── main.py          # 应用入口
│   │   ├── models.py        # 数据库模型
│   │   ├── database.py      # 数据库连接
│   │   ├── config.py        # 配置管理
│   │   ├── routers/
│   │   │   ├── api.py       # REST API路由
│   │   │   └── websocket.py # WebSocket路由
│   │   └── services/
│   │       ├── fall_detection.py
│   │       └── data_service.py
│   ├── alembic/             # 数据库迁移
│   ├── requirements.txt
│   └── Dockerfile
├── frontend/                 # Vue3前端
│   ├── src/
│   │   ├── views/           # 页面组件
│   │   ├── components/      # 公共组件
│   │   ├── stores/          # Pinia状态管理
│   │   ├── services/        # API和WebSocket服务
│   │   ├── router/          # 路由配置
│   │   ├── main.js
│   │   └── App.vue
│   ├── package.json
│   ├── vite.config.js
│   ├── nginx.conf
│   └── Dockerfile
├── database/
│   └── init.sql             # 数据库初始化脚本
├── docker-compose.yml
└── README.md
```

## API文档

启动后端服务后，访问：
- Swagger UI：http://localhost:8000/docs
- ReDoc：http://localhost:8000/redoc

### 主要API端点

#### WebSocket
- `ws://localhost:8000/ws/motion/{device_id}` - 实时数据传输

#### REST API
- `GET /api/data` - 获取数据列表
- `GET /api/data/{id}` - 获取单条数据
- `POST /api/data/annotate` - 标注数据
- `GET /api/fall-events` - 获取摔倒事件
- `GET /api/stats` - 获取统计信息
- `GET /api/export` - 导出CSV数据

## 数据格式

### ESP32发送的JSON数据
```json
{
  "device_id": "ESP32_001",
  "timestamp": 1234567890,
  "ax": 0.12,
  "ay": -0.05,
  "az": 9.81,
  "gx": 1.23,
  "gy": -0.45,
  "gz": 0.67
}
```

### 数据库字段
- `id`: 自增主键
- `device_id`: 设备标识
- `timestamp`: ESP32时间戳（毫秒）
- `ax, ay, az`: 三轴加速度（g）
- `gx, gy, gz`: 三轴角速度（°/s）
- `is_fall`: 是否为摔倒数据
- `fall_type`: 摔倒类型
- `confidence`: 置信度
- `notes`: 备注

## 摔倒检测算法

系统使用基于阈值的摔倒检测算法：

1. **特征提取**
   - 加速度幅值：`sqrt(ax² + ay² + az²)`
   - 角速度幅值：`sqrt(gx² + gy² + gz²)`

2. **检测条件**
   - 加速度幅值 > 2.5g
   - 角速度幅值 > 300°/s
   - 时间窗口：500ms（50个样本@100Hz）

3. **检测流程**
   - 滑动窗口分析
   - 检测加速度突变
   - 检测角速度峰值
   - 判断是否为摔倒事件

## 模型训练

### 数据导出

1. 访问前端 http://localhost:3000/export
2. 选择时间范围和摔倒类型
3. 点击"导出CSV"
4. 下载包含标注数据的CSV文件

### 数据格式（CSV）
```csv
timestamp,ax,ay,az,gx,gy,gz,is_fall,fall_type
1234567890,0.12,-0.05,9.81,1.23,-0.45,0.67,true,forward
```

### 训练建议
- 使用LSTM或GRU处理时间序列数据
- 特征工程：加速度幅值、角速度幅值、姿态角
- 数据增强：添加噪声、时间偏移
- 交叉验证：按设备或时间段划分

## 故障排除

### ESP32无法连接WiFi
- 检查WiFi名称和密码是否正确
- 确认ESP32在WiFi信号范围内
- 查看串口日志获取错误信息

### WebSocket连接失败
- 确认后端服务正在运行
- 检查防火墙设置
- 确认ESP32和电脑在同一网络

### 数据库连接失败
- 检查PostgreSQL服务状态
- 确认数据库凭据正确
- 查看后端日志获取详细错误

### 前端无法显示数据
- 检查浏览器控制台错误
- 确认WebSocket连接正常
- 清除浏览器缓存重试

## 性能优化

### 数据库优化
- 定期清理旧数据（保留30天）
- 添加合适的索引
- 使用连接池

### 实时传输优化
- 批量发送数据（每10个样本）
- 数据压缩
- 采样率可配置

### 前端优化
- 数据采样显示（避免渲染过多点）
- 使用Web Workers处理数据
- 启用gzip压缩

## 扩展功能

### 多设备支持
系统已支持多设备，只需：
1. 为每个ESP32设置不同的`device_id`
2. 前端可按设备筛选数据

### 摔倒检测算法优化
- 添加机器学习模型
- 多传感器融合
- 个性化阈值调整

### 实时报警
- 添加邮件/短信通知
- 集成钉钉/企业微信
- 移动端推送

## 开发指南

### 本地开发（不使用Docker）

#### 后端
```bash
cd backend
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

#### 前端
```bash
cd frontend
npm install
npm run dev
```

### 数据库迁移
```bash
cd backend
alembic revision --autogenerate -m "description"
alembic upgrade head
```

## 贡献指南

1. Fork 项目
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'Add some feature'`
4. 推送到分支：`git push origin feature/your-feature`
5. 提交Pull Request

## 许可证

MIT License

## 联系方式

如有问题或建议，请提交Issue或联系开发者。
