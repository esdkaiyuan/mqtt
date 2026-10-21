package com.mqtt.cloud.collect.modbus;

import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.entity.DevicePointTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modbus 批量读合并单测（T-31 P4）。
 * <p>
 * 合并错会把相邻点位的值串位，且不会报错——只能靠区间断言守住。
 */
class ModbusPointDispatchTest {

    @Test
    void contiguous_registers_should_merge_into_one_read() {
        List<ModbusPointDispatch.ReadGroup> groups = ModbusPointDispatch.plan(List.of(
                point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int"),
                point(2L, PointTypeValue.HOLDING_REGISTER, "1", "int"),
                point(3L, PointTypeValue.HOLDING_REGISTER, "2", "int")));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).start()).isZero();
        assertThat(groups.get(0).end()).isEqualTo(2);
        assertThat(groups.get(0).count()).isEqualTo(3);
        assertThat(groups.get(0).points()).hasSize(3);
    }

    @Test
    void gap_should_split_reads() {
        List<ModbusPointDispatch.ReadGroup> groups = ModbusPointDispatch.plan(List.of(
                point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int"),
                point(2L, PointTypeValue.HOLDING_REGISTER, "2", "int")));

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(ModbusPointDispatch.ReadGroup::start)
                .containsExactly(0, 2);
    }

    @Test
    void float_width_should_extend_group_range() {
        List<ModbusPointDispatch.ReadGroup> groups = ModbusPointDispatch.plan(List.of(
                point(1L, PointTypeValue.HOLDING_REGISTER, "0", "float"),
                point(2L, PointTypeValue.HOLDING_REGISTER, "2", "int")));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).start()).isZero();
        assertThat(groups.get(0).end()).isEqualTo(2);
        assertThat(groups.get(0).count()).isEqualTo(3);
    }

    @Test
    void different_function_codes_should_not_merge() {
        List<ModbusPointDispatch.ReadGroup> groups = ModbusPointDispatch.plan(List.of(
                point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int"),
                point(2L, PointTypeValue.INPUT_REGISTER, "1", "int"),
                point(3L, PointTypeValue.COIL, "2", "bool")));

        assertThat(groups).hasSize(3);
        assertThat(groups).extracting(ModbusPointDispatch.ReadGroup::pointType)
                .containsExactlyInAnyOrder(PointTypeValue.HOLDING_REGISTER,
                        PointTypeValue.INPUT_REGISTER, PointTypeValue.COIL);
    }

    @Test
    void coils_should_merge_with_bit_limit() {
        List<ModbusPointDispatch.ReadGroup> groups = ModbusPointDispatch.plan(List.of(
                point(1L, PointTypeValue.COIL, "0", "bool"),
                point(2L, PointTypeValue.COIL, "1", "bool"),
                point(3L, PointTypeValue.COIL, "2", "bool")));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).count()).isEqualTo(3);
    }

    @Test
    void register_group_should_split_at_protocol_limit() {
        List<DevicePointTable> points = java.util.stream.IntStream.range(0, 126)
                .mapToObj(i -> point((long) i + 1, PointTypeValue.HOLDING_REGISTER,
                        String.valueOf(i), "int"))
                .toList();

        List<ModbusPointDispatch.ReadGroup> groups = ModbusPointDispatch.plan(points);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).count()).isEqualTo(125);
        assertThat(groups.get(1).count()).isEqualTo(1);
    }

    @Test
    void different_units_should_never_merge_even_with_same_address() {
        DevicePointTable unit2 = point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int");
        unit2.setUnitId(2);
        DevicePointTable unit3 = point(2L, PointTypeValue.HOLDING_REGISTER, "0", "int");
        unit3.setUnitId(3);

        List<ModbusPointDispatch.ReadGroup> groups =
                ModbusPointDispatch.plan(List.of(unit2, unit3), 1);

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(ModbusPointDispatch.ReadGroup::unitId)
                .containsExactlyInAnyOrder(2, 3);
        assertThat(groups).allMatch(group -> group.start() == 0);
    }

    @Test
    void null_unit_should_inherit_default_and_merge_with_same_unit() {
        DevicePointTable inherited = point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int");
        DevicePointTable explicit = point(2L, PointTypeValue.HOLDING_REGISTER, "1", "int");
        explicit.setUnitId(1);

        List<ModbusPointDispatch.ReadGroup> groups =
                ModbusPointDispatch.plan(List.of(inherited, explicit), 1);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).unitId()).isEqualTo(1);
        assertThat(groups.get(0).points()).hasSize(2);
    }

    @Test
    void tag_and_invalid_address_should_be_ignored() {
        List<ModbusPointDispatch.ReadGroup> groups = ModbusPointDispatch.plan(List.of(
                point(1L, PointTypeValue.TAG, "Line1.Temp", "float"),
                point(2L, PointTypeValue.HOLDING_REGISTER, "-1", "int"),
                point(3L, PointTypeValue.HOLDING_REGISTER, "abc", "int"),
                point(4L, PointTypeValue.HOLDING_REGISTER, "0", "int")));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).points()).extracting(DevicePointTable::getId).containsExactly(4L);
    }

    private DevicePointTable point(Long id, String pointType, String address, String dataType) {
        DevicePointTable point = new DevicePointTable();
        point.setId(id);
        point.setDeviceId(9L);
        point.setPointKey("p" + id);
        point.setPointType(pointType);
        point.setAddress(address);
        point.setDataType(dataType);
        return point;
    }
}
