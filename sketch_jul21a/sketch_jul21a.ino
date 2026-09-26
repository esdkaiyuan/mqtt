/**
 * ESP32-S3 摔倒检测数据采集固件 - Arduino IDE 入口文件
 *
 * 本文件为 Arduino IDE 编译入口，所有代码在下方包含的头文件中。
 * 主要代码位于 esp32_firmware/src/ 目录下（PlatformIO 项目）。
 *
 * 通信协议：MQTT 3.1.1 (Mosquitto / EMQX)
 * 硬件：ESP32-S3 + MPU6500 (I2C)
 *
 * 所需库（Arduino IDE）：
 *   - PubSubClient (by Nick O'Leary)
 *   - ArduinoJson (by Benoit Blanchon)
 *   - MPU6500 (内置驱动)
 *
 * 版本：1.0.0 (MQTT 版本)
 */

// 包含所有模块
#include "mpu6500_driver.h"
#include "mqtt_manager.h"
#include "ring_buffer.h"

// =============================================================================
// 配置参数（根据实际情况修改）
// =============================================================================

// WiFi 网络配置
#define WIFI_SSID               "1302"             // WiFi 名称
#define WIFI_PASSWORD           "17305675843"      // WiFi 密码
#define WIFI_CONNECT_TIMEOUT_MS  15000             // WiFi 连接超时时间（毫秒）
#define WIFI_RETRY_DELAY_MS      5000              // WiFi 重试间隔（毫秒）

// MQTT Broker 配置
#define MQTT_BROKER_HOST        "192.168.1.100"    // MQTT Broker 地址（改为你电脑的IP）
#define MQTT_BROKER_PORT        1883               // MQTT Broker 端口
#define MQTT_USERNAME           "admin"            // MQTT 用户名
#define MQTT_PASSWORD           "public"           // MQTT 密码
#define MQTT_CLIENT_ID          "ESP32_Fall_001"   // MQTT 客户端ID（需与设备标识一致）
#define MQTT_KEEPALIVE          60                 // MQTT 心跳间隔（秒）
#define MQTT_RECONNECT_INTERVAL 5000               // MQTT 重连间隔（毫秒）

// 设备标识（需与平台创建设备时的 deviceKey 一致）
#define DEVICE_ID               "ESP32_Fall_001"   // 设备唯一标识符

// MQTT Topic 配置
#define MQTT_TOPIC_DATA         "device/ESP32_Fall_001/data"
#define MQTT_TOPIC_HEARTBEAT    "device/ESP32_Fall_001/heartbeat"
#define MQTT_TOPIC_LWT          "device/ESP32_Fall_001/lwt"
#define MQTT_TOPIC_CMD          "device/ESP32_Fall_001/cmd"

// MPU6500 传感器配置
#define MPU6500_I2C_ADDR        0x68               // I2C 地址
#define I2C_SDA_PIN             21                 // I2C SDA 引脚
#define I2C_SCL_PIN             22                 // I2C SCL 引脚
#define I2C_CLOCK_SPEED         400000             // I2C 时钟频率（400kHz）

// 数据采集配置
#define SAMPLE_RATE_HZ          100                // 采样频率（Hz）
#define SAMPLE_INTERVAL_MS      (1000 / SAMPLE_RATE_HZ)
#define BATCH_SIZE              10                 // 每批发送样本数
#define SEND_INTERVAL_MS        (SAMPLE_INTERVAL_MS * BATCH_SIZE)
#define RING_BUFFER_SIZE        1000               // 环形缓冲区容量

// LED 配置
#define LED_PIN                 2                  // LED 引脚
#define LED_ON                  HIGH
#define LED_OFF                 LOW

// 调试配置
#define DEBUG_SERIAL_BAUD       115200
#define DEBUG_ENABLE            true

// 调试宏
#if DEBUG_ENABLE
    #define DEBUG_PRINT(x)       Serial.print(x)
    #define DEBUG_PRINTLN(x)     Serial.println(x)
    #define DEBUG_PRINTF(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
    #define DEBUG_PRINT(x)
    #define DEBUG_PRINTLN(x)
    #define DEBUG_PRINTF(fmt, ...)
#endif

// =============================================================================
// 全局对象
// =============================================================================

