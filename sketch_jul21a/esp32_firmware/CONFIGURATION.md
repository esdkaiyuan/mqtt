# ESP32 固件配置说明

固件位置：`esp32_firmware/`（PlatformIO 工程）。所有可调参数集中在 `include/config.h`。

上报协议为 **WebSocket**，数据发往 `ws://<WS_SERVER_HOST>:<WS_SERVER_PORT>/ws/motion/<DEVICE_ID>`，
与后端 `app/routers/websocket.py` 的接入端点对应。

## 快速配置

编辑 `esp32_firmware/include/config.h`：

```cpp
// WiFi（仅支持 2.4GHz）
#define WIFI_SSID           "8202"
#define WIFI_PASSWORD       "88888888"

// 后端地址（填运行后端的电脑局域网 IP，不能用 localhost）
#define WS_SERVER_HOST      "192.168.1.100"
#define WS_SERVER_PORT      8000
#define WS_PATH_PREFIX      "/ws/motion/"

// 设备 ID（需与前端订阅的设备一致）
#define DEVICE_ID           "ESP32_001"

// 采样配置
#define SAMPLE_RATE_HZ      100     // 采样率（Hz）
#define BATCH_SIZE          10      // 每批样本数（10 个 = 100ms）
```

## 获取电脑 IP 地址

```bash
# Windows
ipconfig                 # 查找 "IPv4 地址"

# Linux / macOS
ifconfig | grep "inet " | grep -v 127.0.0.1
```

确认后端在监听：

```bash
curl http://<IP>:8000/health
```

## 编译与烧录

```bash
cd esp32_firmware
pio run                 # 编译
pio run -t upload       # 烧录
pio device monitor      # 串口日志（115200）
```

## MPU6500 配置

### I2C 地址

- 默认 `0x68`（AD0 接地）
- AD0 接 VCC 时为 `0x69`，改 `MPU6500_I2C_ADDR` 即可

### 量程

```cpp
#define MPU6500_ACCEL_RANGE  16     // ±2 / ±4 / ±8 / ±16 g
#define MPU6500_GYRO_RANGE   2000   // ±250 / ±500 / ±1000 / ±2000 °/s
```

推荐：摔倒检测用 ±16g / ±2000°/s（默认），普通运动用 ±8g / ±1000°/s。

### 数字低通滤波器（DLPF）

```cpp
#define MPU6500_DLPF_CFG  3   // 0-6；3 ≈ 44Hz 带宽（推荐）
```

## 引脚配置

默认按 ESP32-S3-DevKitC-1：

```cpp
#define I2C_SDA_PIN  1    // GPIO1
#define I2C_SCL_PIN  2    // GPIO2
#define LED_PIN      48   // 板载 LED
```

常见开发板对照：

| 开发板 | SDA | SCL | LED |
|--------|-----|-----|-----|
| ESP32-S3-DevKitC-1 | GPIO1 | GPIO2 | GPIO48 |
| ESP32-S3-WROOM-1 | GPIO21 | GPIO22 | GPIO2 |
| XIAO ESP32-S3 | GPIO5 | GPIO6 | GPIO21 |

接线：

```
ESP32-S3        MPU6500
GPIO1  (SDA) --> SDA
GPIO2  (SCL) --> SCL
3.3V         --> VCC
GND          --> GND
```

## 采样与缓冲

```cpp
#define SAMPLE_RATE_HZ    100    // 50/100/200/500 Hz
#define BATCH_SIZE        10     // 每批样本数
#define RING_BUFFER_SIZE  1000   // 环形缓冲容量（100Hz 下约 10 秒）
```

- 发送间隔 = `BATCH_SIZE / SAMPLE_RATE_HZ`（默认 10/100 = 100ms）
- 采样率越高，数据量、带宽与存储压力越大
- 环形缓冲区在断线期间暂存数据，重连后按顺序补发；写满时覆盖最旧数据

## LED 状态指示

| 现象 | 含义 |
|------|------|
| 500ms 慢闪 | WiFi 连接中 |
| 300ms 快闪 | WebSocket 连接中 |
| 常亮 | 连接正常 |
| 100ms 极快闪 | 正在发送数据 |

## 调试输出

```cpp
#define DEBUG_ENABLE            true
#define DEBUG_SERIAL_BAUD       115200
#define DEBUG_PRINT_INTERVAL_MS 5000   // 状态打印间隔
```

每 5 秒打印一次 WiFi/WebSocket 状态、发送成功/失败计数、缓冲区占用与实际采样率。
生产环境可将 `DEBUG_ENABLE` 置为 `false` 关闭输出。

## 故障排除

### WiFi 连接失败

1. 检查 SSID / 密码，确认是 2.4GHz 网络（ESP32 不支持 5GHz）
2. 确认信号强度，必要时靠近路由器
3. 查看串口日志中的连接进度与超时信息

### I2C 通信失败

1. 检查 SDA/SCL 接线与供电（3.3V）
2. 确认 I2C 地址（`0x68` 或 `0x69`）
3. 必要时加 4.7kΩ 上拉电阻
4. 降低 I2C 频率：`I2C_CLOCK_SPEED` 改为 `100000`

### WebSocket 连接失败

1. 确认 `WS_SERVER_HOST` 是后端所在电脑的局域网 IP，且与 ESP32 同网段
2. 确认后端已启动：`curl http://<IP>:8000/health`
3. 确认防火墙放行 8000 端口
4. 检查后端日志是否出现该 `DEVICE_ID` 的连接

### 数据异常

1. 确认传感器焊接质量与供电稳定
2. 减少电磁干扰，必要时启用 DLPF
3. 观察串口打印的「实际采样率」是否接近 `SAMPLE_RATE_HZ`

## 参考资源

- [ESP32-S3 技术手册](https://www.espressif.com/sites/default/files/documentation/esp32-s3_datasheet_cn.pdf)
- [MPU6500 寄存器手册](https://invensense.tdk.com/wp-content/uploads/2015/02/MPU-6500-Register-Map-and-Descriptions.pdf)
- [PlatformIO 文档](https://docs.platformio.org/)
- [Arduino ESP32 文档](https://docs.espressif.com/projects/arduino-esp32/)