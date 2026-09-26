/**
 * main.cpp - ESP32-S3 摔倒检测数据采集固件 - 主程序
 *
 * 功能概述：
 *   1. 连接 WiFi 网络
 *   2. 初始化 MPU6500 六轴传感器（I2C 通信）
 *   3. 以 100Hz 采样率持续采集加速度和角速度数据
 *   4. 将数据写入环形缓冲区（1000 个样本，约 10 秒）
 *   5. 每 10 个样本打包为 JSON 数组，通过 WebSocket 上报后端
 *   6. 断线自动重连；断线期间数据留在缓冲区，重连后按顺序补发
 *   7. LED 指示系统状态
 *
 * 硬件连接（ESP32-S3-DevKitC-1）：
 *   ESP32-S3          MPU6500
 *   GPIO1 (SDA)   --> SDA
 *   GPIO2 (SCL)   --> SCL
 *   3.3V          --> VCC
 *   GND           --> GND
 *   GPIO48        --> 板载 LED
 *
 * 通信协议：WebSocket (ws://<host>:8000/ws/motion/<DEVICE_ID>)
 * 编程环境：PlatformIO + Arduino 框架
 * 版本：2.0.0
 */

#include <Arduino.h>
#include <WiFi.h>
#include "config.h"
#include "mpu6500.h"
#include "websocket_client.h"

// =============================================================================
// 全局对象实例化
// =============================================================================

/** MPU6500 传感器驱动实例 */
MPU6500 mpu(Wire, MPU6500_I2C_ADDR);

/** WebSocket 客户端管理实例 */
WebSocketManager wsManager;

// =============================================================================
// 环形缓冲区（Ring Buffer）
// =============================================================================

/**
 * 环形缓冲区结构
 *
 * 作为待上报数据的唯一来源：采集时写入，连接正常时按批取出上报。
 * 取数使用 peek + discard 两步，只有发送成功才丢弃，避免重复上报。
 */
class RingBuffer {
public:
    RingBuffer() : _head(0), _tail(0), _count(0) {}

    /**
     * 将数据写入缓冲区
     * @param data 要存储的传感器数据
     * @return 恒为 true（缓冲区满时丢弃最旧数据）
     */
    bool push(const MPU6500_Data_t &data)
    {
        if (_count == RING_BUFFER_SIZE) {
            // 缓冲区满，丢弃最旧数据以容纳新数据
            _tail = (_tail + 1) % RING_BUFFER_SIZE;
            _count--;
        }

        _buffer[_head] = data;
        _head = (_head + 1) % RING_BUFFER_SIZE;
        _count++;
        return true;
    }

    /**
     * 复制最旧的若干条数据，但不移除
     * @param out      输出数组
     * @param maxCount 最多复制条数
     * @return 实际复制条数
     */
    size_t peekBatch(MPU6500_Data_t *out, size_t maxCount) const
    {
        size_t n = (_count < maxCount) ? _count : maxCount;
        size_t idx = _tail;

        for (size_t i = 0; i < n; i++) {
            out[i] = _buffer[idx];
            idx = (idx + 1) % RING_BUFFER_SIZE;
        }
        return n;
    }

