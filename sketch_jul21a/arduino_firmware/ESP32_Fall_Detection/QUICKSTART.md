# Arduino IDE 快速开始指南

## 第一步：安装Arduino IDE

1. 下载 Arduino IDE 2.x：
   https://www.arduino.cc/en/software

2. 安装并打开Arduino IDE

## 第二步：安装ESP32开发板支持

1. 打开 Arduino IDE
2. 文件 → 首选项
3. 在"附加开发板管理器网址"中添加：
   ```
   https://dl.espressif.com/dl/package_esp32_index.json
   ```
4. 工具 → 开发板 → 开发板管理器
5. 搜索 "esp32"
6. 安装 "esp32 by Espressif Systems"（最新版本）
7. 等待安装完成

## 第三步：安装所需库

1. 工具 → 管理库
2. 搜索并安装：
   - **WebSocketsClient** (by Markus Sattler) - 版本 2.6.1+
   - **ArduinoJson** (by Benoit Blanchon) - 版本 7.2.0+

3. 等待安装完成

## 第四步：打开项目

1. 文件 → 打开
2. 导航到：`arduino_firmware/ESP32_Fall_Detection/`
3. 选择 `ESP32_Fall_Detection.ino`
4. 点击"打开"

## 第五步：修改配置

在 `ESP32_Fall_Detection.ino` 文件中找到并修改：

```cpp
// WebSocket服务器（修改为你的电脑IP）
#define WS_SERVER_HOST      "192.168.1.100"  // ← 修改这里
```

**获取电脑IP地址**：

### Windows
1. 按 Win+R，输入 `cmd`，回车
2. 输入 `ipconfig`
3. 找到 "IPv4 地址"，通常是 192.168.x.x

### Mac
1. 打开终端
2. 输入 `ifconfig | grep "inet "`
3. 找到类似 192.168.x.x 的地址

### Linux
1. 打开终端
2. 输入 `ip addr show | grep "inet "`
3. 找到类似 192.168.x.x 的地址

## 第六步：配置开发板

1. 工具 → 开发板 → esp32 → ESP32S3 Dev Module
2. 工具 → 端口 → 选择你的ESP32端口
3. 配置选项：
   - Flash Size: 8MB (或根据你的开发板)
   - PSRAM: Enabled (如果有)
   - Upload Speed: 921600
   - CPU Frequency: 240MHz

**如果看不到端口**：
- 检查USB线是否支持数据传输
- 安装CP2102或CH340驱动

## 第七步：编译和上传

1. 点击"验证"按钮（✓）检查代码
2. 等待编译完成
3. 点击"上传"按钮（→）
4. 等待上传完成

**如果上传失败**：
- 按住ESP32的BOOT按钮
- 点击上传
- 看到"Connecting..."后松开BOOT按钮

## 第八步：查看串口输出

1. 工具 → 串口监视器
2. 设置波特率为 115200
3. 查看输出信息

**成功输出示例**：
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
```

## 第九步：查看数据

1. 启动后端服务：
   ```bash
   # Windows
   双击 setup_and_start.bat

   # Linux/Mac
   chmod +x setup_and_start.sh
   ./setup_and_start.sh
   ```

2. 打开浏览器：http://localhost:3000

3. 查看实时波形：http://localhost:3000/waveform

## 硬件连接

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

## LED 状态说明

| LED状态 | 含义 |
|---------|------|
| 快闪（500ms） | WiFi 连接中 |
| 快闪（300ms） | WebSocket 连接中 |
| 常亮 | 正常工作 |
| 极快闪（100ms） | 数据发送中 |
| 熄灭 | 未初始化 |

## 常见问题

### Q: 看不到串口端口？

**A**:
1. 检查USB线是否支持数据传输（不是充电线）
2. 安装USB转串口驱动：
   - CP2102: https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers
   - CH340: https://sparks.gogo.co.nz/ch340.html
3. 重启Arduino IDE
4. 尝试其他USB端口

### Q: 编译错误 "WebSocketsClient.h: No such file"?

**A**:
1. 安装WebSockets库：工具 → 管理库 → 搜索"WebSockets" → 安装
2. 安装ArduinoJson库：工具 → 管理库 → 搜索"ArduinoJson" → 安装
3. 重启Arduino IDE

### Q: 上传失败 "Failed to connect to ESP32"?

**A**:
1. 按住ESP32的BOOT按钮
2. 点击上传按钮
3. 看到"Connecting..."后松开BOOT按钮
4. 尝试降低上传速度：工具 → Upload Speed → 115200

### Q: MPU6500初始化失败？

**A**:
1. 检查I2C接线是否正确
2. 确认MPU6500供电为3.3V
3. 尝试交换SDA和SCL
4. 添加上拉电阻（4.7kΩ到3.3V）

### Q: WiFi连接失败？

**A**:
1. 确认WiFi名称和密码正确
2. 确认WiFi为2.4GHz（ESP32不支持5GHz）
3. 检查信号强度
4. 尝试重启路由器

### Q: WebSocket连接失败？

**A**:
1. 确认后端服务已启动
2. 检查IP地址是否正确
3. 确认防火墙允许8000端口
4. 确认ESP32和电脑在同一网络

### Q: 数据显示为0？

**A**:
1. 检查MPU6500接线
2. 确认传感器正常工作
3. 查看串口输出是否有错误
4. 尝试重启ESP32

## 调试技巧

### 1. 启用详细日志

在代码中找到：
```cpp
#define DEBUG_ENABLE  true
```

确保为 `true`，这样会输出详细调试信息。

### 2. 查看内存使用

串口输出会显示：
```
│ 可用内存:      285432 字节
```

如果内存持续减少，可能存在内存泄漏。

### 3. 监控发送状态

串口输出会显示：
```
│ 发送成功/失败: 100 / 0
```

如果失败次数持续增加，检查网络连接。

### 4. 使用I2C扫描器

如果不确定MPU6500的I2C地址，可以使用I2C扫描器：

```cpp
#include <Wire.h>

