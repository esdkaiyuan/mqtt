package com.mqtt.cloud.collect.modbus;

import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.entity.DevicePointTable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Modbus 寄存器 → 工程值换算（T-31 P4）。
 * <p>
 * 统一口径（必须与 D2 协议文档一致）：
 * <ul>
 *   <li>{@code int}：1 个寄存器，按 <b>有符号 16 位</b>解释；</li>
 *   <li>{@code int32} / {@code uint32}：2 个寄存器，分别按有符号 / 无符号 32 位解释；</li>
 *   <li>{@code int64} / {@code uint64}：4 个寄存器，分别按有符号 / 无符号 64 位解释；</li>
 *   <li>{@code float}：2 个寄存器，32 位大端，IEEE-754；</li>
 *   <li>{@code double}：4 个寄存器，64 位大端，IEEE-754；</li>
 *   <li>{@code bool}：1 个寄存器 / 1 个线圈，{@code != 0} 为 true；</li>
 *   <li>{@code string}：1 个寄存器，2 字节 ASCII，遇 NUL 截断。</li>
 * </ul>
 * 数值型工程值 = 原始值 × scale + offset；scale / offset 为空分别按 1 / 0。
 * bool 与 string 不做线性换算。
 * <p>
 * <b>字序</b>：{@code wordOrder} 只影响 32 / 64 位数值，支持
 * {@code ABCD}（大端）、{@code CDAB}（字交换）、{@code BADC}（字节交换）、
 * {@code DCBA}（小端）。16 位 int 与单寄存器 bool/string 不受影响。
 * <p>
 * 之所以把口径写死在纯函数里：边缘侧与平台侧必须算出同一个值，
 * 换算差异不会报错，只会让两块屏幕显示不同的数，事后极难定位。
 */
public final class ModbusValueConverter {

    private static final int DEFAULT_SCALE = 1;
    private static final String DEFAULT_WORD_ORDER = "ABCD";

    private ModbusValueConverter() {
    }

    /** 寄存器宽度（int32/uint32/float 2、int64/uint64/double 4，其余 1）。 */
    public static int registerWidth(String dataType) {
        return switch (normalize(dataType)) {
            case "int32", "uint32", "float" -> 2;
            case "int64", "uint64", "double" -> 4;
            default -> 1;
        };
    }

    /**
     * 把一个点位的原始寄存器 / 位切片转换成 {@link PointValue}。
     *
     * @param registers 从该点位起始地址开始的寄存器（或单个线圈 0/1）
     */
    public static PointValue toPointValue(DevicePointTable point, int[] registers,
                                          LocalDateTime collectedAt) {
        return toPointValue(point, registers, collectedAt, DEFAULT_WORD_ORDER);
    }

    /**
     * 带字序的版本。{@code wordOrder} 为空或非法时按 {@code ABCD} 处理；
     * 正规链路会先在 {@code ModbusConnectionConfig} 校验并拒绝非法值。
     */
    public static PointValue toPointValue(DevicePointTable point, int[] registers,
                                          LocalDateTime collectedAt, String wordOrder) {
        if (point == null) {
            return PointValue.failed(null, null, null, "点位为空");
        }
        int width = registerWidth(point.getDataType());
        if (registers == null || registers.length < width) {
            return PointValue.failed(point.getId(), point.getDeviceId(), point.getPointKey(),
                    "寄存器数量不足：需要 " + width + " 个，实际 "
                            + (registers == null ? 0 : registers.length));
        }
        String raw;
        try {
            raw = rawValue(point.getDataType(), registers, wordOrder);
        } catch (Exception e) {
            return PointValue.failed(point.getId(), point.getDeviceId(), point.getPointKey(),
                    "原始值解析失败: " + message(e));
        }
        String value;
        try {
            value = engineeringValue(point, raw);
        } catch (Exception e) {
            return PointValue.failed(point.getId(), point.getDeviceId(), point.getPointKey(),
                    "工程值换算失败: " + message(e));
        }
        return new PointValue(point.getId(), point.getDeviceId(), point.getPointKey(),
                true, raw, value, null, collectedAt);
    }

    /** 原始值文本化（默认大端字序）。 */
    public static String rawValue(String dataType, int[] registers) {
        return rawValue(dataType, registers, DEFAULT_WORD_ORDER);
    }

