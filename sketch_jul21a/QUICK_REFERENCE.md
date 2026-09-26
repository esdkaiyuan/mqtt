# 摔倒检测系统 - 快速参考卡

## 🚀 一键启动

### Windows
```bash
双击 setup_and_start.bat
```

### Linux/Mac
```bash
chmod +x setup_and_start.sh
./setup_and_start.sh
```

## 📍 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端界面 | http://localhost:3000 | Vue3主界面 |
| 后端API | http://localhost:8000 | FastAPI服务 |
| API文档 | http://localhost:8000/docs | Swagger UI |
| 数据库 | localhost:5432 | PostgreSQL |

## 🔑 数据库凭据

```
主机: localhost
端口: 5432
数据库: fall_detection
用户: fall_user
密码: fall_password
```

## 📶 ESP32配置

**WiFi**:
- SSID: `8202`
- 密码: `88888888`

**WebSocket服务器**:
- 地址: `你的电脑IP:8000`
- 路径: `/ws/motion/ESP32_001`

**配置文件**: `esp32_firmware/include/config.h`

## 🎯 核心功能

### 1. 实时波形
**地址**: http://localhost:3000/waveform

**显示内容**:
- 红色：X轴加速度 (ax)
- 绿色：Y轴加速度 (ay)
- 蓝色：Z轴加速度 (az)
- 橙色：X轴角速度 (gx)
- 青色：Y轴角速度 (gy)
- 紫色：Z轴角速度 (gz)

### 2. 3D姿态
**地址**: http://localhost:3000/3d-view

**功能**: Three.js 3D立方体实时旋转

### 3. 数据标注
**地址**: http://localhost:3000/annotation

**摔倒类型**:
- Forward (前倒)
- Backward (后倒)
- Left Side (左侧倒)
- Right Side (右侧倒)

### 4. 数据导出
**地址**: http://localhost:3000/export

**格式**: CSV
**筛选**: 时间范围、设备、摔倒类型

### 5. 仪表板
**地址**: http://localhost:3000/dashboard

**显示**: 统计信息、连接状态、事件列表

## 🔌 API端点

### WebSocket
```
ws://localhost:8000/ws/motion/{device_id}
```

### REST API
```bash
# 获取统计信息
GET /api/stats

# 获取数据列表
GET /api/data?page=1&page_size=100

# 获取单条数据
GET /api/data/{id}

# 标注数据
POST /api/data/annotate
Body: {
  "start_id": 1000,
  "end_id": 1200,
  "is_fall": true,
  "fall_type": "forward",
  "notes": "前倒测试"
}

# 获取摔倒事件
GET /api/fall-events

# 导出CSV
GET /api/export?start_time=...&end_time=...

# 健康检查
GET /health
```

## 📊 数据格式

### ESP32发送格式
```json
{
  "device_id": "ESP32_001",
  "timestamp": 1704067200000,
  "ax": 0.12,
  "ay": -0.05,
  "az": 9.81,
  "gx": 1.23,
  "gy": -0.45,
  "gz": 0.67
}
```

### 批量发送格式
```json
[
  {"device_id":"ESP32_001","timestamp":1704067200000,"ax":0.12,...},
  {"device_id":"ESP32_001","timestamp":1704067200010,"ax":0.13,...},
  // ... 共10个样本
]
```

## 🐳 Docker命令

```bash
# 启动所有服务
docker-compose up -d

# 停止所有服务
docker-compose down

# 查看日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f backend

# 查看前端日志
docker-compose logs -f frontend

# 查看数据库日志
docker-compose logs -f postgres

# 重启服务
docker-compose restart

# 重建并启动
docker-compose up -d --build

# 进入后端容器
docker-compose exec backend bash

# 进入数据库
docker-compose exec postgres psql -U fall_user -d fall_detection
```

## 🛠️ 常用SQL

```sql
-- 查看所有表
\dt

-- 查看表结构
\d motion_data
\d fall_events

-- 统计记录数
SELECT COUNT(*) FROM motion_data;

-- 统计摔倒事件
SELECT COUNT(*) FROM fall_events;

-- 查看最近数据
SELECT * FROM motion_data ORDER BY timestamp DESC LIMIT 10;

-- 查看摔倒事件
SELECT * FROM fall_events ORDER BY detected_at DESC LIMIT 10;

-- 按设备统计
SELECT device_id, COUNT(*) as count
FROM motion_data
GROUP BY device_id;

-- 清理30天前的数据
SELECT cleanup_old_data();

-- 查看统计视图
SELECT * FROM data_stats;
SELECT * FROM fall_stats;
```

## 🧪 测试命令

### 生成测试数据
```bash
cd scripts
pip install websockets
python generate_test_data.py --count 1000 --falls 5
```

**参数**:
- `--count`: 数据点数量（默认1000）
- `--falls`: 摔倒事件数量（默认5）
- `--device`: 设备ID（默认ESP32_001）
- `--server`: 服务器地址（默认localhost:8000）

### 测试API
```bash
# 健康检查
curl http://localhost:8000/health

# 获取统计
curl http://localhost:8000/api/stats

# 获取数据
curl "http://localhost:8000/api/data?limit=10"

# 导出CSV
curl http://localhost:8000/api/export -o data.csv
```

### 测试WebSocket
```bash
# 使用websocat（需要安装）
echo '{"device_id":"test","timestamp":12345,"ax":0.1,"ay":0.2,"az":9.8,"gx":1.0,"gy":2.0,"gz":3.0}' | websocat ws://localhost:8000/ws/motion/test
```

## 🔧 ESP32开发