void setup() {
    Serial.begin(115200);
    Wire.begin(21, 22);

    Serial.println("I2C扫描器");
    Serial.println("正在扫描...");

    for (byte addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0) {
            Serial.print("发现设备: 0x");
            Serial.println(addr, HEX);
        }
    }

    Serial.println("扫描完成");
}

void loop() {}
```

上传后查看串口输出，应该看到：
```
发现设备: 0x68
```

## 进阶配置

### 修改采样率

```cpp
#define SAMPLE_RATE_HZ  100  // 修改为 50, 200, 500 等
```

### 修改量程

```cpp
// 加速度计
#define MPU6500_ACCEL_RANGE  16  // ±2, ±4, ±8, ±16 g

// 陀螺仪
#define MPU6500_GYRO_RANGE   2000  // ±250, ±500, ±1000, ±2000 °/s
```

### 修改LED引脚

```cpp
#define LED_PIN  2  // 修改为你的LED引脚
```

常见开发板LED引脚：
- ESP32-DevKitC: GPIO2
- ESP32-S3-DevKitC-1: GPIO48
- NodeMCU-32S: GPIO2

### 修改I2C引脚

```cpp
#define I2C_SDA_PIN  21  // 修改为你的SDA引脚
#define I2C_SCL_PIN  22  // 修改为你的SCL引脚
```

## 完整测试流程

### 1. 准备工作

```bash
# 启动后端服务
./setup_and_start.sh
```

### 2. 烧录固件

1. 在Arduino IDE中打开项目
2. 修改WS_SERVER_HOST为你的电脑IP
3. 编译并上传

### 3. 查看串口

1. 打开串口监视器（115200波特率）
2. 确认显示"系统初始化完成"
3. 确认显示"发送成功"

### 4. 查看前端

1. 打开 http://localhost:3000
2. 查看波形页面
3. 应该看到实时数据曲线

### 5. 测试断线重连

1. 关闭WiFi路由器
2. 串口显示"连接丢失"
3. 重新打开路由器
4. 串口显示"连接成功"

## 性能优化

### 降低功耗

```cpp
#define SAMPLE_RATE_HZ  50   // 降低采样率
#define BATCH_SIZE      20   // 增加批次大小
```

### 提高稳定性

```cpp
#define RING_BUFFER_SIZE  2000  // 增大缓冲区
```

### 减少网络流量

```cpp
#define SAMPLE_RATE_HZ  50  // 从100Hz降到50Hz
```

## 多设备支持

为每个ESP32设置不同的设备ID：

```cpp
// 设备1
#define DEVICE_ID  "ESP32_001"

// 设备2
#define DEVICE_ID  "ESP32_002"

// 设备3
#define DEVICE_ID  "ESP32_003"
```

## 数据验证

### 检查数据格式

ESP32发送的JSON格式：
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

### 验证数据正确性

- **静止时**：az ≈ 9.81g，其他轴接近0
- **水平放置**：ax ≈ 0, ay ≈ 0, az ≈ 9.81
- **倾斜时**：az会减小，其他轴会增加

## 故障排除清单

- [ ] WiFi名称和密码正确
- [ ] WiFi为2.4GHz
- [ ] 服务器IP地址正确
- [ ] 后端服务已启动
- [ ] 防火墙允许8000端口
- [ ] MPU6500接线正确
- [ ] MPU6500供电为3.3V
- [ ] I2C地址为0x68
- [ ] WebSockets库已安装
- [ ] ArduinoJson库已安装
- [ ] ESP32开发板已选择
- [ ] 串口端口已选择

## 获取帮助

### 查看文档

- `README.md` - 完整说明
- `docs/user-guide.md` - 用户手册
- `backend/API_DOCUMENTATION.md` - API文档

### 查看日志

```bash
# 后端日志
docker-compose logs -f backend

# 前端日志
docker-compose logs -f frontend
```

### 测试API

```bash
# 健康检查
curl http://localhost:8000/health

# 获取统计
curl http://localhost:8000/api/stats
```

## 下一步

1. ✅ 完成固件烧录
2. ✅ 查看串口输出
3. ✅ 确认数据传输
4. ✅ 查看前端波形
5. ✅ 进行摔倒测试
6. ✅ 标注数据
7. ✅ 导出CSV
8. ✅ 训练模型

**祝你使用愉快！** 🚀
