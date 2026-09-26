# 项目完整示例配置

## 1. 后端配置 (backend/.env)

```env
# 数据库配置
DATABASE_URL=postgresql+asyncpg://fall_user:fall_password@localhost:5432/fall_detection
DATABASE_URL_SYNC=postgresql://fall_user:fall_password@localhost:5432/fall_detection

# 应用配置
APP_NAME=Fall Detection System
APP_VERSION=1.0.0
DEBUG=true

# CORS配置
CORS_ORIGINS=["http://localhost:3000","http://localhost:5173","http://127.0.0.1:3000"]

# 摔倒检测参数
FALL_WINDOW_SIZE=50          # 检测窗口大小（500ms @ 100Hz）
FALL_ACCEL_THRESHOLD=2.5     # 加速度阈值（g）
FALL_GYRO_THRESHOLD=300.0    # 角速度阈值（°/s）
FALL_SAMPLE_RATE=100         # 采样率（Hz）

# 数据保留天数
DATA_RETENTION_DAYS=30
```

## 2. 前端配置 (frontend/.env)

```env
# API配置
VITE_API_URL=http://localhost:8000
VITE_WS_URL=ws://localhost:8000

# 应用配置
VITE_APP_TITLE=摔倒检测系统
VITE_APP_VERSION=1.0.0
```

## 3. ESP32配置 (esp32_firmware/include/config.h)

```cpp
// WiFi配置
#define WIFI_SSID           "8202"
#define WIFI_PASSWORD       "88888888"

// WebSocket服务器配置（修改为你的电脑IP）
#define WS_SERVER_HOST      "192.168.1.100"  // 修改为实际IP地址
#define WS_SERVER_PORT      8000
#define WS_SERVER_PATH      "/ws/motion/"

// 设备ID
#define DEVICE_ID           "ESP32_001"      // 每个设备唯一

// 采样配置
#define SAMPLE_RATE_HZ      100             // 采样率
#define BATCH_SIZE          10              // 每批样本数
```

## 4. Docker配置 (docker-compose.yml)

已配置完成，包含：
- PostgreSQL数据库（端口5432）
- FastAPI后端（端口8000）
- Vue3前端（端口3000）

## 5. 获取电脑IP地址

### Windows
```bash
ipconfig
```
查找 "IPv4 地址"，通常是 192.168.x.x 格式

### Linux/Mac
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
```

### 验证连接
```bash
# 测试后端是否运行
curl http://localhost:8000/health

# 测试WebSocket
# 使用 websocat 或浏览器开发者工具
```

## 6. 常见配置场景

### 场景1：本地开发（推荐）

```bash
# 启动数据库和后端
docker-compose up -d postgres backend

# 前端本地开发
cd frontend
npm install
npm run dev  # 访问 http://localhost:5173

# ESP32配置
WS_SERVER_HOST = "localhost" 或 "127.0.0.1"
```

### 场景2：完整Docker部署

```bash
# 一键启动所有服务
docker-compose up -d

# 访问
# 前端: http://localhost:3000
# 后端: http://localhost:8000
# API文档: http://localhost:8000/docs
```

### 场景3：局域网多设备访问

```bash
# 后端配置
CORS_ORIGINS=["http://192.168.1.100:3000","http://192.168.1.100:5173"]

# 前端配置
VITE_API_URL=http://192.168.1.100:8000
VITE_WS_URL=ws://192.168.1.100:8000

# ESP32配置
WS_SERVER_HOST = "192.168.1.100"

# 访问
# 使用局域网内任何设备访问 http://192.168.1.100:3000
```

## 7. 验证配置

### 检查后端

```bash
# 1. 检查数据库连接
docker-compose exec postgres psql -U fall_user -d fall_detection -c "\dt"

# 2. 检查后端健康
curl http://localhost:8000/health

# 3. 查看API文档
open http://localhost:8000/docs
```

### 检查前端

```bash
# 1. 检查前端是否运行
curl http://localhost:3000

# 2. 检查WebSocket连接
# 打开浏览器开发者工具 -> Network -> WS
```

### 检查ESP32

```bash
# 1. 查看串口输出
pio device monitor

# 2. 检查WiFi连接
# 应该显示 "WiFi connected" 和 IP 地址

# 3. 检查WebSocket连接
# 应该显示 "WebSocket connected"
```

## 8. 故障排除配置

### 问题：数据库连接失败

```bash
# 检查PostgreSQL是否运行
docker-compose ps postgres

# 检查日志
docker-compose logs postgres

# 验证凭据
docker-compose exec postgres psql -U fall_user -d fall_detection
```

### 问题：CORS错误

```env
# 确保CORS_ORIGINS包含前端地址
CORS_ORIGINS=["http://localhost:3000","http://localhost:5173","http://127.0.0.1:3000","http://127.0.0.1:5173"]
```

### 问题：WebSocket连接失败

```bash
# 1. 检查防火墙
# Windows: 允许8000端口
# Linux: sudo ufw allow 8000

# 2. 检查后端是否接受WebSocket
curl http://localhost:8000/ws/status

# 3. 检查ESP32配置
# 确保WS_SERVER_HOST是正确的IP地址
```

## 9. 生产环境配置建议

### 安全性

```env
# 生产环境禁用调试
DEBUG=false

# 使用强密码
DATABASE_URL=postgresql+asyncpg://secure_user:strong_password@db-host:5432/fall_detection

# 限制CORS来源
CORS_ORIGINS=["https://yourdomain.com"]
```

### 性能

```env
# 数据保留策略
DATA_RETENTION_DAYS=90

# 调整摔倒检测参数（根据实际需求）
FALL_WINDOW_SIZE=50
FALL_ACCEL_THRESHOLD=2.0  # 降低阈值，增加灵敏度
FALL_GYRO_THRESHOLD=250.0
```

### 监控

```bash
# 启用日志
docker-compose logs -f

# 数据库备份
./scripts/backup.sh

# 健康检查
curl http://localhost:8000/health
```

## 10. 快速启动检查清单

- [ ] Docker和Docker Compose已安装
- [ ] 后端.env文件已配置（数据库凭据）
- [ ] 前端.env文件已配置（API地址）
- [ ] ESP32 config.h已配置（WiFi和服务器地址）
- [ ] 获取电脑IP地址并更新配置
- [ ] 启动服务：`docker-compose up -d`
- [ ] 验证后端：访问 http://localhost:8000/docs
- [ ] 验证前端：访问 http://localhost:3000
- [ ] 烧录ESP32固件
- [ ] 测试数据传输

完成以上步骤后，系统即可正常使用！