### PlatformIO命令
```bash
cd esp32_firmware

# 编译
pio run

# 烧录
pio run -t upload

# 串口监控
pio device monitor

# 清理
pio run -t clean

# 列出设备
pio device list
```

### Arduino IDE
1. 安装ESP32开发板支持
2. 选择开发板：ESP32S3 Dev Module
3. 安装库：WebSocketsClient, ArduinoJson
4. 打开 `esp32_firmware/src/main.cpp`
5. 编译并上传

## 📁 项目结构

```
fall-detection-system/
├── esp32_firmware/           # ESP32固件
│   ├── include/
│   │   ├── config.h         # 配置文件
│   │   ├── mpu6500.h
│   │   └── websocket_client.h
│   ├── src/
│   │   ├── main.cpp         # 主程序
│   │   └── mpu6500.cpp
│   ├── platformio.ini
│   └── CONFIGURATION.md
│
├── backend/                  # FastAPI后端
│   ├── app/
│   │   ├── main.py          # 应用入口
│   │   ├── config.py
│   │   ├── database.py
│   │   ├── models.py
│   │   ├── routers/
│   │   │   ├── api.py
│   │   │   └── websocket.py
│   │   └── services/
│   │       ├── data_service.py
│   │       └── fall_detection.py
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── .env
│   └── API_DOCUMENTATION.md
│
├── frontend/                 # Vue3前端
│   ├── src/
│   │   ├── views/           # 页面
│   │   ├── components/      # 组件
│   │   ├── stores/          # 状态管理
│   │   └── services/        # 服务
│   ├── package.json
│   ├── vite.config.js
│   ├── Dockerfile
│   └── README.md
│
├── database/
│   └── init.sql             # 数据库初始化
│
├── scripts/
│   ├── backup.sh            # 数据库备份
│   ├── restore.sh           # 数据库恢复
│   └── generate_test_data.py # 测试数据生成
│
├── docs/
│   ├── development.md       # 开发指南
│   └── user-guide.md        # 用户指南
│
├── docker-compose.yml        # Docker配置
├── setup_and_start.sh       # 一键启动（Linux/Mac）
├── setup_and_start.bat      # 一键启动（Windows）
├── verify_project.sh        # 项目验证
├── README.md                # 项目说明
├── QUICKSTART.md            # 快速开始
└── FINAL_REPORT.md          # 项目报告
```

## 📚 文档清单

### 用户文档
1. **README.md** - 项目概述
2. **QUICKSTART.md** - 快速开始
3. **docs/user-guide.md** - 用户手册
4. **DELIVERY_CHECKLIST.md** - 交付清单

### 开发文档
5. **docs/development.md** - 开发指南
6. **backend/API_DOCUMENTATION.md** - API文档
7. **esp32_firmware/CONFIGURATION.md** - ESP32配置
8. **CONFIGURATION_EXAMPLES.md** - 配置示例

### 参考文档
9. **FINAL_REPORT.md** - 项目报告
10. **PROJECT_SUMMARY.md** - 项目总结

## 🔍 故障排除

### ESP32无法连接WiFi
1. 检查WiFi名称和密码
2. 确认WiFi为2.4GHz
3. 查看串口日志
4. 尝试重启ESP32

### WebSocket连接失败
1. 确认后端服务运行
2. 检查防火墙设置
3. 验证IP地址配置
4. 检查端口8000是否被占用

### 前端无法显示数据
1. 检查浏览器控制台
2. 确认WebSocket连接
3. 清除浏览器缓存
4. 重启前端服务

### 数据库连接失败
1. 检查PostgreSQL服务
2. 验证数据库凭据
3. 查看后端日志
4. 重启数据库服务

### 性能问题
1. 降低采样率
2. 清理旧数据
3. 优化数据库索引
4. 增加服务器资源

## 💡 最佳实践

### 数据采集
- 多样化测试场景
- 保持标注一致性
- 定期备份数据
- 记录实验日志

### 系统维护
- 定期清理旧数据
- 备份数据库
- 更新依赖
- 监控系统日志

### 模型训练
- 积累足够数据（1000+摔倒事件）
- 数据预处理
- 特征工程
- 交叉验证

## 🎓 进阶功能

### 实时报警
```python
# 添加邮件/钉钉/短信通知
async def send_alert(fall_event):
    # 实现报警逻辑
    pass
```

### 机器学习集成
```python
# 训练和部署模型
import joblib
model = joblib.load('fall_model.pkl')
prediction = model.predict(features)
```

### 多设备支持
```cpp
// 为每个设备设置唯一ID
#define DEVICE_ID "ESP32_002"
```

## 📞 获取帮助

### 查看日志
```bash
docker-compose logs -f
```

### 验证项目
```bash
./verify_project.sh
```

### 常用链接
- 前端：http://localhost:3000
- API文档：http://localhost:8000/docs
- Swagger：http://localhost:8000/redoc

## ✅ 检查清单

- [ ] Docker已安装
- [ ] 项目结构验证通过
- [ ] 环境配置已设置
- [ ] 服务已启动
- [ ] 前端可以访问
- [ ] API可以访问
- [ ] ESP32已配置
- [ ] ESP32已烧录
- [ ] 数据传输正常
- [ ] 波形显示正常

## 🎉 快速开始

1. **启动系统**: `./setup_and_start.sh`
2. **访问前端**: http://localhost:3000
3. **配置ESP32**: 修改 `config.h`
4. **烧录固件**: `pio run -t upload`
5. **开始采集**: 佩戴设备，开始测试
6. **标注数据**: 在前端标注摔倒事件
7. **导出数据**: 下载CSV用于模型训练

**祝你使用愉快！** 🚀
