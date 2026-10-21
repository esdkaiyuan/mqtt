package com.mqtt.cloud.collect.modbus;

import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.entity.DevicePointTable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modbus 换算口径单测（T-31 P4）。
 * <p>
 * 换算错不会报错，只会让现场屏幕与平台显示两个数；因此这里把每个 dataType、
 * 有无 scale/offset 的组合都钉死。
 */
class ModbusValueConverterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);

    @Test
    void int_should_be_signed_16_bit() {
        DevicePointTable point = point("int", null, null);

        PointValue value = ModbusValueConverter.toPointValue(point, new int[]{0xFFF4}, NOW);

        assertThat(value.success()).isTrue();
        assertThat(value.rawValue()).isEqualTo("-12");
        assertThat(value.value()).isEqualTo("-12");
    }

    @Test
    void int_should_apply_scale_and_offset() {
        DevicePointTable point = point("int", "0.1", "5");

        PointValue value = ModbusValueConverter.toPointValue(point, new int[]{10}, NOW);

        assertThat(value.value()).isEqualTo("6");
    }

    @Test
    void float_should_use_two_registers_big_endian() {
        DevicePointTable point = point("float", null, null);
        int bits = Float.floatToIntBits(25.5f);
        int[] registers = {(bits >>> 16) & 0xFFFF, bits & 0xFFFF};

        PointValue value = ModbusValueConverter.toPointValue(point, registers, NOW);

        assertThat(value.success()).isTrue();
        assertThat(value.rawValue()).isEqualTo("25.5");
        assertThat(value.value()).isEqualTo("25.5");
    }

    @Test
    void double_should_use_four_registers_big_endian() {
        DevicePointTable point = point("double", null, null);
        long bits = Double.doubleToLongBits(3.14159);
        int[] registers = {
                (int) ((bits >>> 48) & 0xFFFF),
                (int) ((bits >>> 32) & 0xFFFF),
                (int) ((bits >>> 16) & 0xFFFF),
                (int) (bits & 0xFFFF)
        };

        PointValue value = ModbusValueConverter.toPointValue(point, registers, NOW);

        assertThat(value.success()).isTrue();
        assertThat(Double.parseDouble(value.value())).isEqualTo(3.14159);
    }

    @Test
    void int32_should_use_two_registers_big_endian() {
        PointValue value = ModbusValueConverter.toPointValue(point("int32", null, null),
                new int[]{0x0001, 0x0002}, NOW);

        assertThat(value.rawValue()).isEqualTo("65538");
    }

    @Test
    void uint32_should_not_be_negative() {
        PointValue value = ModbusValueConverter.toPointValue(point("uint32", null, null),
                new int[]{0xFFFF, 0xFFFF}, NOW);

        assertThat(value.rawValue()).isEqualTo("4294967295");
    }

    @Test
    void float_should_apply_cdab_word_order() {
        // 大端 25.5 = 0x41CC0000；CDAB 输入时寄存器顺序为 [0x0000, 0x41CC]
        PointValue value = ModbusValueConverter.toPointValue(point("float", null, null),
                new int[]{0x0000, 0x41CC}, NOW, "CDAB");

        assertThat(value.value()).isEqualTo("25.5");
    }

    @Test
    void int32_should_apply_cdab_word_order() {
        PointValue value = ModbusValueConverter.toPointValue(point("int32", null, null),
                new int[]{0x0001, 0x0002}, NOW, "CDAB");

        assertThat(value.rawValue()).isEqualTo("131073");
    }

    @Test
    void uint32_should_apply_dcba_word_order() {
        PointValue value = ModbusValueConverter.toPointValue(point("uint32", null, null),
                new int[]{0x0001, 0x0002}, NOW, "DCBA");

        assertThat(value.rawValue()).isEqualTo("33554688");
    }

    @Test
    void double_should_apply_word_order() {
        long bits = Double.doubleToLongBits(3.14159);
        int[] bigEndian = {
                (int) ((bits >>> 48) & 0xFFFF),
                (int) ((bits >>> 32) & 0xFFFF),
                (int) ((bits >>> 16) & 0xFFFF),
                (int) (bits & 0xFFFF)
        };
        // DCBA 输入是完整小端字节序：把大端字节反转后按寄存器（大端字节）重新打包
        byte[] beBytes = new byte[8];
        for (int i = 0; i < 4; i++) {
            beBytes[i * 2] = (byte) ((bigEndian[i] >>> 8) & 0xFF);
            beBytes[i * 2 + 1] = (byte) (bigEndian[i] & 0xFF);
        }
        byte[] leBytes = new byte[8];
        for (int i = 0; i < 8; i++) leBytes[i] = beBytes[7 - i];
        int[] littleEndianRegisters = new int[4];
        for (int i = 0; i < 4; i++) {
            littleEndianRegisters[i] = ((leBytes[i * 2] & 0xFF) << 8) | (leBytes[i * 2 + 1] & 0xFF);
        }

        PointValue value = ModbusValueConverter.toPointValue(point("double", null, null),
                littleEndianRegisters, NOW, "DCBA");

        assertThat(Double.parseDouble(value.value())).isEqualTo(3.14159);
    }

    @Test
    void bool_should_map_nonzero_to_true() {
        assertThat(ModbusValueConverter.toPointValue(point("bool", null, null),
                new int[]{1}, NOW).value()).isEqualTo("true");
        assertThat(ModbusValueConverter.toPointValue(point("bool", null, null),
                new int[]{0}, NOW).value()).isEqualTo("false");
        assertThat(ModbusValueConverter.toPointValue(point("bool", null, null),
                new int[]{7}, NOW).value()).isEqualTo("true");
    }

    @Test
    void string_should_decode_ascii_and_trim_nul() {
        assertThat(ModbusValueConverter.toPointValue(point("string", null, null),
                new int[]{0x4142}, NOW).value()).isEqualTo("AB");
        assertThat(ModbusValueConverter.toPointValue(point("string", null, null),
                new int[]{0x4100}, NOW).value()).isEqualTo("A");
    }

    @Test
    void string_should_not_apply_scale() {
        DevicePointTable point = point("string", "10", "1");

        assertThat(ModbusValueConverter.toPointValue(point, new int[]{0x4142}, NOW).value())
                .isEqualTo("AB");
    }

    @Test
    void insufficient_registers_should_fail() {
        PointValue value = ModbusValueConverter.toPointValue(point("float", null, null),
                new int[]{1}, NOW);

        assertThat(value.success()).isFalse();
        assertThat(value.errorMessage()).contains("寄存器数量不足");
    }

    @Test
    void non_finite_float_should_fail_instead_of_leaking_nan() {
        DevicePointTable point = point("float", null, null);
        int bits = Float.floatToIntBits(Float.NaN);

        PointValue value = ModbusValueConverter.toPointValue(point,
                new int[]{(bits >>> 16) & 0xFFFF, bits & 0xFFFF}, NOW);

        assertThat(value.success()).isFalse();
        assertThat(value.errorMessage()).contains("工程值换算失败");
    }

    @Test
    void trailing_zeros_should_be_stripped() {
        DevicePointTable point = point("int", "1.0", "0.0");

        assertThat(ModbusValueConverter.toPointValue(point, new int[]{25}, NOW).value())
                .isEqualTo("25");
    }

    @Test
    void unknown_data_type_should_fail() {
        PointValue value = ModbusValueConverter.toPointValue(point("decimal", null, null),
                new int[]{1}, NOW);

        assertThat(value.success()).isFalse();
    }

    @Test
    void int64_should_be_signed_64_bit() {
        PointValue value = ModbusValueConverter.toPointValue(point("int64", null, null),
                registers(Long.MIN_VALUE), NOW);

        assertThat(value.success()).isTrue();
        assertThat(value.rawValue()).isEqualTo("-9223372036854775808");
    }

    @Test
    void uint64_should_not_be_negative() {
        PointValue value = ModbusValueConverter.toPointValue(point("uint64", null, null),
                registers(-1L), NOW);

        assertThat(value.rawValue()).isEqualTo("18446744073709551615");
    }

    @Test
    void int64_should_apply_word_order() {
        // 目标值 0x0000000100000002 的大端字节为 00 00 00 01 00 00 00 02；
        // DCBA 输入是它的完整字节反转，寄存器为 0x0200 / 0x0000 / 0x0100 / 0x0000
        int[] littleEndian = {0x0200, 0x0000, 0x0100, 0x0000};

        PointValue value = ModbusValueConverter.toPointValue(point("int64", null, null),
                littleEndian, NOW, "DCBA");

        assertThat(value.rawValue()).isEqualTo("4294967298");
    }

    @Test
    void register_width_should_follow_data_type() {
        assertThat(ModbusValueConverter.registerWidth("int")).isEqualTo(1);
        assertThat(ModbusValueConverter.registerWidth("int32")).isEqualTo(2);
        assertThat(ModbusValueConverter.registerWidth("uint32")).isEqualTo(2);
        assertThat(ModbusValueConverter.registerWidth("int64")).isEqualTo(4);
        assertThat(ModbusValueConverter.registerWidth("uint64")).isEqualTo(4);
        assertThat(ModbusValueConverter.registerWidth("bool")).isEqualTo(1);
        assertThat(ModbusValueConverter.registerWidth("string")).isEqualTo(1);
        assertThat(ModbusValueConverter.registerWidth("float")).isEqualTo(2);
        assertThat(ModbusValueConverter.registerWidth("double")).isEqualTo(4);
    }

    private int[] registers(long value) {
        return new int[]{
                (int) ((value >>> 48) & 0xFFFF),
                (int) ((value >>> 32) & 0xFFFF),
                (int) ((value >>> 16) & 0xFFFF),
                (int) (value & 0xFFFF)
        };
    }

    private DevicePointTable point(String dataType, String scale, String offset) {
        DevicePointTable point = new DevicePointTable();
        point.setId(1L);
        point.setDeviceId(2L);
        point.setPointKey("p1");
        point.setDataType(dataType);
        point.setScale(scale == null ? null : new BigDecimal(scale));
        point.setOffset(offset == null ? null : new BigDecimal(offset));
        return point;
    }
}
