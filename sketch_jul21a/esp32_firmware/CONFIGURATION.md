# ESP32固件配置说明

## 快速配置

编辑 `esp32_firmware/include/config.h` 文件：

```cpp
// WiFi配置
#define WIFI_SSID "1302"           // 你的WiFi名称（仅2.4GHz）
#define WIFI_PASSWORD "17305675843"   // 你的WiFi密码

// MQTT Broker配置（平台后端地址）
#define MQTT_BROKER_HOST "192.168.1.57"  // MQTT Broker IP（电脑/服务器IP）
#define MQTT_BROKER_PORT 1883             // MQTT端口
#define MQTT_USERNAME "admin"             // MQTT用户名
#define MQTT_PASSWORD "public"            // MQTT密码

// 设备ID（必须与平台上创建设备时的 deviceKey 一致）
#define DEVICE_ID "ESP32_001"

// 采样配置
#define SAMPLE_RATE_HZ 100         // 采样率（Hz）
#define BATCH_SIZE 10              // 每批发送的样本数（建议10）
```

## 获取电脑IP地址

### Windows
```bash
ipconfig
```
查找 "IPv4 地址" 或 "IPv4 Address"

### Linux/Mac
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
```
或
```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

## MPU6500配置

### I2C地址
- 默认：0x68
- AD0引脚接高电平：0x69

### 量程配置

在 `config.h` 中修改：

```cpp
// 加速度计量程
#define MPU6500_ACCEL_RANGE_2G    0x00  // ±2g
#define MPU6500_ACCEL_RANGE_4G    0x08  // ±4g
#define MPU6500_ACCEL_RANGE_8G    0x10  // ±8g
#define MPU6500_ACCEL_RANGE_16G   0x18  // ±16g（默认）

// 陀螺仪量程
#define MPU6500_GYRO_RANGE_250DPS   0x00  // ±250°/s
#define MPU6500_GYRO_RANGE_500DPS   0x08  // ±500°/s
#define MPU6500_GYRO_RANGE_1000DPS  0x10  // ±1000°/s
#define MPU6500_GYRO_RANGE_2000DPS  0x18  // ±2000°/s（默认）
```

**推荐配置**：
- 摔倒检测：±16g / ±2000°/s
- 普通运动：±8g / ±1000°/s
- 高精度：±4g / ±500°/s

### 数字低通滤波器（DLPF）

```cpp
#define MPU6500_DLPF_250HZ  0x00  // 250Hz带宽
#define MPU6500_DLPF_184HZ  0x01  // 184Hz带宽
#define MPU6500_DLPF_92HZ   0x02  // 92Hz带宽
#define MPU6500_DLPF_41HZ   0x03  // 41Hz带宽（推荐）
#define MPU6500_DLPF_20HZ   0x04  // 20Hz带宽
#define MPU6500_DLPF_10HZ   0x05  // 10Hz带宽
#define MPU6500_DLPF_5HZ    0x06  // 5Hz带宽
```

**推荐**：41Hz（平衡噪声和响应速度）

## 引脚配置

### ESP32-S3默认I2C引脚

```cpp
#define I2C_SDA_PIN  1  // SDA引脚（GPIO1）
#define I2C_SCL_PIN  2  // SCL引脚（GPIO2）
```

### LED引脚

```cpp
#define LED_PIN  2  // 内置LED（GPIO2）
```

### 常见ESP32-S3开发板引脚

| 开发板 | SDA | SCL | LED |
|--------|-----|-----|-----|
| ESP32-S3-DevKitC-1 | GPIO1 | GPIO2 | GPIO48 |
| ESP32-S3-WROOM-1 | GPIO21 | GPIO22 | GPIO2 |
| XIAO ESP32-S3 | GPIO5 | GPIO6 | GPIO21 |

根据你的开发板修改引脚配置。

## 采样率配置

```cpp
#define SAMPLE_RATE_HZ  100  // 采样率（Hz）
```

**采样率选择**：
- 50Hz：低功耗，适合长时间采集
- 100Hz：平衡模式（推荐）
- 200Hz：高精度，数据量大
- 500Hz：超高精度，仅在需要时使用

**注意**：采样率越高，数据量越大，网络带宽和存储需求也越大。

## 批量发送配置

```cpp
#define BATCH_SIZE  10  // 每批发送的样本数
```

**计算公式**：
- 发送间隔 = BATCH_SIZE / SAMPLE_RATE_HZ
- 例：10 / 100Hz = 100ms

**推荐**：
- 5-10个样本（50-100ms间隔）
- 平衡实时性和网络效率

## 环形缓冲区配置

```cpp
#define RING_BUFFER_SIZE  1000  // 缓冲区大小
```

**用途**：
- 断线时存储数据
- 重连后重发数据

**大小计算**：
- 100Hz采样率，1000个样本 = 10秒缓冲
- 内存占用：约32KB

**推荐**：
- 500-2000个样本
- 根据可用内存调整

## 数据校准

### 自动校准

