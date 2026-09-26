/**
 * ESP32-S3 原始数据采集固件
 *
 * 功能：
 *   1. 连接 WiFi（SSID: 8202）
 *   2. 读取 MPU6500 六轴传感器原始数据
 *   3. 以 100Hz 采样率采集数据
 *   4. 通过 WebSocket 实时发送到服务器
 *
 * 引脚连接：
 *   ESP32-S3          MPU6500
 *   GPIO21 (SDA)  --> SDA
 *   GPIO22 (SCL)  --> SCL
 *   3.3V          --> VCC
 *   GND           --> GND
 *   GPIO2         --> LED（板载指示灯）
 */

#include <Arduino.h>
#include <WiFi.h>
#include <Wire.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>

// =============================================================================
// 配置参数
// =============================================================================

// WiFi配置
#define WIFI_SSID           "8202"
#define WIFI_PASSWORD       "88888888"
#define WIFI_TIMEOUT_MS     10000

// WebSocket服务器配置
#define WS_SERVER_HOST      "192.168.125.19"  // ← 修改为你的电脑IP
#define WS_SERVER_PORT      8000
#define WS_DEVICE_PATH      "/ws/motion/"
#define DEVICE_ID           "ESP32_001"      // 设备ID

// 采样配置
#define SAMPLE_RATE_HZ      100              // 采样率
#define SAMPLE_INTERVAL_MS  (1000 / SAMPLE_RATE_HZ)
#define BATCH_SIZE          10               // 每批发送的样本数
#define BUFFER_SIZE         1000             // 缓冲区大小

// 引脚定义
#define I2C_SDA_PIN         21               // I2C SDA
#define I2C_SCL_PIN         22               // I2C SCL
#define LED_PIN             2                // LED引脚

// 调试配置
#define SERIAL_BAUD         115200
#define DEBUG_ENABLE        true

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
// MPU6500 寄存器地址
// =============================================================================

#define MPU6500_WHO_AM_I        0x75
#define MPU6500_PWR_MGMT_1      0x6B
#define MPU6500_PWR_MGMT_2      0x6C
#define MPU6500_SMPLRT_DIV      0x19
#define MPU6500_CONFIG          0x1A
#define MPU6500_GYRO_CONFIG     0x1B
#define MPU6500_ACCEL_CONFIG    0x1C
#define MPU6500_ACCEL_XOUT_H    0x3B
#define MPU6500_TEMP_OUT_H      0x41
#define MPU6500_GYRO_XOUT_H     0x43
#define MPU6500_I2C_ADDR        0x68

// =============================================================================
// 数据结构
// =============================================================================

typedef struct {
    uint32_t timestamp;
    float ax, ay, az;   // 加速度（g）
    float gx, gy, gz;   // 角速度（°/s）
} SensorData_t;

// =============================================================================
// 全局变量
// =============================================================================

WebSocketsClient wsClient;
bool wsConnected = false;

SensorData_t dataBuffer[BUFFER_SIZE];
volatile int bufferHead = 0;
volatile int bufferTail = 0;
volatile int bufferCount = 0;

// =============================================================================
// MPU6500 驱动
// =============================================================================

class MPU6500_Driver {
public:
    bool begin() {
        Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN, 400000);
        delay(100);

        // 检查传感器
        uint8_t id = readReg(MPU6500_WHO_AM_I);
        if (id != 0x70) {
            DEBUG_PRINTF("MPU6500 未找到，ID: 0x%02X\n", id);
            return false;
        }
        DEBUG_PRINTF("MPU6500 已连接，ID: 0x%02X\n", id);

        // 复位
        writeReg(MPU6500_PWR_MGMT_1, 0x80);
        delay(100);

        // 唤醒，选择PLL作为时钟源
        writeReg(MPU6500_PWR_MGMT_1, 0x01);
        delay(50);

        // 使能所有轴
        writeReg(MPU6500_PWR_MGMT_2, 0x00);
        delay(10);

        // 配置DLPF
        writeReg(MPU6500_CONFIG, 0x03);
        delay(10);

        // 配置采样率 (1000 / (1 + 9) = 100Hz)
        writeReg(MPU6500_SMPLRT_DIV, 9);
        delay(10);

        // 加速度计 ±16g
        writeReg(MPU6500_ACCEL_CONFIG, 0x18);
        delay(10);

        // 陀螺仪 ±2000°/s
        writeReg(MPU6500_GYRO_CONFIG, 0x18);
        delay(10);

