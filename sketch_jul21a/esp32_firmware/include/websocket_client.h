/**
 * websocket_client.h - WebSocket 客户端管理类
 *
 * 功能：
 *   - 封装 WebSocket 连接管理（使用 links2004/WebSockets）
 *   - 自动重连机制
 *   - 批量数据发送（JSON 数组格式）
 *   - 发送成功/失败计数
 *
 * 依赖：links2004/WebSockets 库
 *
 * 上报格式与后端 app/routers/websocket.py 的 /ws/motion/{device_id} 约定一致：
 *   [{"timestamp": 12345, "ax": 0.12, ...}, ...]
 */

#ifndef WEBSOCKET_CLIENT_H
#define WEBSOCKET_CLIENT_H

#include <Arduino.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>
#include "config.h"
#include "mpu6500.h"

// =============================================================================
// WebSocket 连接状态枚举
// =============================================================================
enum class WS_State {
    DISCONNECTED,       // 未连接
    CONNECTING,         // 正在连接
    CONNECTED           // 已连接
};

// =============================================================================
// WebSocket 客户端管理类
// =============================================================================
class WebSocketManager {
public:
    WebSocketManager()
        : _state(WS_State::DISCONNECTED)
        , _sendCount(0)
        , _failCount(0)
        , _lastSendAt(0)
    {
    }

    /**
     * 初始化 WebSocket 客户端并开始连接
     */
    void begin()
    {
        String path = String(WS_PATH_PREFIX) + DEVICE_ID;

        DEBUG_PRINTLN("[WS] 初始化 WebSocket 客户端...");
        DEBUG_PRINTF("[WS] 服务器: ws://%s:%d%s\n", WS_SERVER_HOST, WS_SERVER_PORT, path.c_str());
        DEBUG_PRINTF("[WS] 设备ID: %s\n", DEVICE_ID);

        _client.begin(WS_SERVER_HOST, WS_SERVER_PORT, path.c_str());
        _client.onEvent([this](WStype_t type, uint8_t *payload, size_t length) {
            this->_handleEvent(type, payload, length);
        });
        _client.setReconnectInterval(WS_RECONNECT_INTERVAL);

        _state = WS_State::CONNECTING;
        DEBUG_PRINTLN("[WS] WebSocket 客户端初始化完成");
    }

    /**
     * 主循环更新（需在 loop() 中调用）
     * 处理 WebSocket 事件与重连
     */
    void update()
    {
        _client.loop();
    }

    /**
     * 发送一批传感器数据（JSON 数组格式）
     * @param dataArray  传感器数据数组
     * @param count      数据条数
     * @return true=发送成功
     */
    bool sendBatch(const MPU6500_Data_t *dataArray, size_t count)
    {
        if (!isConnected() || count == 0) {
            _failCount++;
            return false;
        }

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

        String jsonString;
        serializeJson(doc, jsonString);

        bool success = _client.sendTXT(jsonString);

        if (success) {
            _sendCount++;
            _lastSendAt = millis();
            DEBUG_PRINTF("[WS] 发送成功: %d 个样本, 长度=%d 字节\n", count, jsonString.length());
        } else {
            _failCount++;
            DEBUG_PRINTLN("[WS] 发送失败！");
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
     * 检查是否已连接
     * @return true=已连接
     */
    bool isConnected()
    {
        return _state == WS_State::CONNECTED && _client.isConnected();
    }

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
     * 最近一次成功发送的时刻（millis()），用于 LED 指示
     */
    unsigned long getLastSendAt() const { return _lastSendAt; }

    /**
     * 获取连接状态字符串描述
     * @return 状态字符串
     */
    const char* getStateString() const
    {
        switch (_state) {
            case WS_State::DISCONNECTED:    return "断开连接";
            case WS_State::CONNECTING:      return "正在连接";
            case WS_State::CONNECTED:       return "已连接";
            default:                        return "未知";
        }
    }

    /**
     * 断开 WebSocket 连接
     */
    void disconnect()
    {
        _client.disconnect();
        _state = WS_State::DISCONNECTED;
    }

private:
    WebSocketsClient _client;       // WebSocket 客户端实例
    WS_State _state;                // 连接状态
    uint32_t _sendCount;            // 成功发送计数
    uint32_t _failCount;            // 发送失败计数
    unsigned long _lastSendAt;      // 最近一次成功发送时刻

    /**
     * 处理 WebSocket 事件
     */
    void _handleEvent(WStype_t type, uint8_t *payload, size_t length)
    {
        switch (type) {
            case WStype_CONNECTED:
                _state = WS_State::CONNECTED;
                DEBUG_PRINTLN("[WS] 连接成功");
                break;

            case WStype_DISCONNECTED:
                _state = WS_State::DISCONNECTED;
                DEBUG_PRINTLN("[WS] 连接断开");
                break;

            case WStype_TEXT:
                // 后端每帧回执：{"status":"ok","count":N,...}
                DEBUG_PRINTF("[WS] 收到回执: %s\n", (char *)payload);
                break;

            default:
                break;
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

#endif // WEBSOCKET_CLIENT_H