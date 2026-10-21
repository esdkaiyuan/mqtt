package com.mqtt.cloud.collect.modbus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Modbus TCP 连接参数（T-31 P4）。
 * <p>
 * 来源优先级：{@code product.driver_config} 显式字段 &gt; 应用默认值。
 * host 必须显式配置——默认连 127.0.0.1 会让配置错误表现为「一直采不到值」，
 * 不如在驱动层直接给出可读的失败原因。
 *
 * @param host           现场设备 / 网关地址
 * @param port           Modbus TCP 端口（默认 502）
 * @param unitId         从站地址（0~247）
 * @param connectTimeout 连接超时
 * @param readTimeout    读超时
 * @param retry          单次请求重试次数
 * @param wordOrder      32/64 位数值字序：ABCD / CDAB / BADC / DCBA
 */
public record ModbusConnectionConfig(String host,
                                     int port,
                                     int unitId,
                                     Duration connectTimeout,
                                     Duration readTimeout,
                                     int retry,
                                     String wordOrder) {

    private static final int DEFAULT_PORT = 502;
    private static final int DEFAULT_UNIT_ID = 1;
    private static final int MAX_UNIT_ID = 247;
    private static final String DEFAULT_WORD_ORDER = "ABCD";
    private static final java.util.Set<String> WORD_ORDERS =
            java.util.Set.of("ABCD", "CDAB", "BADC", "DCBA");

    /** 兼容既有调用：未指定字序时按 ABCD。 */
    public ModbusConnectionConfig(String host, int port, int unitId,
                                  Duration connectTimeout, Duration readTimeout, int retry) {
        this(host, port, unitId, connectTimeout, readTimeout, retry, DEFAULT_WORD_ORDER);
    }

    /**
     * 解析驱动配置。非法 JSON 或字段越界抛出 {@link IllegalArgumentException}，
     * 由采集器转成「全部点位 FAILED」而不外泄异常。
     */
    public static ModbusConnectionConfig parse(String driverConfig,
                                               String defaultHost,
                                               int defaultPort,
                                               int defaultUnitId,
                                               Duration connectTimeout,
                                               Duration readTimeout,
                                               int defaultRetry,
                                               ObjectMapper objectMapper) {
        JsonNode root = null;
        if (driverConfig != null && !driverConfig.isBlank()) {
            try {
                root = objectMapper.readTree(driverConfig);
            } catch (Exception e) {
                throw new IllegalArgumentException("driver_config 不是合法 JSON", e);
            }
        }

        String host = text(root, "host", defaultHost);
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("driver_config 缺少 host");
        }
        int port = integer(root, "port", defaultPort > 0 ? defaultPort : DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port 超出 1~65535: " + port);
        }
        int unitId = integer(root, "unitId", defaultUnitId);
        if (unitId < 0 || unitId > MAX_UNIT_ID) {
            throw new IllegalArgumentException("unitId 超出 0~" + MAX_UNIT_ID + ": " + unitId);
        }
        long connectMs = longValue(root, "connectTimeoutMs",
                connectTimeout == null ? 3000L : connectTimeout.toMillis());
        long readMs = longValue(root, "readTimeoutMs",
                readTimeout == null ? 3000L : readTimeout.toMillis());
        if (connectMs <= 0 || readMs <= 0) {
            throw new IllegalArgumentException("连接 / 读超时必须大于 0");
        }
        int retry = integer(root, "retry", defaultRetry);
        if (retry < 0 || retry > 10) {
            throw new IllegalArgumentException("retry 超出 0~10: " + retry);
        }
        String wordOrder = text(root, "wordOrder", DEFAULT_WORD_ORDER);
        String normalizedWordOrder = wordOrder == null ? DEFAULT_WORD_ORDER
                : wordOrder.trim().toUpperCase(java.util.Locale.ROOT);
        if (!WORD_ORDERS.contains(normalizedWordOrder)) {
            throw new IllegalArgumentException("wordOrder 仅支持 ABCD / CDAB / BADC / DCBA: " + wordOrder);
        }
        return new ModbusConnectionConfig(host.trim(), port, unitId,
                Duration.ofMillis(connectMs), Duration.ofMillis(readMs), retry, normalizedWordOrder);
    }

    private static String text(JsonNode root, String field, String fallback) {
        if (root == null) {
            return fallback;
        }
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static int integer(JsonNode root, String field, int fallback) {
        if (root == null) {
            return fallback;
        }
        JsonNode value = root.get(field);
        return value == null || !value.isNumber() ? fallback : value.asInt();
    }

    private static long longValue(JsonNode root, String field, long fallback) {
        if (root == null) {
            return fallback;
        }
        JsonNode value = root.get(field);
        return value == null || !value.isNumber() ? fallback : value.asLong();
    }
}