MPU6500_Driver mpu;
MQTTManager mqttManager;
RingBuffer ringBuffer;
MPU6500_Data_t batchBuffer[BATCH_SIZE];
size_t batchCount = 0;

// LED 状态
enum LedState { OFF, WIFI_CONNECTING, MQTT_CONNECTING, CONNECTED, DATA_SENDING };
LedState ledState = OFF;
unsigned long lastLedToggle = 0;
bool ledIsOn = false;

// 调试计时
unsigned long lastDebugPrint = 0;
uint32_t sampleCountInPeriod = 0;

// =============================================================================
// LED 状态管理
// =============================================================================

void updateLED() {
    unsigned long now = millis();
    unsigned int interval = 0;

    switch (ledState) {
        case OFF:               digitalWrite(LED_PIN, LED_OFF); return;
        case WIFI_CONNECTING:   interval = 500; break;
        case MQTT_CONNECTING:   interval = 300; break;
        case CONNECTED:         digitalWrite(LED_PIN, LED_ON); return;
        case DATA_SENDING:      interval = 100; break;
    }

    if (interval > 0 && (now - lastLedToggle >= interval)) {
        lastLedToggle = now;
        ledIsOn = !ledIsOn;
        digitalWrite(LED_PIN, ledIsOn ? LED_ON : LED_OFF);
    }
}

void setLedState(LedState state) {
    if (ledState != state) {
        ledState = state;
        lastLedToggle = millis();
        ledIsOn = false;
    }
}

// =============================================================================
// WiFi 连接管理
// =============================================================================

bool connectWiFi() {
    DEBUG_PRINTLN("==========================================");
    DEBUG_PRINTLN("  ESP32-S3 摔倒检测固件 v1.0.0 (MQTT)");
    DEBUG_PRINTF("  设备ID: %s\n", DEVICE_ID);
    DEBUG_PRINTLN("==========================================");
    DEBUG_PRINTF("[WiFi] 正在连接: %s\n", WIFI_SSID);

    setLedState(WIFI_CONNECTING);
    WiFi.disconnect(true);
    delay(100);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED) {
        updateLED();
        delay(10);
        if (millis() - start > WIFI_CONNECT_TIMEOUT_MS) {
            DEBUG_PRINTLN("\n[WiFi] 连接超时！");
            return false;
        }
        if ((millis() - start) % 1000 < 10) DEBUG_PRINT(".");
    }

    DEBUG_PRINTLN("");
    DEBUG_PRINTF("[WiFi] 连接成功！IP: %s\n", WiFi.localIP().toString().c_str());
    DEBUG_PRINTF("[WiFi] 信号强度: %d dBm\n", WiFi.RSSI());
    return true;
}

// =============================================================================
// 调试信息输出
// =============================================================================

void printDebugInfo() {
    unsigned long now = millis();
    if (now - lastDebugPrint >= 5000) {
        lastDebugPrint = now;
        DEBUG_PRINTLN("");
        DEBUG_PRINTLN("┌──────────────── 系统状态 ────────────────┐");
        DEBUG_PRINTF("│ 运行时间:      %lu 秒\n", now / 1000);
        DEBUG_PRINTF("│ WiFi:          %s (%d dBm)\n",
                     WiFi.isConnected() ? "已连接" : "未连接", WiFi.RSSI());
        DEBUG_PRINTF("│ MQTT:          %s\n", mqttManager.getStateString());
        DEBUG_PRINTF("│ 发送:          %lu 成功 / %lu 失败\n",
                     mqttManager.getSendCount(), mqttManager.getFailCount());
        DEBUG_PRINTF("│ 缓冲区:        %d / %d\n", ringBuffer.available(), RING_BUFFER_SIZE);
        DEBUG_PRINTF("│ 采样率:        %d Hz\n", sampleCountInPeriod * 1000 / 5000);
        DEBUG_PRINTF("│ 设备ID:        %s\n", DEVICE_ID);
        DEBUG_PRINTF("│ MQTT Topic:    %s\n", MQTT_TOPIC_DATA);
        DEBUG_PRINTLN("└──────────────────────────────────────────┘");
        sampleCountInPeriod = 0;
    }
}

