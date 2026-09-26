# ESP32-S3 摔倒检测固件 - Arduino IDE 版本

## 快速开始

### 1. 安装所需库

在Arduino IDE中安装以下库（工具 → 管理库）：

1. **WebSocketsClient**
   - 作者：Markus Sattler
   - 版本：2.6.1+
   - 搜索：`WebSockets`

2. **ArduinoJson**
   - 作者：Benoit Blanchon
   - 版本：7.2.0+
   - 搜索：`ArduinoJson`

### 2. 配置开发板

1. 打开 Arduino IDE
2. 工具 → 开发板 → esp32 → ESP32S3 Dev Module
3. 配置选项：
   - Flash Size: 8MB
   - PSRAM: Enabled (如果开发板有PSRAM)
   - Upload Speed: 921600
   - CPU Frequency: 240MHz

### 3. 修改配置

打开 `ESP32_Fall_Detection.ino`，修改以下配置：

```cpp
// WiFi配置
#define WIFI_SSID           "8202"           // 你的WiFi名称
#define WIFI_PASSWORD       "88888888"       // 你的WiFi密码

// WebSocket服务器（修改为你的电脑IP）
#define WS_SERVER_HOST      "192.168.1.100"  // ← 修改这里
#define WS_SERVER_PORT      8000
#define DEVICE_ID           "ESP32_001"      // 设备ID
```

**获取电脑IP地址**：
- Windows: 打开cmd，输入 `ipconfig`，找到 IPv4 地址
- Linux/Mac: 打开终端，输入 `ifconfig` 或 `ip addr`

### 4. 编译和上传

1. 连接ESP32-S3到电脑
2. 工具 → 端口 → 选择对应的COM口
3. 点击"上传"按钮（→）
4. 等待编译和上传完成

### 5. 查看串口输出

1. 工具 → 串口监视器
2. 设置波特率为 115200
3. 查看调试信息

## 硬件连接

### ESP32-S3 与 MPU6500 接线

```
ESP32-S3          MPU6500
─────────────────────────
GPIO21 (SDA)  →  SDA
GPIO22 (SCL)  →  SCL
3.3V          →  VCC
GND           →  GND
```

**注意**：
- MPU6500 必须使用 3.3V 供电
- 如果使用GY-521模块，已内置上拉电阻
- AD0 引脚接地时 I2C 地址为 0x68

### LED 指示灯

默认使用 GPIO2（大多数ESP32开发板的内置LED）。

如果你的开发板LED引脚不同，修改：
```cpp
#define LED_PIN  2  // 改为你的LED引脚
```

常见开发板LED引脚：
- ESP32-DevKitC: GPIO2
- ESP32-S3-DevKitC-1: GPIO48
- NodeMCU-32S: GPIO2
- WEMOS LOLIN32: GPIO2

## LED 状态说明

| LED状态 | 含义 |
|---------|------|
| 快闪（500ms） | WiFi 连接中 |
| 快闪（300ms） | WebSocket 连接中 |
| 常亮 | 正常工作 |
| 极快闪（100ms） | 数据发送中 |
| 熄灭 | 未初始化 |

## 串口输出示例

```
============================================
  ESP32-S3 摔倒检测数据采集固件
  版本: 1.0.0
  设备ID: ESP32_001
============================================

[INIT] LED 引脚初始化完成
[INIT] 正在初始化 MPU6500 传感器...
[MPU6500] 传感器已连接，WHO_AM_I = 0x70
[MPU6500] 传感器初始化成功！
[WiFi] 正在连接到: 8202
.....
[WiFi] 连接成功！
[WiFi] IP 地址: 192.168.1.105
[WS] 正在连接: ws://192.168.1.100:8000/ws/motion/ESP32_001
[WS] 已连接到服务器

[INIT] ===== 系统初始化完成，开始数据采集 =====
[INIT] 采样率: 100 Hz
[INIT] 批次大小: 10 个样本
[INIT] 发送间隔: 100 ms
[INIT] 缓冲区容量: 1000 个样本

┌──────────────── 系统状态 ────────────────┐
│ 运行时间:      10 秒
│ WiFi 状态:     已连接 (RSSI: -45 dBm)
│ WebSocket:     已连接
│ 发送成功/失败: 100 / 0
│ 缓冲区占用:    0 / 1000
│ 实际采样率:    100 Hz
│ 可用内存:      285432 字节
└──────────────────────────────────────────┘
```

## 故障排除

### 问题1：MPU6500 初始化失败

**症状**：
```
[MPU6500] 错误：传感器未找到！
[INIT] 错误：MPU6500 初始化失败！
```

**解决方案**：
1. 检查 I2C 接线是否正确
2. 确认 MPU6500 供电为 3.3V
3. 检查 SDA/SCL 是否接反
4. 尝试添加上拉电阻（4.7kΩ到3.3V）

### 问题2：WiFi 连接失败

**症状**：
```
[WiFi] 连接超时！
```

