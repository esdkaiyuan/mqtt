package com.mqtt.cloud.collect.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.BitVector;
import com.mqtt.cloud.collect.CollectRequest;
import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.config.ModbusCollectProperties;
import com.mqtt.cloud.entity.DevicePointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Modbus 采集器单测（T-31 P4）。
 * <p>
 * 用 mock 的 Modbus 主站覆盖协议交互，无需真机；重点验证「合并读 → 逐点换算」、
 * 「合并失败降级逐点」、「连接 / 读取异常不外抛」与「finally 必关连接」。
 */
class ModbusPointCollectorTest {

    private ModbusMasterRegistry registry;
    private ModbusCollectProperties properties;
    private ModbusPointCollector collector;
    private ModbusTCPMaster master;

    @BeforeEach
    void setUp() {
        registry = mock(ModbusMasterRegistry.class);
        properties = new ModbusCollectProperties();
        properties.setDefaultHost("");
        properties.setDefaultPort(502);
        properties.setDefaultUnitId(1);
        properties.setRetry(1);
        collector = new ModbusPointCollector(registry, properties, new ObjectMapper());
        master = mock(ModbusTCPMaster.class);
    }

    @Test
    void driver_id_should_be_modbus_tcp() {
        assertThat(collector.driverId()).isEqualTo("modbus-tcp");
    }