// =============================================================================
// Arduino setup()
// =============================================================================

void setup() {
    Serial.begin(DEBUG_SERIAL_BAUD);
    delay(1000);

    DEBUG_PRINTLN("");
    DEBUG_PRINTLN("============================================");
    DEBUG_PRINTLN("  ESP32-S3 摔倒检测固件 (MQTT 版本)");
    DEBUG_PRINTF("  设备ID: %s\n", DEVICE_ID);
    DEBUG_PRINTF("  Broker: %s:%d\n", MQTT_BROKER_HOST, MQTT_BROKER_PORT);
    DEBUG_PRINTLN("============================================");
    DEBUG_PRINTLN("");

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LED_OFF);
    DEBUG_PRINTLN("[INIT] LED 初始化完成");

    // MPU6500
    DEBUG_PRINTLN("[INIT] 初始化 MPU6500...");
    if (!mpu.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_CLOCK_SPEED)) {
        DEBUG_PRINTLN("[INIT] 错误：MPU6500 初始化失败！");
        while (true) {
            digitalWrite(LED_PIN, !digitalRead(LED_PIN));
            delay(100);
        }
    }
    DEBUG_PRINTLN("[INIT] MPU6500 初始化成功");

    // WiFi
    if (!connectWiFi()) {
        DEBUG_PRINTLN("[INIT] WiFi 连接失败，后台将持续重试");
    }

    // MQTT
    mqttManager.begin();

    DEBUG_PRINTLN("");
    DEBUG_PRINTLN("[INIT] ===== 系统初始化完成，开始数据采集 =====");
    DEBUG_PRINTF("[INIT] 采样率: %d Hz\n", SAMPLE_RATE_HZ);
    DEBUG_PRINTF("[INIT] 批次大小: %d 个样本\n", BATCH_SIZE);
    DEBUG_PRINTF("[INIT] 发送间隔: %d ms\n", SEND_INTERVAL_MS);
    DEBUG_PRINTLN("");
}

// =============================================================================
// Arduino loop()
// =============================================================================

void loop() {
    static unsigned long lastSampleTime = 0;
    static unsigned long lastSendTime = 0;

    unsigned long now = millis();

    // WiFi 管理
    if (WiFi.status() != WL_CONNECTED) {
        setLedState(WIFI_CONNECTING);
        static unsigned long lastRetry = 0;
        if (now - lastRetry >= WIFI_RETRY_DELAY_MS) {
            lastRetry = now;
            DEBUG_PRINTLN("[WiFi] 断开，正在重连...");
            WiFi.disconnect();
            WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        }
        updateLED();
        mqttManager.update();
        return;
    }

    // MQTT 管理
    mqttManager.update();

    // LED 状态
    if (mqttManager.isConnected()) {
        setLedState((now - lastSendTime < 100) ? DATA_SENDING : CONNECTED);
    } else {
        setLedState(MQTT_CONNECTING);
    }
    updateLED();

    // 传感器采集（100Hz）
    if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
        lastSampleTime = now;

        MPU6500_Data_t data;
        if (mpu.readAll(data)) {
            ringBuffer.push(data);
            batchBuffer[batchCount] = data;
            batchCount++;
            sampleCountInPeriod++;
        }
    }

    // 批量发送（每10个样本）
    if (batchCount >= BATCH_SIZE) {
        lastSendTime = now;
        if (mqttManager.isConnected()) {
            if (mqttManager.sendBatch(batchBuffer, batchCount)) {
                // 发送成功
            } else {
                DEBUG_PRINTLN("[Send] 发送失败");
            }
        } else {
            DEBUG_PRINTF("[Send] MQTT 未连接，暂存缓冲区 (%d 样本)\n", ringBuffer.available());
        }
        batchCount = 0;
    }

    // 恢复发送缓冲区数据
    if (mqttManager.isConnected() && ringBuffer.available() > BATCH_SIZE) {
        MPU6500_Data_t resend[BATCH_SIZE];
        size_t count = ringBuffer.popBatch(resend, BATCH_SIZE);
        if (count > 0) {
            mqttManager.sendBatch(resend, count);
        }
    }

    // 调试信息
    printDebugInfo();

    delayMicroseconds(100);
}
