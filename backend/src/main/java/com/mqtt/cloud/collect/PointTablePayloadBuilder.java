package com.mqtt.cloud.collect;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 点表下发载荷组装器（T-31 P3，路线 A）。
 * <p>
 * <b>只组装，不发布</b>：publish 由 {@code DeviceCommandService} 统一负责，
 * 这样载荷格式的变更（下字段、改名、调版本策略）不会与下行通道的实现纠缠在一起，
 * 也便于对组装逻辑做纯函数式单测。
 * <p>
 * 字段名<b>与 {@code device_point_table} 的列名一一对应</b>（{@code point_key → pointKey}）。
 * 这是刻意选择：边缘网关收到载荷后可以直接按同名映射落自己的本地配置，
 * 无需维护一张「平台列名 → 边缘字段名」的翻译表 —— 那张表一旦两侧版本错位就是静默的采不到值。
 * <p>
 * 无状态、可复用，{@code version} 由调用方传入（本类不负责生成版本号，
 * 因为版本需要跨副本单调，必须由持有持久化/Redis 的服务层生成）。
 */
public final class PointTablePayloadBuilder {

    private PointTablePayloadBuilder() {
    }

    /**
     * 组装点表下发载荷。
     *
     * @param mapper       用于创建子节点（由调用方注入，本类不持有序列化设施）
     * @param deviceId     设备ID，写入载荷供网关校验，防止把 A 设备的点表应用到 B 设备
     * @param version      点表版本号（单调递增，由调用方生成）
     * @param dispatchedAt 下发时间（毫秒时间戳）
     * @param points       启用中的边缘点位
     * @return 新建的载荷根节点
     */
    public static ObjectNode build(ObjectMapper mapper, Long deviceId, long version,
                                   long dispatchedAt, List<PointTableEntry> points) {
        ObjectNode root = mapper.createObjectNode();
        root.put("deviceId", deviceId);
        root.put("version", version);
        root.put("dispatchedAt", dispatchedAt);
        ArrayNode array = root.putArray("points");
        for (PointTableEntry point : points) {
            array.add(toNode(mapper, point));
        }
        return root;
    }

    private static ObjectNode toNode(ObjectMapper mapper, PointTableEntry point) {
        ObjectNode node = mapper.createObjectNode();
        node.put("pointKey", point.pointKey());
        node.put("pointType", point.pointType());
        // address 对 TAG/NODE 类点位是名称而非数字，对线圈类是布尔字面量，故按文本下发。
        putIfPresent(node, "address", point.address());
        putIfPresent(node, "dataType", point.dataType());
        if (point.unitId() != null) {
            node.put("unitId", point.unitId());
        }
        putIfPresent(node, "scale", point.scale());
        putIfPresent(node, "offset", point.offset());
        putIfPresent(node, "unit", point.unit());
        if (point.readIntervalMs() != null) {
            node.put("readIntervalMs", point.readIntervalMs());
        }
        return node;
    }

    /** 数值以字符串下发：{@code DECIMAL(18,6)} 转 double 会有精度表述歧义，交给网关按十进制解析更稳。 */
    private static void putIfPresent(ObjectNode node, String field, BigDecimal value) {
        if (value != null) {
            node.put(field, value.toPlainString());
        }
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    /**
     * 载荷中的单个点位（与 {@code device_point_table} 列一一对应）。
     * <p>
     * 用不可变 record 而非直接传实体，是为了把「下发给外部的字段集合」固定下来：
     * 实体将来加列时不会自动泄漏到下发载荷里，避免把内部字段（如 {@code enabled}、
     * {@code executor}）无意间暴露给网关。
     *
     * @param unitId         从站地址，可空（空表示网关使用驱动默认值，T-50）
     * @param scale          线性缩放系数，可空（空表示不缩放）
     * @param offset         线性偏移，可空
     * @param readIntervalMs 采集周期，可空（网关侧应有默认值兜底）
     */
    public record PointTableEntry(String pointKey,
                                  String pointType,
                                  String address,
                                  String dataType,
                                  Integer unitId,
                                  BigDecimal scale,
                                  BigDecimal offset,
                                  String unit,
                                  Integer readIntervalMs) {
    }
}
