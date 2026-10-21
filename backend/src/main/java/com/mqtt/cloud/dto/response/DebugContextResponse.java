package com.mqtt.cloud.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备调试上下文（T-37）。
 * <p>
 * 只读投影：连接参数来自部署配置，Topic 来自产品模板，物模型字段来自 TSL 运行时视图，
 * 最新值来自 {@code device_property_latest}。<b>不包含设备密钥、平台账号或任何可直连 Broker 的明文凭据。</b>
 */
public record DebugContextResponse(
        String deviceKey,
        String deviceName,
        String productKey,
        String productName,
        String protocolType,
        String authMode,
        String status,
        boolean modeled,
        int thingModelVersion,
        Broker broker,
        Topics topics,
        List<PropertyView> properties,
        List<EventView> events,
        List<ServiceView> services,
        List<PropertyView> writableProperties) {

    /** 连接参数。密码位只给提示，不回显。 */
    public record Broker(String tcp, String tls, String ws, String username, String passwordHint) {
    }

    /** 标准上下行主题。 */
    public record Topics(String data,
                         String heartbeat,
                         String lwt,
                         String reply,
                         String ota,
                         String commandDown) {
    }

    /** 属性定义 + 最新值。 */
    public record PropertyView(String identifier,
                               String type,
                               String accessMode,
                               BigDecimal min,
                               BigDecimal max,
                               boolean integer,
                               List<String> enumKeys,
                               Integer textLength,
                               String value,
                               LocalDateTime reportedAt) {
    }

    public record EventView(String identifier, String eventType) {
    }

    public record ParamView(String identifier,
                            String type,
                            BigDecimal min,
                            BigDecimal max,
                            boolean integer,
                            List<String> enumKeys,
                            Integer textLength,
                            boolean required) {
    }

    public record ServiceView(String identifier, String callType, List<ParamView> input) {
    }
}
