# 原始数据采集系统 - 简化版

## 系统概述

本系统用于采集ESP32-S3 + MPU6500的原始传感器数据，不包含摔倒检测功能。

### 功能

✅ **ESP32固件**
- 100Hz采样率
- 采集6轴原始数据（加速度 + 角速度）
- 通过WiFi + WebSocket实时发送

✅ **后端服务**
- 接收ESP32数据
- 存储到PostgreSQL数据库
- REST API查询
- CSV导出

❌ **已去除**
- 摔倒检测算法
- 数据标注功能
- 事件统计

---

## 引脚连接

### ESP32-S3 与 MPU6500

```
ESP32-S3              MPU6500
────────────────────────────────
GPIO21 (SDA)    ───►  SDA
GPIO22 (SCL)    ───►  SCL
3.3V            ───►  VCC
GND             ───►  GND

GPIO2           ───►  LED（板载指示灯）
```

### 引脚列表

| 引脚 | 功能 | 连接目标 |
|------|------|----------|
| GPIO21 | I2C SDA | MPU6500 SDA |
| GPIO22 | I2C SCL | MPU6500 SCL |
| GPIO2 | LED | 板载LED |
| 3.3V | 电源 | MPU6500 VCC |
| GND | 地线 | MPU6500 GND |

---

## 快速开始

### 1. 启动后端服务

```bash
# 使用Docker
docker-compose up -d postgres backend

# 或者本地启动
cd backend
pip install -r requirements.txt
uvicorn app.main_simple:app --reload --port 8000
```

### 2. 编译ESP32固件

在Arduino IDE中打开：
```
arduino_firmware/ESP32_Raw_Data/ESP32_Raw_Data.ino
```

修改配置：
```cpp
#define WS_SERVER_HOST  "你的电脑IP"  // ← 修改这里
#define DEVICE_ID       "ESP32_001"
```

编译并上传

### 3. 查看数据

```bash
# 启动前端（可选）
cd frontend
npm install
npm run dev

# 访问
# 前端：http://localhost:3000
# API文档：http://localhost:8000/docs
# 统计信息：http://localhost:8000/api/stats
```

---

## 数据格式

### ESP32发送的数据

```json
[
  {
    "device_id": "ESP32_001",
    "timestamp": 1704067200000,
    "ax": 0.1234,
    "ay": -0.0567,
    "az": 9.8123,
    "gx": 1.2345,
    "gy": -0.4567,
    "gz": 0.6789
  }
]
```

### 字段说明

| 字段 | 类型 | 单位 | 说明 |
|------|------|------|------|
| device_id | string | - | 设备标识符 |
| timestamp | int | ms | 时间戳（毫秒） |
| ax | float | g | X轴加速度 |
| ay | float | g | Y轴加速度 |
| az | float | g | Z轴加速度 |
| gx | float | °/s | X轴角速度 |
| gy | float | °/s | Y轴角速度 |
| gz | float | °/s | Z轴角速度 |

### 验证数据正确性

**静止时**（水平放置）：
- ax ≈ 0
- ay ≈ 0
- az ≈ 9.81
- gx, gy, gz ≈ 0

**倾斜时**：
- az 减小
- ax 或 ay 增加

---

## API接口

### 获取统计信息

```bash
GET /api/stats
```

响应：
```json
{
  "total_records": 100000,
  "device_count": 1,
  "first_record": "2024-01-01T00:00:00",
  "last_record": "2024-01-01T12:00:00"
}
```

### 获取数据列表

```bash
GET /api/data?page=1&page_size=100&device_id=ESP32_001
```

### 获取单条数据

```bash
GET /api/data/{id}
```

### 导出CSV

```bash
GET /api/export?device_id=ESP32_001&start_time=1704067200000&end_time=1704153600000
```

---

## 配置修改

### ESP32配置

编辑 `ESP32_Raw_Data.ino`：

```cpp
// WiFi
#define WIFI_SSID       "8202"
#define WIFI_PASSWORD   "88888888"

// 服务器（修改为你的电脑IP）
#define WS_SERVER_HOST  "192.168.1.100"
#define DEVICE_ID       "ESP32_001"

// 采样率
#define SAMPLE_RATE_HZ  100
#define BATCH_SIZE      10
```

### 后端配置

编辑 `backend/app/main_simple.py`：

```python
DATABASE_URL = "postgresql+asyncpg://fall_user:fall_password@localhost:5432/fall_detection"
```

---

## 常见问题

### Q: MPU6500初始化失败？

**检查**：
- I2C接线是否正确
- MPU6500供电是否为3.3V
- I2C地址是否为0x68

### Q: WiFi连接失败？

**检查**：
- WiFi名称和密码
- WiFi是否为2.4GHz
- 信号强度

### Q: WebSocket连接失败？

**检查**：
- 服务器IP地址
- 后端服务是否运行
- 防火墙是否允许8000端口

### Q: 数据显示为0？

**检查**：
- MPU6500接线
- 传感器是否正常工作
- 查看串口输出

---

## 故障排除

### 串口调试

1. 打开Arduino IDE串口监视器
2. 设置波特率为115200
3. 查看调试信息

### 后端日志

```bash
docker-compose logs -f backend
```

### 数据库查询

```bash
docker-compose exec postgres psql -U fall_user -d fall_detection

# 查看统计
SELECT * FROM data_stats;

# 查看最近数据
SELECT * FROM motion_data ORDER BY id DESC LIMIT 10;

# 清理旧数据
SELECT cleanup_old_data(30);
```

---

## 数据导出和使用

### 导出CSV

```bash
# 方法1：使用API
curl "http://localhost:8000/api/export?device_id=ESP32_001" -o data.csv

# 方法2：使用数据库
docker-compose exec postgres psql -U fall_user -d fall_detection -c \
  "\COPY (SELECT * FROM motion_data ORDER BY id) TO 'data.csv' CSV HEADER"
```

### 数据分析示例

```python
import pandas as pd

# 加载数据
df = pd.read_csv('data.csv')

# 计算加速度幅值
df['accel_magnitude'] = np.sqrt(df['ax']**2 + df['ay']**2 + df['az']**2)

# 计算角速度幅值
df['gyro_magnitude'] = np.sqrt(df['gx']**2 + df['gy']**2 + df['gz']**2)

# 统计信息
print(df.describe())

# 绘图
df[['ax', 'ay', 'az']].plot()
```

---

## 下一步

1. ✅ 启动后端服务
2. ✅ 编译ESP32固件
3. ✅ 采集原始数据
4. ✅ 查看数据统计
5. ✅ 导出CSV数据
6. ✅ 数据分析和可视化

---

## 技术栈

- **硬件**：ESP32-S3 + MPU6500
- **后端**：Python 3.11 + FastAPI + PostgreSQL
- **通信**：WiFi + WebSocket
- **数据格式**：JSON

---

**系统已简化为纯数据采集模式** ✅