    @Test
    void collect_should_read_and_convert_holding_register() throws Exception {
        when(registry.open(any())).thenReturn(master);
        when(master.readMultipleRegisters(1, 0, 1))
                .thenReturn(new Register[]{new SimpleRegister(26)});

        List<PointValue> values = collector.collect(request(
                List.of(point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int")),
                "{\"host\":\"127.0.0.1\"}"));

        assertThat(values).hasSize(1);
        assertThat(values.get(0).success()).isTrue();
        assertThat(values.get(0).value()).isEqualTo("26");
        verify(registry).close(master);
    }

    @Test
    void contiguous_points_should_use_one_batch_read() throws Exception {
        when(registry.open(any())).thenReturn(master);
        when(master.readMultipleRegisters(1, 0, 2))
                .thenReturn(new Register[]{new SimpleRegister(10), new SimpleRegister(20)});

        List<PointValue> values = collector.collect(request(List.of(
                point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int"),
                point(2L, PointTypeValue.HOLDING_REGISTER, "1", "int")), "{\"host\":\"127.0.0.1\"}"));

        assertThat(values).extracting(PointValue::value).containsExactly("10", "20");
        verify(master).readMultipleRegisters(1, 0, 2);
        verify(master, never()).readMultipleRegisters(1, 0, 1);
    }

    @Test
    void batch_failure_should_degrade_to_single_reads() throws Exception {
        when(registry.open(any())).thenReturn(master);
        when(master.readMultipleRegisters(1, 0, 2)).thenThrow(new RuntimeException("batch failed"));
        when(master.readMultipleRegisters(1, 0, 1)).thenReturn(new Register[]{new SimpleRegister(11)});
        when(master.readMultipleRegisters(1, 1, 1)).thenReturn(new Register[]{new SimpleRegister(22)});

        List<PointValue> values = collector.collect(request(List.of(
                point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int"),
                point(2L, PointTypeValue.HOLDING_REGISTER, "1", "int")), "{\"host\":\"127.0.0.1\"}"));

        assertThat(values).extracting(PointValue::value).containsExactly("11", "22");
        verify(master).readMultipleRegisters(1, 0, 1);
        verify(master).readMultipleRegisters(1, 1, 1);
    }

    @Test
    void coil_should_be_read_as_bit_and_converted_to_bool() throws Exception {
        when(registry.open(any())).thenReturn(master);
        BitVector bits = new BitVector(1);
        bits.setBit(0, true);
        when(master.readCoils(1, 5, 1)).thenReturn(bits);

        List<PointValue> values = collector.collect(request(
                List.of(point(1L, PointTypeValue.COIL, "5", "bool")),
                "{\"host\":\"127.0.0.1\"}"));

        assertThat(values.get(0).value()).isEqualTo("true");
    }

    @Test
    void float_should_read_two_registers() throws Exception {
        when(registry.open(any())).thenReturn(master);
        int bits = Float.floatToIntBits(12.5f);
        when(master.readMultipleRegisters(1, 0, 2)).thenReturn(new Register[]{
                new SimpleRegister((bits >>> 16) & 0xFFFF),
                new SimpleRegister(bits & 0xFFFF)});

        List<PointValue> values = collector.collect(request(
                List.of(point(1L, PointTypeValue.HOLDING_REGISTER, "0", "float")),
                "{\"host\":\"127.0.0.1\"}"));

        assertThat(values.get(0).value()).isEqualTo("12.5");
    }

    @Test
    void multi_unit_points_should_read_separately_on_one_connection() throws Exception {
        when(registry.open(any())).thenReturn(master);
        when(master.readMultipleRegisters(2, 0, 1)).thenReturn(new Register[]{new SimpleRegister(11)});
        when(master.readMultipleRegisters(3, 0, 1)).thenReturn(new Register[]{new SimpleRegister(22)});
        DevicePointTable unit2 = point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int");
        unit2.setUnitId(2);
        DevicePointTable unit3 = point(2L, PointTypeValue.HOLDING_REGISTER, "0", "int");
        unit3.setUnitId(3);

        List<PointValue> values = collector.collect(request(List.of(unit2, unit3),
                "{\"host\":\"127.0.0.1\",\"unitId\":1}"));

        assertThat(values).extracting(PointValue::value).containsExactly("11", "22");
        verify(master).readMultipleRegisters(2, 0, 1);
        verify(master).readMultipleRegisters(3, 0, 1);
        verify(master, never()).readMultipleRegisters(1, 0, 1);
        verify(registry).close(master);
    }

    @Test
    void invalid_unit_id_should_fail_point_without_opening_connection() throws Exception {
        DevicePointTable invalid = point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int");
        invalid.setUnitId(248);

        List<PointValue> values = collector.collect(request(List.of(invalid),
                "{\"host\":\"127.0.0.1\"}"));

        assertThat(values).hasSize(1);
        assertThat(values.get(0).success()).isFalse();
        assertThat(values.get(0).errorMessage()).contains("从站地址非法");
        verify(registry, never()).open(any());
    }

    @Test
    void connection_failure_should_not_throw_and_should_fail_all_points() throws Exception {
        when(registry.open(any())).thenThrow(new IOException("refused"));

        List<PointValue> values = collector.collect(request(List.of(
                point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int"),
                point(2L, PointTypeValue.HOLDING_REGISTER, "1", "int")), "{\"host\":\"127.0.0.1\"}"));

        assertThat(values).hasSize(2).allMatch(value -> !value.success());
        assertThat(values.get(0).errorMessage()).contains("连接");
        verify(registry, never()).close(any());
    }

    @Test
    void read_exception_should_still_close_connection() throws Exception {
        when(registry.open(any())).thenReturn(master);
        when(master.readMultipleRegisters(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("read failed"));

        assertThatCode(() -> collector.collect(request(
                List.of(point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int")),
                "{\"host\":\"127.0.0.1\"}"))).doesNotThrowAnyException();

        verify(registry).close(master);
    }

    @Test
    void invalid_driver_config_should_fail_without_opening_connection() throws Exception {
        List<PointValue> values = collector.collect(request(
                List.of(point(1L, PointTypeValue.HOLDING_REGISTER, "0", "int")), "{}"));

        assertThat(values.get(0).success()).isFalse();
        assertThat(values.get(0).errorMessage()).contains("host");
        verify(registry, never()).open(any());
    }

    @Test
    void tag_point_should_fail_without_reading() throws Exception {
        List<PointValue> values = collector.collect(request(
                List.of(point(1L, PointTypeValue.TAG, "Line1.Temp", "float")),
                "{\"host\":\"127.0.0.1\"}"));

        assertThat(values.get(0).success()).isFalse();
        assertThat(values.get(0).errorMessage()).contains("不支持 Modbus");
        verify(registry, never()).open(any());
    }

    private CollectRequest request(List<DevicePointTable> points, String driverConfig) {
        return new CollectRequest(points, driverConfig, Duration.ofMillis(500), Duration.ofMillis(500));
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