**解决方案**：
1. 检查 WiFi 名称和密码是否正确
2. 确认 WiFi 为 2.4GHz（ESP32不支持5GHz）
3. 检查信号强度
4. 尝试重启路由器

### 问题3：WebSocket 连接失败

**症状**：
```
[WS] 连接错误！
[WS] 连接已断开！
```

**解决方案**：
1. 确认后端服务已启动
2. 检查服务器IP地址是否正确
3. 确认防火墙允许8000端口
4. 确认ESP32和电脑在同一网络

### 问题4：编译错误

**症状**：
```
fatal error: WebSocketsClient.h: No such file or directory
```

**解决方案**：
1. 安装 WebSockets 库
2. 安装 ArduinoJson 库
3. 重启 Arduino IDE

### 问题5：上传失败

**症状**：
```
A fatal error occurred: Failed to connect to ESP32
```

**解决方案**：
1. 检查 USB 线是否支持数据传输
2. 按住 BOOT 按钮再上传
3. 尝试降低上传速度
4. 安装 CP2102/CH340 驱动

## 高级配置

### 修改采样率

```cpp
#define SAMPLE_RATE_HZ  100  // 修改为 50, 200, 500 等
```

**注意**：采样率越高，数据量越大，网络带宽需求越高。

### 修改量程

```cpp
// 加速度计量程
#define MPU6500_ACCEL_RANGE  16   // ±2, ±4, ±8, ±16 g

// 陀螺仪量程
#define MPU6500_GYRO_RANGE   2000 // ±250, ±500, ±1000, ±2000 °/s
```

### 修改 I2C 引脚

```cpp
#define I2C_SDA_PIN  21  // 修改为你的SDA引脚
#define I2C_SCL_PIN  22  // 修改为你的SCL引脚
```

### 修改 LED 引脚

```cpp
#define LED_PIN  2  // 修改为你的LED引脚
```

### 修改设备ID

```cpp
#define DEVICE_ID  "ESP32_001"  // 修改为唯一标识符
```

## 数据格式

ESP32 发送的 JSON 数据格式：

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
  },
  // ... 共10个样本
]
```

## 性能优化

### 降低功耗

如果需要降低功耗，可以：

1. 降低采样率
   ```cpp
   #define SAMPLE_RATE_HZ  50  // 从100Hz降到50Hz
   ```

2. 增加批次大小
   ```cpp
   #define BATCH_SIZE  20  // 从10增加到20
   ```

### 提高稳定性

如果数据丢失严重，可以：

1. 增大缓冲区
   ```cpp
   #define RING_BUFFER_SIZE  2000  // 从1000增加到2000
   ```

2. 降低采样率
   ```cpp
   #define SAMPLE_RATE_HZ  50
   ```

## 扩展功能

### 添加电池电压监测

```cpp
// 在 loop() 中添加
float batteryVoltage = analogRead(35) * 2.0 * 3.3 / 4096.0;
DEBUG_PRINTF("电池电压: %.2fV\n", batteryVoltage);
```

### 添加按钮控制

```cpp
#define BUTTON_PIN  0  // BOOT按钮

void setup() {
    pinMode(BUTTON_PIN, INPUT_PULLUP);
}

void loop() {
    if (digitalRead(BUTTON_PIN) == LOW) {
        // 按钮按下时的操作
        delay(200);  // 去抖动
    }
}
```

## 测试流程

### 1. 单元测试

```bash
# 启动后端
./setup_and_start.sh

# 烧录ESP32固件
# 在Arduino IDE中上传

# 查看串口输出
# 确认初始化成功

# 查看后端日志
docker-compose logs -f backend
```

### 2. 数据传输测试

1. 启动系统
2. 烧录ESP32固件
3. 打开串口监视器
4. 确认显示"发送成功"
5. 打开前端 http://localhost:3000/waveform
6. 查看是否有数据曲线

### 3. 断线重连测试

1. 关闭WiFi路由器
2. 观察串口输出"连接丢失"
3. 重新打开路由器
4. 观察自动重连成功

## 常见问题

**Q: 为什么数据显示为0？**
A: 检查MPU6500接线，确认传感器正常工作。

**Q: 数据发送失败怎么办？**
A: 检查WiFi连接、服务器IP地址、后端服务是否运行。

**Q: 如何修改采样率？**
A: 修改 `SAMPLE_RATE_HZ` 参数，注意网络带宽限制。

**Q: 如何支持多个设备？**
A: 为每个设备设置不同的 `DEVICE_ID`。

**Q: 数据格式可以修改吗？**
A: 可以修改 `sendBatchData()` 函数中的JSON结构。

## 技术支持

- 项目文档：`docs/user-guide.md`
- 配置说明：`esp32_firmware/CONFIGURATION.md`
- API文档：`backend/API_DOCUMENTATION.md`
- 串口调试：开启 `DEBUG_ENABLE` 查看详细日志

## 更新日志

### v1.0.0 (2024-01)
- 初始版本
- 支持MPU6500 6轴数据采集
- 100Hz采样率
- WebSocket实时传输
- 环形缓冲区
- LED状态指示
- 断线自动重连
