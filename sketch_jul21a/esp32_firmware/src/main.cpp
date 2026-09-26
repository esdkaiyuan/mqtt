/**
 * main.cpp - ESP32-S3 摔倒检测数据采集固件 - 主程序
 *
 * 功能概述：
 *   1. 连接 WiFi 网络
 *   2. 初始化 MPU6500 六轴传感器（I2C 通信）
 *   3. 以 100Hz 采样率持续采集加速度和角速度数据
 *   4. 将数据存储到本地环形缓冲区（1000 个样本）
 *   5. 每 10 个样本打包为 JSON 数组，通过 MQTT 发布到服务器
 *   6. 断线自动重连
 *   7. LED 指示系统状态
 *   8. 遗嘱消息（LWT）通知设备在线/离线状态
 *
 * 硬件连接：
 *   ESP32-S3          MPU6500
 *   GPIO21 (SDA)  --> SDA
 *   GPIO22 (SCL)  --> SCL
 *   3.3V          --> VCC
 *   GND           --> GND
 *
 * 通信协议：MQTT 3.1.1 (Mosquitto / EMQX)
 * 编程环境：PlatformIO + Arduino 框架
 * 作者：摔倒检测项目
 * 版本：1.0.0
 */

#include <Arduino.h>
#include <WiFi.h>
#include "config.h"
#include "mpu6500.h"
#include "mqtt_client.h"

// =============================================================================
// 全局对象实例化
// =============================================================================

/** MPU6500 传感器驱动实例 */
MPU6500 mpu(Wire, MPU6500_I2C_ADDR);

/** MQTT 客户端管理实例 */
MQTTManager mqttManager;

// =============================================================================
// 环形缓冲区（Ring Buffer）
// =============================================================================

/**
 * 环形缓冲区结构
 * 用于临时存储传感器数据，在网络断开时暂存数据
 * 容量：1000 个样本
 */
class RingBuffer {
public:
    RingBuffer() : _head(0), _tail(0), _count(0) {}

    /**
     * 将数据写入缓冲区
     * @param data 要存储的传感器数据
     * @return true=写入成功，false=缓冲区已满
     */
    bool push(const MPU6500_Data_t &data)
    {
        _buffer[_head] = data;
        _head = (_head + 1) % RING_BUFFER_SIZE;

        if (_count < RING_BUFFER_SIZE) {
            _count++;
        } else {
            // 缓冲区满，覆盖最旧数据
            _tail = (_tail + 1) % RING_BUFFER_SIZE;
            DEBUG_PRINTLN("[Buffer] 警告：缓冲区已满，覆盖最旧数据");
        }
        return true;
    }

    /**
     * 从缓冲区读取一个数据
     * @param data 输出数据
     * @return true=读取成功，false=缓冲区为空
     */
    bool pop(MPU6500_Data_t &data)
    {
        if (_count == 0) return false;

        data = _buffer[_tail];
        _tail = (_tail + 1) % RING_BUFFER_SIZE;
        _count--;
        return true;
    }

    /**
     * 批量读取数据
     * @param dataArray 输出数组
     * @param maxCount  最大读取数
     * @return 实际读取的样本数
     */
    size_t popBatch(MPU6500_Data_t *dataArray, size_t maxCount)
    {
        size_t count = 0;
        while (count < maxCount && _count > 0) {
            dataArray[count] = _buffer[_tail];
            _tail = (_tail + 1) % RING_BUFFER_SIZE;
            _count--;
            count++;
        }
        return count;
    }

    /** 获取当前缓冲区中的数据量 */
    size_t available() const { return _count; }

    /** 清空缓冲区 */
    void clear() { _head = 0; _tail = 0; _count = 0; }

private:
    MPU6500_Data_t _buffer[RING_BUFFER_SIZE];  // 数据存储数组
    size_t _head;                               // 写入位置
    size_t _tail;                               // 读取位置
    size_t _count;                              // 当前数据量
};

/** 环形缓冲区实例 */
RingBuffer ringBuffer;

// =============================================================================
// 发送批次缓冲区
// =============================================================================
/** 批量发送临时数组 */
MPU6500_Data_t batchBuffer[BATCH_SIZE];
/** 当前批次中的样本计数 */
size_t batchCount = 0;