在 `main.cpp` 中启用自动校准：

```cpp
// 启动时自动校准
mpu.calibrate();
```

校准过程：
1. 保持设备静止2秒
2. 计算加速度和陀螺仪的偏移量
3. 应用校准补偿

### 手动校准

如果自动校准不准确，可以手动设置偏移量：

```cpp
// 在 config.h 中设置
#define ACCEL_X_OFFSET  0.0
#define ACCEL_Y_OFFSET  0.0
#define ACCEL_Z_OFFSET  0.0
#define GYRO_X_OFFSET   0.0
#define GYRO_Y_OFFSET   0.0
#define GYRO_Z_OFFSET   0.0
```

## 调试配置

### 串口调试

```cpp
#define DEBUG_SERIAL  1  // 启用串口调试
#define DEBUG_BAUD_RATE  115200  // 波特率
```

### 调试输出内容

- WiFi连接状态
- WebSocket连接状态
- 采样数据（可选）
- 发送统计

### 禁用调试

生产环境中禁用调试输出：

```cpp
#define DEBUG_SERIAL  0  // 禁用串口调试
```

## 低功耗配置（可选）

### 睡眠模式

```cpp
#define ENABLE_LIGHT_SLEEP  1  // 启用轻度睡眠
#define SLEEP_DURATION_MS  10  // 睡眠时间（毫秒）
```

### 动态采样率

```cpp
#define ENABLE_ADAPTIVE_RATE  1  // 启用自适应采样率
#define MIN_SAMPLE_RATE_HZ  50   // 最低采样率
#define MAX_SAMPLE_RATE_HZ  100  // 最高采样率
```

## 常见配置场景

### 场景1：高精度摔倒检测

```cpp
#define SAMPLE_RATE_HZ  200
#define BATCH_SIZE  20
#define MPU6500_ACCEL_RANGE  MPU6500_ACCEL_RANGE_16G
#define MPU6500_GYRO_RANGE  MPU6500_GYRO_RANGE_2000DPS
#define MPU6500_DLPF  MPU6500_DLPF_41HZ
```

### 场景2：低功耗长时间采集

```cpp
#define SAMPLE_RATE_HZ  50
#define BATCH_SIZE  5
#define ENABLE_LIGHT_SLEEP  1
#define RING_BUFFER_SIZE  2000
```

### 场景3：实时监控

```cpp
#define SAMPLE_RATE_HZ  100
#define BATCH_SIZE  5  // 更频繁发送
#define DEBUG_SERIAL  1
```

## 故障排除

### WiFi连接失败

1. 检查SSID和密码是否正确
2. 确认WiFi信号强度
3. 尝试重启ESP32
4. 检查WiFi是否为2.4GHz（ESP32不支持5GHz）

### I2C通信失败

1. 检查接线是否正确
2. 确认I2C地址（0x68或0x69）
3. 添加上拉电阻（4.7kΩ）
4. 尝试降低I2C频率：

```cpp
#define I2C_FREQUENCY  100000  // 100kHz（默认400kHz）
```

### MQTT连接失败

1. 确认Broker地址（MQTT_BROKER_HOST）正确
2. 确认Broker正在运行（mosquitto/EMQX）
3. 检查防火墙是否开放1883端口
4. 确认MQTT用户名密码正确
5. 在平台上已创建设备，且deviceKey与DEVICE_ID一致

### 数据异常

1. 检查校准是否正确
2. 确认MPU6500焊接质量
3. 减少电磁干扰
4. 使用数字低通滤波器

## 高级配置

### 自定义JSON格式

修改 `websocket_client.h` 中的JSON格式：

```cpp
// 添加额外字段
doc["temperature"] = mpu.getTemperature();
doc["battery"] = getBatteryLevel();
```

### 多设备支持

为每个设备设置唯一ID：

```cpp
// 设备1
#define DEVICE_ID "ESP32_001"

// 设备2
#define DEVICE_ID "ESP32_002"
```

### 数据加密（可选）

添加数据加密：

```cpp
#include <mbedtls/aes.h>

void encryptData(uint8_t* data, size_t length) {
    // AES加密实现
}
```

## 更新固件

### 无线更新（OTA）

添加OTA支持：

```cpp
#include <ArduinoOTA.h>

void setup() {
    ArduinoOTA.begin();
}

void loop() {
    ArduinoOTA.handle();
    // 其他代码...
}
```

### 串口更新

```bash
# PlatformIO
pio run -t upload

# Arduino IDE
工具 -> 上传
```

## 参考资源

- [ESP32-S3技术手册](https://www.espressif.com/sites/default/files/documentation/esp32-s3_datasheet_cn.pdf)
- [MPU6500寄存器手册](https://invensense.tdk.com/wp-content/uploads/2015/02/MPU-6500-Register-Map-and-Descriptions.pdf)
- [PlatformIO文档](https://docs.platformio.org/)
- [Arduino ESP32文档](https://docs.espressif.com/projects/arduino-esp32/)