    /** 原始值文本化；32 / 64 位数值按 {@code wordOrder} 重新排列字节。 */
    public static String rawValue(String dataType, int[] registers, String wordOrder) {
        String type = normalize(dataType);
        return switch (type) {
            case "bool" -> registers[0] != 0 ? "true" : "false";
            case "int" -> String.valueOf((short) registers[0]);
            case "int32" -> String.valueOf(toInt32(orderedBytes(registers, 2, wordOrder)));
            case "uint32" -> Long.toString(toUint32(orderedBytes(registers, 2, wordOrder)));
            case "int64" -> String.valueOf(toInt64(orderedBytes(registers, 4, wordOrder)));
            case "uint64" -> Long.toUnsignedString(toInt64(orderedBytes(registers, 4, wordOrder)));
            case "float" -> String.valueOf(Float.intBitsToFloat(
                    toInt32(orderedBytes(registers, 2, wordOrder))));
            case "double" -> String.valueOf(Double.longBitsToDouble(
                    toInt64(orderedBytes(registers, 4, wordOrder))));
            case "string" -> decodeString(registers[0]);
            default -> throw new IllegalArgumentException("不支持的数据类型: " + dataType);
        };
    }

    /** 工程值 = 原始值 * scale + offset；bool / string 原样返回。 */
    public static String engineeringValue(DevicePointTable point, String raw) {
        String type = normalize(point.getDataType());
        if ("bool".equals(type) || "string".equals(type)) {
            return raw;
        }
        BigDecimal rawNumber = new BigDecimal(raw);
        BigDecimal scale = point.getScale() == null ? BigDecimal.valueOf(DEFAULT_SCALE) : point.getScale();
        BigDecimal offset = point.getOffset() == null ? BigDecimal.ZERO : point.getOffset();
        BigDecimal value = rawNumber.multiply(scale).add(offset);
        // 去掉无意义的尾零，避免 25.0 与 25 在属性历史里来回跳
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0, RoundingMode.UNNECESSARY);
        }
        return normalized.toPlainString();
    }

    /**
     * 把寄存器按大端展开为字节，再按字序重排。
     * ABCD 原样；CDAB 交换每个 32 位字内的两个 16 位字；
     * BADC 交换每个 16 位字内的两个字节；DCBA 完整反转（小端）。
     */
    private static byte[] orderedBytes(int[] registers, int width, String wordOrder) {
        byte[] bytes = new byte[width * 2];
        for (int i = 0; i < width; i++) {
            bytes[i * 2] = (byte) ((registers[i] >>> 8) & 0xFF);
            bytes[i * 2 + 1] = (byte) (registers[i] & 0xFF);
        }
        String order = normalizeWordOrder(wordOrder);
        if (DEFAULT_WORD_ORDER.equals(order)) {
            return bytes;
        }
        byte[] ordered = new byte[bytes.length];
        if ("DCBA".equals(order)) {
            for (int i = 0; i < bytes.length; i++) {
                ordered[i] = bytes[bytes.length - 1 - i];
            }
            return ordered;
        }
        for (int base = 0; base < bytes.length; base += 4) {
            if ("CDAB".equals(order)) {
                ordered[base] = bytes[base + 2];
                ordered[base + 1] = bytes[base + 3];
                ordered[base + 2] = bytes[base];
                ordered[base + 3] = bytes[base + 1];
            } else {
                ordered[base] = bytes[base + 1];
                ordered[base + 1] = bytes[base];
                ordered[base + 2] = bytes[base + 3];
                ordered[base + 3] = bytes[base + 2];
            }
        }
        return ordered;
    }

    private static int toInt32(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private static long toUint32(byte[] bytes) {
        return ((long) (bytes[0] & 0xFF) << 24)
                | ((long) (bytes[1] & 0xFF) << 16)
                | ((long) (bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private static long toInt64(byte[] bytes) {
        long bits = 0;
        for (byte value : bytes) {
            bits = (bits << 8) | (value & 0xFF);
        }
        return bits;
    }

    private static String normalizeWordOrder(String wordOrder) {
        if (wordOrder == null || wordOrder.isBlank()) {
            return DEFAULT_WORD_ORDER;
        }
        String normalized = wordOrder.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ABCD", "CDAB", "BADC", "DCBA" -> normalized;
            default -> DEFAULT_WORD_ORDER;
        };
    }

    private static String decodeString(int register) {
        byte[] bytes = {(byte) ((register >>> 8) & 0xFF), (byte) (register & 0xFF)};
        int length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0) {
            length--;
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII).trim();
    }

    private static String normalize(String dataType) {
        return dataType == null ? "" : dataType.trim().toLowerCase(Locale.ROOT);
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