        DEBUG_PRINTLN("MPU6500 初始化完成");
        return true;
    }

    bool readData(float &ax, float &ay, float &az, float &gx, float &gy, float &gz) {
        uint8_t buf[14];

        Wire.beginTransmission(MPU6500_I2C_ADDR);
        Wire.write(MPU6500_ACCEL_XOUT_H);
        Wire.endTransmission(false);
        Wire.requestFrom(MPU6500_I2C_ADDR, (uint8_t)14, (uint8_t)true);

        if (Wire.available() != 14) return false;

        for (int i = 0; i < 14; i++) buf[i] = Wire.read();

        // 解析加速度（±16g，灵敏度 2048 LSB/g）
        int16_t raw_ax = (buf[0] << 8) | buf[1];
        int16_t raw_ay = (buf[2] << 8) | buf[3];
        int16_t raw_az = (buf[4] << 8) | buf[5];
        ax = raw_ax / 2048.0f;
        ay = raw_ay / 2048.0f;
        az = raw_az / 2048.0f;

        // 解析陀螺仪（±2000°/s，灵敏度 16.4 LSB/(°/s)）
        int16_t raw_gx = (buf[8] << 8) | buf[9];
        int16_t raw_gy = (buf[10] << 8) | buf[11];
        int16_t raw_gz = (buf[12] << 8) | buf[13];
        gx = raw_gx / 16.4f;
        gy = raw_gy / 16.4f;
        gz = raw_gz / 16.4f;

        return true;
    }

private:
    void writeReg(uint8_t reg, uint8_t val) {
        Wire.beginTransmission(MPU6500_I2C_ADDR);
        Wire.write(reg);
        Wire.write(val);
        Wire.endTransmission();
    }

    uint8_t readReg(uint8_t reg) {
        Wire.beginTransmission(MPU6500_I2C_ADDR);
        Wire.write(reg);
        Wire.endTransmission(false);
        Wire.requestFrom(MPU6500_I2C_ADDR, (uint8_t)1, (uint8_t)true);
        return Wire.read();
    }
};

MPU6500_Driver mpu;

// =============================================================================
// 环形缓冲区
// =============================================================================

void bufferPush(SensorData_t data) {
    if (bufferCount < BUFFER_SIZE) {
        dataBuffer[bufferHead] = data;
        bufferHead = (bufferHead + 1) % BUFFER_SIZE;
        bufferCount++;
    }
}

bool bufferPop(SensorData_t &data) {
    if (bufferCount > 0) {
        data = dataBuffer[bufferTail];
        bufferTail = (bufferTail + 1) % BUFFER_SIZE;
        bufferCount--;
        return true;
    }
    return false;
}

int bufferAvailable() {
    return bufferCount;
}

// =============================================================================
// WebSocket 事件处理
// =============================================================================

void webSocketEvent(WStype_t type, uint8_t *payload, size_t length) {
    switch (type) {
        case WStype_CONNECTED:
            wsConnected = true;
            DEBUG_PRINTLN("[WS] 已连接");
            break;

        case WStype_DISCONNECTED:
            wsConnected = false;
            DEBUG_PRINTLN("[WS] 已断开");
            break;

        case WStype_TEXT:
            // 服务器响应
            DEBUG_PRINTF("[WS] 收到: %s\n", (char*)payload);
            break;

        default:
            break;
    }
}

// =============================================================================
// 连接WiFi
// =============================================================================

bool connectWiFi() {
    DEBUG_PRINTF("连接WiFi: %s\n", WIFI_SSID);

    WiFi.disconnect(true);
    delay(100);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED) {
        digitalWrite(LED_PIN, !digitalRead(LED_PIN));
        delay(50);
        if (millis() - start > WIFI_TIMEOUT_MS) {
            DEBUG_PRINTLN("WiFi连接超时");
            return false;
        }
    }

    DEBUG_PRINTF("WiFi已连接，IP: %s\n", WiFi.localIP().toString().c_str());
    return true;
}

// =============================================================================
// 连接WebSocket
// =============================================================================

void connectWebSocket() {
    String path = String(WS_DEVICE_PATH) + DEVICE_ID;
    DEBUG_PRINTF("连接WebSocket: ws://%s:%d%s\n", WS_SERVER_HOST, WS_SERVER_PORT, path.c_str());

    wsClient.begin(WS_SERVER_HOST, WS_SERVER_PORT, path.c_str());
    wsClient.onEvent(webSocketEvent);
    wsClient.setReconnectInterval(5000);
}

// =============================================================================
// 发送数据批次
// =============================================================================

