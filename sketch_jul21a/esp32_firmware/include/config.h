/**
 * config.h - ESP32-S3 + MPU6500 摔倒检测数据采集固件配置文件
 *
 * 集中管理所有可配置参数。
 * 上报协议：WebSocket（JSON 批量数组），对应后端 /ws/motion/{DEVICE_ID}
 */

#ifndef CONFIG_H
#define CONFIG_H

// =============================================================================
// WiFi 网络配置
// =============================================================================
#define WIFI_SSID               "8202"             // WiFi 名称（仅支持 2.4GHz）
#define WIFI_PASSWORD           "88888888"         // WiFi 密码
#define WIFI_CONNECT_TIMEOUT_MS  15000             // WiFi 连接超时时间（毫秒）
#define WIFI_RETRY_DELAY_MS      5000              // WiFi 重试间隔（毫秒）

// =============================================================================
// WebSocket 服务器配置
// =============================================================================
// WS_SERVER_HOST 填运行后端的电脑局域网 IP，不能用 localhost
#define WS_SERVER_HOST          "192.168.1.100"
#define WS_SERVER_PORT          8000               // 后端端口（docker-compose 映射 8000）
#define WS_PATH_PREFIX          "/ws/motion/"      // 数据上报路径前缀，其后拼接 DEVICE_ID
#define WS_RECONNECT_INTERVAL   5000               // 断线重连间隔（毫秒）

// =============================================================================
// 设备标识
// =============================================================================
#define DEVICE_ID               "ESP32_001"        // 设备唯一标识，与前端订阅的设备一致

// =============================================================================
// MPU6500 传感器配置
// =============================================================================
#define MPU6500_I2C_ADDR         0x68           // MPU6500 I2C 地址（AD0 接地时为 0x68）
#define MPU6500_I2C_ADDR_ALT     0x69           // MPU6500 备用地址（AD0 接 VCC 时为 0x69）

// I2C 引脚配置（ESP32-S3-DevKitC-1 默认 I2C 引脚）
#define I2C_SDA_PIN              1              // I2C 数据线引脚（GPIO1）
#define I2C_SCL_PIN              2              // I2C 时钟线引脚（GPIO2）
#define I2C_CLOCK_SPEED          400000         // I2C 时钟频率（400kHz 快速模式）

// 加速度计量程配置
#define MPU6500_ACCEL_RANGE      16             // 加速度计量程：±2/±4/±8/±16 g
#define MPU6500_ACCEL_SCALE      2048.0         // 加速度计灵敏度（LSB/g），±16g 时为 2048

// 陀螺仪量程配置
#define MPU6500_GYRO_RANGE       2000           // 陀螺仪量程：±250/±500/±1000/±2000 °/s
#define MPU6500_GYRO_SCALE       16.4           // 陀螺仪灵敏度（LSB/°/s），±2000°/s 时为 16.4

// 数字低通滤波器
#define MPU6500_DLPF_CFG         3              // DLPF 配置（0-6，3 对应约 44Hz 带宽）

// =============================================================================
// 数据采集配置
// =============================================================================
#define SAMPLE_RATE_HZ           100            // 采样频率（Hz）
#define SAMPLE_INTERVAL_MS       (1000 / SAMPLE_RATE_HZ)  // 采样间隔（毫秒）
#define BATCH_SIZE               10             // 每批发送的样本数（10 个 = 100ms）
#define SEND_INTERVAL_MS         (SAMPLE_INTERVAL_MS * BATCH_SIZE)  // 发送间隔（毫秒）

// =============================================================================
// 环形缓冲区配置
// =============================================================================
// 网络断开时数据暂存在此，重连后按顺序补发；满则覆盖最旧数据
#define RING_BUFFER_SIZE         1000           // 环形缓冲区容量（样本数，约 10 秒）

// =============================================================================
// LED 状态指示配置
// =============================================================================
// ESP32-S3 板载 LED 引脚（根据实际开发板调整）
#define LED_PIN                  48             // 板载 LED 引脚编号
#define LED_ON                   HIGH           // LED 亮电平
#define LED_OFF                  LOW            // LED 灭电平

// LED 闪烁间隔配置（毫秒）
#define LED_BLINK_WIFI_CONNECT   500            // WiFi 连接中：500ms 间隔闪烁
#define LED_BLINK_WS_CONNECT     300            // WebSocket 连接中：300ms 快闪
#define LED_BLINK_DATA_SEND      100            // 数据发送中：100ms 极快闪

// =============================================================================
// 调试配置
// =============================================================================
#define DEBUG_SERIAL_BAUD        115200         // 串口调试波特率
#define DEBUG_ENABLE             true           // 是否启用调试输出
#define DEBUG_PRINT_INTERVAL_MS  5000           // 调试信息打印间隔（毫秒）

// 调试宏定义
#if DEBUG_ENABLE
    #define DEBUG_PRINT(x)       Serial.print(x)
    #define DEBUG_PRINTLN(x)     Serial.println(x)
    #define DEBUG_PRINTF(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
    #define DEBUG_PRINT(x)
    #define DEBUG_PRINTLN(x)
    #define DEBUG_PRINTF(fmt, ...)
#endif

#endif // CONFIG_H