// =============================================================================
// LED 状态管理
// =============================================================================

/** LED 状态枚举 */
enum class LedState {
    OFF,                // 熄灭
    WIFI_CONNECTING,    // WiFi 连接中
    MQTT_CONNECTING,    // MQTT 连接中
    CONNECTED,          // 正常工作（常亮）
    DATA_SENDING        // 数据发送中（快闪）
};

/** 当前 LED 状态 */
LedState ledState = LedState::OFF;
/** LED 上次切换时间 */
unsigned long lastLedToggle = 0;
/** LED 当前亮灭状态 */
bool ledIsOn = false;

/**
 * 更新 LED 指示状态
 * 根据系统状态控制 LED 闪烁模式
 */
void updateLED()
{
    unsigned long now = millis();
    unsigned long interval = 0;

    switch (ledState) {
        case LedState::OFF:
            digitalWrite(LED_PIN, LED_OFF);
            return;

        case LedState::WIFI_CONNECTING:
            interval = LED_BLINK_WIFI_CONNECT;
            break;

        case LedState::MQTT_CONNECTING:
            interval = LED_BLINK_MQTT_CONNECT;
            break;

        case LedState::CONNECTED:
            // 正常工作时 LED 常亮
            digitalWrite(LED_PIN, LED_ON);
            return;

        case LedState::DATA_SENDING:
            interval = LED_BLINK_DATA_SEND;
            break;
    }

    // 按间隔闪烁 LED
    if (interval > 0 && (now - lastLedToggle >= interval)) {
        lastLedToggle = now;
        ledIsOn = !ledIsOn;
        digitalWrite(LED_PIN, ledIsOn ? LED_ON : LED_OFF);
    }
}

/**
 * 设置 LED 状态
 * @param state 新的 LED 状态
 */
void setLedState(LedState state)
{
    if (ledState != state) {
        ledState = state;
        lastLedToggle = millis();
        ledIsOn = false;
    }
}

// =============================================================================
// WiFi 连接管理
// =============================================================================

/**
 * 连接 WiFi 网络
 * @return true=连接成功，false=连接失败或超时
 */
bool connectWiFi()
{
    DEBUG_PRINTLN("==========================================");
    DEBUG_PRINTLN("  ESP32-S3 摔倒检测数据采集固件 v1.0.0");
    DEBUG_PRINTLN("  (MQTT 版本 - Mosquitto/EMQX)");
    DEBUG_PRINTLN("==========================================");
    DEBUG_PRINTF("[WiFi] 正在连接到: %s\n", WIFI_SSID);

    setLedState(LedState::WIFI_CONNECTING);

    // 断开之前的连接
    WiFi.disconnect(true);
    delay(100);

    // 设置 WiFi 模式为 Station
    WiFi.mode(WIFI_STA);

    // 启动连接
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    // 等待连接
    unsigned long startTime = millis();
    while (WiFi.status() != WL_CONNECTED) {
        updateLED();
        delay(10);

        // 检查超时
        if (millis() - startTime > WIFI_CONNECT_TIMEOUT_MS) {
            DEBUG_PRINTLN("\n[WiFi] 连接超时！");
            return false;
        }

        // 每秒打印一次进度
        if ((millis() - startTime) % 1000 < 10) {
            DEBUG_PRINT(".");
        }
    }

    // 连接成功
    DEBUG_PRINTLN("");
    DEBUG_PRINTF("[WiFi] 连接成功！\n");
    DEBUG_PRINTF("[WiFi] IP 地址: %s\n", WiFi.localIP().toString().c_str());
    DEBUG_PRINTF("[WiFi] 信号强度: %d dBm\n", WiFi.RSSI());

    return true;
}

// =============================================================================
// 调试信息输出
// =============================================================================

/** 上次调试输出时间 */
unsigned long lastDebugPrint = 0;
/** 上次采样统计时间 */
unsigned long lastSampleStats = 0;
/** 统计周期内的采样计数 */
uint32_t sampleCountInPeriod = 0;

/**
 * 定期打印系统状态信息
 */