bool sendBatch(SensorData_t *data, int count) {
    if (!wsConnected || count == 0) return false;

    JsonDocument doc;
    JsonArray batch = doc.to<JsonArray>();

    for (int i = 0; i < count; i++) {
        JsonObject sample = batch.add<JsonObject>();
        sample["device_id"] = DEVICE_ID;
        sample["timestamp"] = data[i].timestamp;
        sample["ax"] = roundf(data[i].ax * 10000) / 10000;
        sample["ay"] = roundf(data[i].ay * 10000) / 10000;
        sample["az"] = roundf(data[i].az * 10000) / 10000;
        sample["gx"] = roundf(data[i].gx * 10000) / 10000;
        sample["gy"] = roundf(data[i].gy * 10000) / 10000;
        sample["gz"] = roundf(data[i].gz * 10000) / 10000;
    }

    String json;
    serializeJson(doc, json);

    return wsClient.sendTXT(json);
}

// =============================================================================
// 初始化
// =============================================================================

void setup() {
    Serial.begin(SERIAL_BAUD);
    delay(500);

    DEBUG_PRINTLN("========================================");
    DEBUG_PRINTLN("  ESP32-S3 原始数据采集固件");
    DEBUG_PRINTLN("  版本: 2.0.0");
    DEBUG_PRINTLN("========================================");

    // LED
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    // MPU6500
    if (!mpu.begin()) {
        DEBUG_PRINTLN("错误：MPU6500 初始化失败");
        while (true) {
            digitalWrite(LED_PIN, !digitalRead(LED_PIN));
            delay(100);
        }
    }

    // WiFi
    if (!connectWiFi()) {
        DEBUG_PRINTLN("WiFi连接失败，将重试...");
    }

    // WebSocket
    connectWebSocket();

    DEBUG_PRINTLN("");
    DEBUG_PRINTLN("系统初始化完成，开始采集数据");
    DEBUG_PRINTF("采样率: %d Hz\n", SAMPLE_RATE_HZ);
    DEBUG_PRINTF("每批发送: %d 个样本\n", BATCH_SIZE);
}

// =============================================================================
// 主循环
// =============================================================================

void loop() {
    static unsigned long lastSampleTime = 0;
    static unsigned long lastSendTime = 0;
    static SensorData_t batchBuffer[BATCH_SIZE];
    static int batchCount = 0;
    static uint32_t totalSamples = 0;
    static uint32_t totalSent = 0;
    static unsigned long lastDebugPrint = 0;

    unsigned long now = millis();

    // WiFi重连
    if (WiFi.status() != WL_CONNECTED) {
        static unsigned long lastRetry = 0;
        if (now - lastRetry > 5000) {
            lastRetry = now;
            DEBUG_PRINTLN("WiFi断开，重连...");
            WiFi.disconnect();
            WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        }
        digitalWrite(LED_PIN, (now / 500) % 2);
        wsClient.loop();
        return;
    }

    // WebSocket循环
    wsClient.loop();

    // LED状态
    if (wsConnected) {
        digitalWrite(LED_PIN, HIGH);
    } else {
        digitalWrite(LED_PIN, (now / 300) % 2);
    }

    // 采集数据（100Hz）
    if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
        lastSampleTime = now;

        SensorData_t data;
        data.timestamp = now;

        if (mpu.readData(data.ax, data.ay, data.az, data.gx, data.gy, data.gz)) {
            bufferPush(data);

            batchBuffer[batchCount] = data;
            batchCount++;
            totalSamples++;
        }
    }

    // 发送批次（每10个样本）
    if (batchCount >= BATCH_SIZE) {
        if (wsConnected) {
            if (sendBatch(batchBuffer, batchCount)) {
                totalSent += batchCount;
            }
        }
        batchCount = 0;
    }

    // 调试输出（每5秒）
    if (now - lastDebugPrint >= 5000) {
        lastDebugPrint = now;

        DEBUG_PRINTLN("────────────────────────────");
        DEBUG_PRINTF("运行: %lu 秒\n", now / 1000);
        DEBUG_PRINTF("采集: %lu 个样本\n", totalSamples);
        DEBUG_PRINTF("发送: %lu 个样本\n", totalSent);
        DEBUG_PRINTF("缓冲: %d 个样本\n", bufferAvailable());
        DEBUG_PRINTF("WiFi: %s\n", WiFi.isConnected() ? "已连接" : "未连接");
        DEBUG_PRINTF("WebSocket: %s\n", wsConnected ? "已连接" : "未连接");
        DEBUG_PRINTLN("────────────────────────────");
    }

    delayMicroseconds(100);
}
