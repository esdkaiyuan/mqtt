/**
 * mqtt_client.h - MQTT 客户端管理类
 *
 * 功能：
 *   - 封装 MQTT 连接管理（使用 PubSubClient）
 *   - 自动重连机制
 *   - 批量数据发送（JSON 数组格式）
 *   - 心跳保活（MQTT keepalive）
 *   - LWT 遗嘱消息支持
 *
 * 依赖：knolleary/PubSubClient 库
 */

#ifndef MQTT_CLIENT_H
#define MQTT_CLIENT_H

#include <Arduino.h>
#include <PubSubClient.h>
#include <WiFiClient.h>
#include <ArduinoJson.h>
#include "config.h"
#include "mpu6500.h"

// =============================================================================
// MQTT 连接状态枚举
// =============================================================================
enum class MQTT_State {
    DISCONNECTED,       // 未连接
    CONNECTING,         // 正在连接
    CONNECTED,          // 已连接
    RECONNECTING        // 正在重连
};

// =============================================================================
// MQTT 客户端管理类
// =============================================================================
class MQTTManager {
public:
    /**
     * 构造函数
     */
    MQTTManager()
        : _state(MQTT_State::DISCONNECTED)
        , _lastReconnectAttempt(0)
        , _sendCount(0)
        , _failCount(0)
        , _connectedOnce(false)
    {
    }

    /**
     * 初始化 MQTT 客户端
     * 建立到 MQTT Broker 的连接
     */
    void begin()
    {
        DEBUG_PRINTLN("[MQTT] 初始化 MQTT 客户端...");
        DEBUG_PRINTF("[MQTT] Broker 地址: %s:%d\n", MQTT_BROKER_HOST, MQTT_BROKER_PORT);
        DEBUG_PRINTF("[MQTT] 客户端ID: %s\n", MQTT_CLIENT_ID);
        DEBUG_PRINTF("[MQTT] 用户名: %s\n", MQTT_USERNAME);

        // 配置 WiFi 客户端
        _wifiClient.setTimeout(5000);

        // 配置 MQTT 客户端
        _mqttClient.setServer(MQTT_BROKER_HOST, MQTT_BROKER_PORT);
        _mqttClient.setCallback([this](char *topic, byte *payload, unsigned int length) {
            this->_handleMessage(topic, payload, length);
        });
        _mqttClient.setKeepAlive(MQTT_KEEPALIVE);
        _mqttClient.setBufferSize(MQTT_MAX_PACKET_SIZE);

        // WiFi 未连接时跳过，等待 loop() 中自动重试
        if (WiFi.status() == WL_CONNECTED) {
            _connect();
        } else {
            DEBUG_PRINTLN("[MQTT] WiFi 未连接，将在连接后尝试 MQTT");
        }

        DEBUG_PRINTLN("[MQTT] MQTT 客户端初始化完成");
    }

    /**
     * 主循环更新（需在 loop() 中调用）
     * 处理 MQTT 事件、重连逻辑
     */
    void update()
    {
        if (!_mqttClient.connected()) {
            _state = MQTT_State::DISCONNECTED;
            _handleReconnect();
        } else {
            if (!_connectedOnce) {
                _state = MQTT_State::CONNECTED;
                _connectedOnce = true;
                DEBUG_PRINTLN("[MQTT] 首次连接成功");
                // 连接成功后发送上线消息
                _publishLWT("online");
            }
            _mqttClient.loop();
        }
    }

    /**
     * 发送一批传感器数据（JSON 数组格式）到 MQTT Topic
     * @param dataArray  传感器数据数组
     * @param count      数据条数
     * @return true=发送成功
     */
    bool sendBatch(const MPU6500_Data_t *dataArray, size_t count)
    {
        if (_state != MQTT_State::CONNECTED || count == 0) {
            _failCount++;
            return false;
        }

        // 使用 ArduinoJson 构建 JSON 数组
        JsonDocument doc;
        JsonArray batch = doc.to<JsonArray>();

        for (size_t i = 0; i < count; i++) {
            JsonObject sample = batch.add<JsonObject>();

            sample["device_id"] = DEVICE_ID;
            sample["timestamp"] = dataArray[i].timestamp;
            sample["ax"] = roundTo(dataArray[i].ax, 4);
            sample["ay"] = roundTo(dataArray[i].ay, 4);
            sample["az"] = roundTo(dataArray[i].az, 4);
            sample["gx"] = roundTo(dataArray[i].gx, 4);
            sample["gy"] = roundTo(dataArray[i].gy, 4);
            sample["gz"] = roundTo(dataArray[i].gz, 4);
        }

        // 序列化 JSON 字符串
        String jsonString;
        serializeJson(doc, jsonString);

        // 通过 MQTT 发布
        bool success = _mqttClient.publish(MQTT_TOPIC_DATA, jsonString.c_str());

        if (success) {
            _sendCount++;
            DEBUG_PRINTF("[MQTT] 发布成功: %d 个样本, 长度=%d 字节, topic=%s\n",
                         count, jsonString.length(), MQTT_TOPIC_DATA);
        } else {
            _failCount++;
            DEBUG_PRINTLN("[MQTT] 发布失败！");
        }

        return success;
    }

    /**
     * 发送单条传感器数据
     * @param data 传感器数据
     * @return true=发送成功
     */
    bool sendSingle(const MPU6500_Data_t &data)
    {
        return sendBatch(&data, 1);
    }

    /**
     * 获取当前连接状态
     * @return MQTT 连接状态
     */
    MQTT_State getState() const { return _state; }

    /**
     * 检查是否已连接
     * @return true=已连接
     */
    bool isConnected() { return _state == MQTT_State::CONNECTED && _mqttClient.connected(); }