void printDebugInfo()
{
    unsigned long now = millis();

    if (now - lastDebugPrint >= DEBUG_PRINT_INTERVAL_MS) {
        lastDebugPrint = now;

        DEBUG_PRINTLN("");
        DEBUG_PRINTLN("┌──────────────── 系统状态 ────────────────┐");
        DEBUG_PRINTF("│ 运行时间:      %lu 秒\n", now / 1000);
        DEBUG_PRINTF("│ WiFi 状态:     %s (RSSI: %d dBm)\n",
                     WiFi.isConnected() ? "已连接" : "未连接", WiFi.RSSI());
        DEBUG_PRINTF("│ MQTT 状态:     %s\n", mqttManager.getStateString());
        DEBUG_PRINTF("│ MQTT 已连接:   %s\n", mqttManager.isConnected() ? "是" : "否");
        DEBUG_PRINTF("│ 发送成功/失败: %lu / %lu\n",
                     mqttManager.getSendCount(), mqttManager.getFailCount());
        DEBUG_PRINTF("│ 缓冲区占用:    %d / %d\n", ringBuffer.available(), RING_BUFFER_SIZE);
        DEBUG_PRINTF("│ 实际采样率:    %d Hz\n", sampleCountInPeriod * 1000 / DEBUG_PRINT_INTERVAL_MS);
        DEBUG_PRINTF("│ 可用内存:      %lu 字节\n", ESP.getFreeHeap());
        DEBUG_PRINTF("│ 设备ID:        %s\n", DEVICE_ID);
        DEBUG_PRINTF("│ MQTT Topic:    %s\n", MQTT_TOPIC_DATA);
        DEBUG_PRINTLN("└──────────────────────────────────────────┘");

        // 重置采样统计
        sampleCountInPeriod = 0;
    }
}

// =============================================================================
// 初始化函数（Arduino setup）
// =============================================================================
void setup()
{
    // 初始化串口调试
    Serial.begin(DEBUG_SERIAL_BAUD);
    delay(1000);  // 等待串口稳定

    DEBUG_PRINTLN("");
    DEBUG_PRINTLN("============================================");
    DEBUG_PRINTLN("  ESP32-S3 摔倒检测数据采集固件");
    DEBUG_PRINTLN("  (MQTT 协议版本)");
    DEBUG_PRINTF("  设备ID: %s\n", DEVICE_ID);
    DEBUG_PRINTF("  MQTT Broker: %s:%d\n", MQTT_BROKER_HOST, MQTT_BROKER_PORT);
    DEBUG_PRINTLN("============================================");
    DEBUG_PRINTLN("");

    // 初始化 LED 引脚
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LED_OFF);
    DEBUG_PRINTLN("[INIT] LED 引脚初始化完成");

    // 初始化 MPU6500 传感器（非阻塞：即使失败也继续运行，可后续通过 MQTT 下发数据）
    DEBUG_PRINTLN("[INIT] 正在初始化 MPU6500 传感器...");
    if (!mpu.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_CLOCK_SPEED)) {
        DEBUG_PRINTLN("[INIT] 警告：MPU6500 初始化失败，将在后台持续重试");
        DEBUG_PRINTLN("[INIT] 系统将继续运行，MQTT 连接不受影响");
    } else {
        DEBUG_PRINTLN("[INIT] MPU6500 初始化成功");
    }

    // 连接 WiFi
    if (!connectWiFi()) {
        DEBUG_PRINTLN("[INIT] WiFi 连接失败，将在后台持续重试");
        // WiFi 连接失败不阻塞，后续自动重试
    }

    // 初始化 MQTT 客户端
    mqttManager.begin();

    DEBUG_PRINTLN("");
    DEBUG_PRINTLN("[INIT] ===== 系统初始化完成，开始数据采集 =====");
    DEBUG_PRINTF("[INIT] 采样率: %d Hz\n", SAMPLE_RATE_HZ);
    DEBUG_PRINTF("[INIT] 批次大小: %d 个样本\n", BATCH_SIZE);
    DEBUG_PRINTF("[INIT] 发送间隔: %d ms\n", SEND_INTERVAL_MS);
    DEBUG_PRINTF("[INIT] 缓冲区容量: %d 个样本\n", RING_BUFFER_SIZE);
    DEBUG_PRINTF("[INIT] MQTT Topic: %s\n", MQTT_TOPIC_DATA);
    DEBUG_PRINTLN("");
}

