# ESP32_Raw_Data（Arduino IDE 版固件）

纯数据采集固件，通过 WebSocket 向本项目后端上报 MPU6500 六轴数据。

> 本目录是 **Arduino IDE** 单文件版本，便于不安装 PlatformIO 时快速烧录。
> 功能与推荐的 `esp32_firmware/`（PlatformIO 版）一致，配置项各自独立维护。

## 引脚连接（ESP32-S3）

```
ESP32-S3              MPU6500
GPIO21 (SDA)    ───►  SDA
GPIO22 (SCL)    ───►  SCL
3.3V            ───►  VCC
GND             ───►  GND
GPIO2           ───►  板载 LED
```

## 使用

1. 用 Arduino IDE 打开 `ESP32_Raw_Data.ino`
2. 开发板选择 **ESP32S3 Dev Module**，安装库：`WebSockets`（links2004）、`ArduinoJson`
3. 修改文件顶部配置：

```cpp
#define WIFI_SSID       "8202"           // 仅支持 2.4GHz
#define WIFI_PASSWORD   "88888888"
#define WS_SERVER_HOST  "192.168.1.100"  // ← 改为运行后端的电脑局域网 IP
#define WS_SERVER_PORT  8000
#define DEVICE_ID       "ESP32_001"
```

4. 编译并上传，串口监视器波特率 115200

上报地址为 `ws://<WS_SERVER_HOST>:8000/ws/motion/<DEVICE_ID>`，
每 10 个样本打包为一个 JSON 数组发送，格式与后端 `app/routers/websocket.py` 一致。

## 完整配置项与故障排除

见 [`../../esp32_firmware/CONFIGURATION.md`](../../esp32_firmware/CONFIGURATION.md)，
以及项目根目录的 [`README.md`](../../README.md)。