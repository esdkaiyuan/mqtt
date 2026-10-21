package com.mqtt.cloud.collect.modbus;

import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.entity.DevicePointTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modbus 批量读合并（T-31 P4）。
 * <p>
 * 现场总线往返是采集耗时的主要来源。把同一功能码下地址连续的寄存器 / 线圈合并成一次读，
 * 能把「20 个点 20 次往返」压到「1~2 次往返」；不连续、跨功能码、跨类型一律不合并。
 * <p>
 * 合并失败由 {@code ModbusPointCollector} 自动降级为逐点读，本类只负责「怎么读最省」。
 */
public final class ModbusPointDispatch {

    /** Modbus 单次读保持 / 输入寄存器的上限（协议规定 125）。 */
    public static final int MAX_REGISTERS_PER_READ = 125;

    /** Modbus 单次读线圈 / 离散输入的上限（协议规定 2000）。 */
    public static final int MAX_BITS_PER_READ = 2000;

    /** Modbus 从站地址上限（0~247）。 */
    public static final int MAX_UNIT_ID = 247;

    private ModbusPointDispatch() {
    }

    /** 兼容既有调用：未指定设备默认从站时按 1。 */
    public static List<ReadGroup> plan(List<DevicePointTable> points) {
        return plan(points, 1);
    }

    /**
     * 把点位编排成读取计划。
     * <p>
     * 合并键必须是 {@code (pointType, unitId)}：不同从站即使功能码与地址完全相同，
     * 也绝不能合并成一次读，否则会把 A 从站的值写到 B 从站点位上。
     * <p>
     * TAG / NODE 类型与非法地址不在本类处理：它们没有 Modbus 读取语义，
     * 由采集器直接标记失败，避免把「点位类型不支持」伪装成「读寄存器失败」。
     */
    public static List<ReadGroup> plan(List<DevicePointTable> points, int defaultUnitId) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        Map<GroupKey, List<Candidate>> byGroup = new LinkedHashMap<>();
        for (DevicePointTable point : points) {
            String type = PointTypeValue.normalize(point.getPointType());
            if (!isModbusType(type)) {
                continue;
            }
            Integer address = parseAddress(point.getAddress());
            Integer unitId = resolveUnitId(point, defaultUnitId);
            if (address == null || unitId == null) {
                continue;
            }
            int width = PointTypeValue.isSingleBit(type)
                    ? 1 : ModbusValueConverter.registerWidth(point.getDataType());
            byGroup.computeIfAbsent(new GroupKey(type, unitId), key -> new ArrayList<>())
                    .add(new Candidate(point, address, address + width - 1));
        }

        List<ReadGroup> groups = new ArrayList<>();
        for (Map.Entry<GroupKey, List<Candidate>> entry : byGroup.entrySet()) {
            GroupKey key = entry.getKey();
            List<Candidate> candidates = entry.getValue();
            candidates.sort(Comparator.comparingInt(Candidate::start));

            int limit = PointTypeValue.isSingleBit(key.pointType())
                    ? MAX_BITS_PER_READ : MAX_REGISTERS_PER_READ;
            MutableGroup current = null;
            for (Candidate candidate : candidates) {
                if (current == null
                        || candidate.start() > current.end + 1
                        || Math.max(current.end, candidate.end()) - current.start + 1 > limit) {
                    if (current != null) {
                        groups.add(current.toGroup(key.pointType(), key.unitId()));
                    }
                    current = new MutableGroup(candidate.start(), candidate.end());
                } else {
                    current.end = Math.max(current.end, candidate.end());
                }
                current.points.add(candidate.point());
            }
            if (current != null) {
                groups.add(current.toGroup(key.pointType(), key.unitId()));
            }
        }
        return groups;
    }

    /** 解析点位有效从站：点位值优先，空则继承设备默认值；越界返回 {@code null}。 */
    public static Integer resolveUnitId(DevicePointTable point, int defaultUnitId) {
        Integer configured = point == null ? null : point.getUnitId();
        int unitId = configured == null ? defaultUnitId : configured;
        return unitId < 0 || unitId > MAX_UNIT_ID ? null : unitId;
    }

    private static boolean isModbusType(String type) {
        return switch (type) {
            case PointTypeValue.HOLDING_REGISTER, PointTypeValue.INPUT_REGISTER,
                 PointTypeValue.COIL, PointTypeValue.DISCRETE_INPUT -> true;
            default -> false;
        };
    }

    /** 寄存器地址必须是非负整数；非法地址返回 null，由采集器标记失败。 */
    public static Integer parseAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(address.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Candidate(DevicePointTable point, int start, int end) {
    }

    private record GroupKey(String pointType, int unitId) {
    }

    private static final class MutableGroup {
        private final int start;
        private int end;
        private final List<DevicePointTable> points = new ArrayList<>();

        private MutableGroup(int start, int end) {
            this.start = start;
            this.end = end;
        }

        private ReadGroup toGroup(String pointType, int unitId) {
            return new ReadGroup(pointType, unitId, start, end, points);
        }
    }

    /**
     * 一次批量读：{@code unitId} 从站、{@code start} 起共 {@code count} 个寄存器 / 位。
     * {@code end} 为闭区间末地址，便于测试与排障。
     */
    public record ReadGroup(String pointType, int unitId, int start, int end,
                            List<DevicePointTable> points) {

        public ReadGroup {
            points = points == null ? List.of() : List.copyOf(points);
        }

        public int count() {
            return end - start + 1;
        }
    }
}