    /**
     * 移除最旧的若干条数据（发送成功后调用）
     * @param count 要移除的条数
     */
    void discard(size_t count)
    {
        size_t n = (count < _count) ? count : _count;
        _tail = (_tail + n) % RING_BUFFER_SIZE;
        _count -= n;
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
// LED 状态管理
// =============================================================================

/** LED 状态枚举 */
enum class LedState {
    OFF,                // 熄灭
    WIFI_CONNECTING,    // WiFi 连接中
    WS_CONNECTING,      // WebSocket 连接中
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

        case LedState::WS_CONNECTING:
            interval = LED_BLINK_WS_CONNECT;
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
    DEBUG_PRINTLN("  ESP32-S3 摔倒检测数据采集固件 v2.0.0");
    DEBUG_PRINTLN("  (WebSocket 版本)");
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
    DEBUG_PRINTLN("[WiFi] 连接成功！");
    DEBUG_PRINTF("[WiFi] IP 地址: %s\n", WiFi.localIP().toString().c_str());
    DEBUG_PRINTF("[WiFi] 信号强度: %d dBm\n", WiFi.RSSI());

    return true;
}

// =============================================================================
// 调试信息输出
// =============================================================================

/** 上次调试输出时间 */
unsigned long lastDebugPrint = 0;
/** 统计周期内的采样计数 */
uint32_t sampleCountInPeriod = 0;

/**
 * 定期打印系统状态信息
 */
void printDebugInfo()
{
    unsigned long now = millis();

    if (now - lastDebugPrint >= DEBUG_PRINT_INTERVAL_MS) {
        DEBUG_PRINTLN("");
        DEBUG_PRINTLN("┌──────────────── 系统状态 ────────────────┐");
        DEBUG_PRINTF("│ 运行时间:      %lu 秒\n", now / 1000);
        DEBUG_PRINTF("│ WiFi 状态:     %s (RSSI: %d dBm)\n",
                     WiFi.isConnected() ? "已连接" : "未连接", WiFi.RSSI());
        DEBUG_PRINTF("│ WebSocket:     %s\n", wsManager.getStateString());
        DEBUG_PRINTF("│ 发送成功/失败: %lu / %lu\n",
                     wsManager.getSendCount(), wsManager.getFailCount());
        DEBUG_PRINTF("│ 缓冲区占用:    %d / %d\n", ringBuffer.available(), RING_BUFFER_SIZE);
        DEBUG_PRINTF("│ 实际采样率:    %d Hz\n", sampleCountInPeriod * 1000 / DEBUG_PRINT_INTERVAL_MS);
        DEBUG_PRINTF("│ 可用内存:      %lu 字节\n", ESP.getFreeHeap());
        DEBUG_PRINTF("│ 设备ID:        %s\n", DEVICE_ID);
        DEBUG_PRINTLN("└──────────────────────────────────────────┘");

        // 重置采样统计
        sampleCountInPeriod = 0;
        lastDebugPrint = now;
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
    DEBUG_PRINTLN("  (WebSocket 协议版本)");
    DEBUG_PRINTF("  设备ID: %s\n", DEVICE_ID);
    DEBUG_PRINTF("  服务器: ws://%s:%d%s%s\n",
                 WS_SERVER_HOST, WS_SERVER_PORT, WS_PATH_PREFIX, DEVICE_ID);
    DEBUG_PRINTLN("============================================");
    DEBUG_PRINTLN("");

    // 初始化 LED 引脚
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LED_OFF);
    DEBUG_PRINTLN("[INIT] LED 引脚初始化完成");

    // 初始化 MPU6500 传感器（非阻塞：即使失败也继续运行）
    DEBUG_PRINTLN("[INIT] 正在初始化 MPU6500 传感器...");
    if (!mpu.begin(I2C_SDA_PIN, I2C_SCL_PIN, I2C_CLOCK_SPEED)) {
        DEBUG_PRINTLN("[INIT] 警告：MPU6500 初始化失败，请检查接线与 I2C 地址");
    } else {
        DEBUG_PRINTLN("[INIT] MPU6500 初始化成功");
    }

    // 连接 WiFi
    if (!connectWiFi()) {
        DEBUG_PRINTLN("[INIT] WiFi 连接失败，将在后台持续重试");
    }

    // 初始化 WebSocket 客户端
    wsManager.begin();

    DEBUG_PRINTLN("");
    DEBUG_PRINTLN("[INIT] ===== 系统初始化完成，开始数据采集 =====");
    DEBUG_PRINTF("[INIT] 采样率: %d Hz\n", SAMPLE_RATE_HZ);
    DEBUG_PRINTF("[INIT] 批次大小: %d 个样本\n", BATCH_SIZE);
    DEBUG_PRINTF("[INIT] 发送间隔: %d ms\n", SEND_INTERVAL_MS);
    DEBUG_PRINTF("[INIT] 缓冲区容量: %d 个样本\n", RING_BUFFER_SIZE);
    DEBUG_PRINTLN("");
}

// =============================================================================
// 主循环函数（Arduino loop）
// =============================================================================
void loop()
{
    static unsigned long lastSampleTime = 0;
    static unsigned long lastWifiRetry = 0;

    unsigned long now = millis();

    // ─────────────────────────────────────────────
    // 第一部分：WiFi 连接管理
    // ─────────────────────────────────────────────
    if (WiFi.status() != WL_CONNECTED) {
        setLedState(LedState::WIFI_CONNECTING);

        // 每隔一段时间尝试重连 WiFi
        if (now - lastWifiRetry >= WIFI_RETRY_DELAY_MS) {
            lastWifiRetry = now;
            DEBUG_PRINTLN("[WiFi] 连接丢失，正在重连...");
            WiFi.disconnect();
            WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        }

        updateLED();
        wsManager.update();
        return;  // WiFi 未连接时跳过后续处理
    }

    // ─────────────────────────────────────────────
    // 第二部分：WebSocket 连接管理
    // ─────────────────────────────────────────────
    wsManager.update();

    if (wsManager.isConnected()) {
        // 刚刚发送过数据则快闪，否则常亮
        if (now - wsManager.getLastSendAt() < LED_BLINK_DATA_SEND) {
            setLedState(LedState::DATA_SENDING);
        } else {
            setLedState(LedState::CONNECTED);
        }
    } else {
        setLedState(LedState::WS_CONNECTING);
    }

    updateLED();

    // ─────────────────────────────────────────────
    // 第三部分：传感器数据采集（100Hz）
    // ─────────────────────────────────────────────
    if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
        lastSampleTime = now;

        MPU6500_Data_t data;
        if (mpu.readAll(data)) {
            ringBuffer.push(data);
            sampleCountInPeriod++;
        } else {
            DEBUG_PRINTLN("[Sensor] 警告：传感器读取失败");
        }
    }

    // ─────────────────────────────────────────────
    // 第四部分：批量上报（连接正常时按批取出）
    // ─────────────────────────────────────────────
    if (wsManager.isConnected() && ringBuffer.available() >= BATCH_SIZE) {
        MPU6500_Data_t batch[BATCH_SIZE];
        size_t count = ringBuffer.peekBatch(batch, BATCH_SIZE);

        // 只有发送成功才从缓冲区移除，失败则保留待下轮重试
        if (wsManager.sendBatch(batch, count)) {
            ringBuffer.discard(count);
        }
    }

    // ─────────────────────────────────────────────
    // 第五部分：调试信息输出
    // ─────────────────────────────────────────────
    printDebugInfo();

    // 小延时防止 CPU 过载
    delayMicroseconds(100);
}