    /**
     * 获取成功发送计数
     * @return 发送成功次数
     */
    uint32_t getSendCount() const { return _sendCount; }

    /**
     * 获取发送失败计数
     * @return 发送失败次数
     */
    uint32_t getFailCount() const { return _failCount; }

    /**
     * 获取连接状态字符串描述
     * @return 状态字符串
     */
    const char* getStateString() const
    {
        switch (_state) {
            case MQTT_State::DISCONNECTED:  return "断开连接";
            case MQTT_State::CONNECTING:    return "正在连接";
            case MQTT_State::CONNECTED:     return "已连接";
            case MQTT_State::RECONNECTING:  return "重连中";
            default:                        return "未知";
        }
    }

    /**
     * 断开 MQTT 连接
     */
    void disconnect()
    {
        if (_mqttClient.connected()) {
            _publishLWT("offline");
            _mqttClient.disconnect();
        }
        _state = MQTT_State::DISCONNECTED;
        _connectedOnce = false;
    }

private:
    WiFiClient _wifiClient;               // WiFi TCP 客户端
    PubSubClient _mqttClient{_wifiClient}; // MQTT 客户端实例（绑定 WiFi 客户端）
    MQTT_State _state;                    // 连接状态
    unsigned long _lastReconnectAttempt;  // 上次重连尝试时间
    uint32_t _sendCount;                  // 成功发送计数
    uint32_t _failCount;                  // 发送失败计数
    bool _connectedOnce;                  // 是否曾经连接成功过

    /**
     * 建立 MQTT 连接
     */
    void _connect()
    {
        if (_state == MQTT_State::CONNECTING) return;
        if (WiFi.status() != WL_CONNECTED) {
            DEBUG_PRINTLN("[MQTT] WiFi 未连接，跳过 MQTT 连接");
            _state = MQTT_State::DISCONNECTED;
            return;
        }

        _state = MQTT_State::CONNECTING;

        DEBUG_PRINTF("[MQTT] 正在连接: %s:%d\n", MQTT_BROKER_HOST, MQTT_BROKER_PORT);

        // 生成带时间戳的 Client ID 以确保唯一性
        String clientId = String(MQTT_CLIENT_ID) + "_" + String((uint32_t)ESP.getEfuseMac(), HEX);

        // 连接 MQTT Broker
        // 参数: clientId, username, password, willTopic, willQoS, willRetain, willMessage
        bool success = _mqttClient.connect(
            clientId.c_str(),
            MQTT_USERNAME,
            MQTT_PASSWORD,
            MQTT_TOPIC_LWT,           // Will Topic
            1,                         // Will QoS
            true,                      // Will Retain
            "offline"                  // Will message
        );

        if (success) {
            _state = MQTT_State::CONNECTED;
            _connectedOnce = true;
            DEBUG_PRINTLN("[MQTT] 连接成功！");
            DEBUG_PRINTF("[MQTT] Client ID: %s\n", clientId.c_str());

            // 发布上线消息
            _publishLWT("online");

            // 订阅指令 Topic（可选：接收服务器下发的指令）
            _mqttClient.subscribe(MQTT_TOPIC_CMD);
        } else {
            _state = MQTT_State::RECONNECTING;
            DEBUG_PRINTF("[MQTT] 连接失败, rc=%d\n", _mqttClient.state());
        }
    }

    /**
     * 处理 MQTT 消息回调
     */
    void _handleMessage(char *topic, byte *payload, unsigned int length)
    {
        DEBUG_PRINTF("[MQTT] 收到消息: topic=%s, length=%d\n", topic, length);

        String message;
        message.reserve(length);
        for (unsigned int i = 0; i < length; i++) {
            message += (char)payload[i];
        }
        DEBUG_PRINTF("[MQTT] 消息内容: %s\n", message.c_str());

        // 解析指令（预留扩展）
        JsonDocument doc;
        DeserializationError error = deserializeJson(doc, message);

        if (error) {
            DEBUG_PRINTF("[MQTT] JSON 解析错误: %s\n", error.c_str());
            return;
        }

        if (doc["cmd"].is<const char*>()) {
            String cmd = doc["cmd"].as<String>();

            if (cmd == "ping") {
                // 回复 pong
                _mqttClient.publish("device/ESP32_001/resp", "{\"cmd\":\"pong\"}");
            }
            else if (cmd == "reset") {
                DEBUG_PRINTLN("[MQTT] 收到复位指令，3秒后重启...");
                delay(3000);
                ESP.restart();
            }
        }
    }

    /**
     * 处理自动重连逻辑
     */
    void _handleReconnect()
    {
        unsigned long now = millis();

        if (now - _lastReconnectAttempt >= MQTT_RECONNECT_INTERVAL) {
            _lastReconnectAttempt = now;
            _state = MQTT_State::RECONNECTING;

            DEBUG_PRINTF("[MQTT] 尝试重新连接... (已发送: %d, 失败: %d)\n",
                         _sendCount, _failCount);

            _connect();
        }
    }

    /**
     * 发布 LWT（遗嘱/心跳）消息
     * @param status 状态: "online" 或 "offline"
     */
    void _publishLWT(const char *status)
    {
        if (_mqttClient.connected()) {
            _mqttClient.publish(MQTT_TOPIC_HEARTBEAT, status);
            DEBUG_PRINTF("[MQTT] 心跳发布: %s\n", status);
        }
    }

    /**
     * 四舍五入到指定小数位
     * @param value  原始浮点数
     * @param digits 小数位数
     * @return 四舍五入后的值
     */
    static float roundTo(float value, int digits)
    {
        float multiplier = pow(10.0f, digits);
        return roundf(value * multiplier) / multiplier;
    }
};

#endif // MQTT_CLIENT_H