// =============================================================================
// 主循环函数（Arduino loop）
// =============================================================================
void loop()
{
    static unsigned long lastSampleTime = 0;
    static unsigned long lastSendTime = 0;

    unsigned long now = millis();

    // ─────────────────────────────────────────────
    // 第一部分：WiFi 连接管理
    // ─────────────────────────────────────────────
    if (WiFi.status() != WL_CONNECTED) {
        setLedState(LedState::WIFI_CONNECTING);

        // 每隔一段时间尝试重连 WiFi
        static unsigned long lastWifiRetry = 0;
        if (now - lastWifiRetry >= WIFI_RETRY_DELAY_MS) {
            lastWifiRetry = now;
            DEBUG_PRINTLN("[WiFi] 连接丢失，正在重连...");
            WiFi.disconnect();
            WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        }

        updateLED();
        mqttManager.update();
        return;  // WiFi 未连接时跳过后续处理
    }

    // ─────────────────────────────────────────────
    // 第二部分：MQTT 连接管理
    // ─────────────────────────────────────────────
    mqttManager.update();

    // 更新 LED 状态
    if (mqttManager.isConnected()) {
        // 如果正在发送数据，LED 快闪
        if (now - lastSendTime < 100) {
            setLedState(LedState::DATA_SENDING);
        } else {
            setLedState(LedState::CONNECTED);
        }
    } else {
        setLedState(LedState::MQTT_CONNECTING);
    }

    updateLED();

    // ─────────────────────────────────────────────
    // 第三部分：传感器数据采集（100Hz）
    // ─────────────────────────────────────────────
    if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
        lastSampleTime = now;

        MPU6500_Data_t data;

        // 读取传感器数据
        if (mpu.readAll(data)) {
            // 存入环形缓冲区
            ringBuffer.push(data);

            // 存入发送批次缓冲区
            batchBuffer[batchCount] = data;
            batchCount++;

            // 采样统计
            sampleCountInPeriod++;
        } else {
            DEBUG_PRINTLN("[Sensor] 警告：传感器读取失败");
        }
    }

    // ─────────────────────────────────────────────
    // 第四部分：批量发送数据（每 N 个样本）
    // ─────────────────────────────────────────────
    if (batchCount >= BATCH_SIZE) {
        lastSendTime = now;

        if (mqttManager.isConnected()) {
            // 通过 MQTT 发布批次数据
            bool success = mqttManager.sendBatch(batchBuffer, batchCount);

            if (success) {
                // 发送成功，清空批次
                batchCount = 0;
            } else {
                DEBUG_PRINTLN("[Send] 发送失败，数据保留在缓冲区等待重发");
                // 数据已在 ringBuffer 中有备份（push 发生在采集时），
                // 不清空 batchCount 让下一批继续累积并重试
            }
        } else {
            // MQTT 未连接，batchCount 不清零，数据已在环形缓冲区备份
            // 等待连接恢复后可以重新发送
            DEBUG_PRINTF("[Send] MQTT 未连接，数据暂存缓冲区 (%d 个样本)\n",
                         ringBuffer.available());
        }
    }

    // ─────────────────────────────────────────────
    // 第五部分：连接恢复后发送缓冲区数据
    // ─────────────────────────────────────────────
    if (mqttManager.isConnected() && ringBuffer.available() > BATCH_SIZE) {
        // 连接恢复后，将缓冲区中的数据尽快发出
        MPU6500_Data_t resendBatch[BATCH_SIZE];
        size_t count = ringBuffer.popBatch(resendBatch, BATCH_SIZE);

        if (count > 0) {
            mqttManager.sendBatch(resendBatch, count);
            DEBUG_PRINTF("[Recovery] 发送缓冲区数据: %d 个样本\n", count);
        }
    }

    // ─────────────────────────────────────────────
    // 第六部分：调试信息输出
    // ─────────────────────────────────────────────
    printDebugInfo();

    // 小延时防止 CPU 过载
    delayMicroseconds(100);
}
