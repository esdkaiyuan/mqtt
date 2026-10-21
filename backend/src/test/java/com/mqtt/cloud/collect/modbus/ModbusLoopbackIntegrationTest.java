package com.mqtt.cloud.collect.modbus;

import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import com.mqtt.cloud.collect.CollectRequest;
import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.config.ModbusCollectProperties;
import com.mqtt.cloud.entity.DevicePointTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modbus TCP 回环集成测试（T-31 P4）。
 * <p>
 * 用 j2mod 自带从站起一个真实 TCP 服务，让采集器走真实 socket 读寄存器，
 * 验证「建连 → 批量读 → 换算 → 关闭」整条链路，而不仅是 Mockito 桩。
 * 真实现场从站 / 网关仍需部署后实测。
 */
class ModbusLoopbackIntegrationTest {

    private ModbusSlave slave;

    @AfterEach
    void tearDown() {
        if (slave != null) {
            ModbusSlaveFactory.close(slave);
            slave = null;
        }
    }

    @Test
    void collector_should_read_real_tcp_slave() throws Exception {
        int port = freePort();
        SimpleProcessImage image = new SimpleProcessImage(1);
        image.addRegister(new SimpleRegister(123));
        image.addRegister(new SimpleRegister(456));
        slave = ModbusSlaveFactory.createTCPSlave(port, 2);
        slave.addProcessImage(1, image);
        slave.open();

        ModbusCollectProperties properties = new ModbusCollectProperties();
        ModbusMasterRegistry registry = new ModbusMasterRegistry(properties);
        ModbusPointCollector collector = new ModbusPointCollector(registry, properties, new ObjectMapper());

        List<PointValue> values = collector.collect(new CollectRequest(
                List.of(point(1L, "0"), point(2L, "1")),
                "{\"host\":\"127.0.0.1\",\"port\":" + port + ",\"unitId\":1}",
                Duration.ofMillis(1000), Duration.ofMillis(1000)));

        assertThat(values).hasSize(2);
        assertThat(values).allMatch(PointValue::success);
        assertThat(values).extracting(PointValue::value).containsExactly("123", "456");
        assertThat(registry.activeCount()).isZero();
    }

    @Test
    void collector_should_read_multiple_units_on_one_tcp_connection() throws Exception {
        int port = freePort();
        SimpleProcessImage unit1 = new SimpleProcessImage(1);
        unit1.addRegister(new SimpleRegister(123));
        SimpleProcessImage unit2 = new SimpleProcessImage(1);
        unit2.addRegister(new SimpleRegister(789));
        slave = ModbusSlaveFactory.createTCPSlave(port, 2);
        slave.addProcessImage(1, unit1);
        slave.addProcessImage(2, unit2);
        slave.open();

        ModbusCollectProperties properties = new ModbusCollectProperties();
        ModbusMasterRegistry registry = new ModbusMasterRegistry(properties);
        ModbusPointCollector collector = new ModbusPointCollector(registry, properties, new ObjectMapper());
        DevicePointTable unit2Point = point(2L, "0");
        unit2Point.setUnitId(2);

        List<PointValue> values = collector.collect(new CollectRequest(
                List.of(point(1L, "0"), unit2Point),
                "{\"host\":\"127.0.0.1\",\"port\":" + port + ",\"unitId\":1}",
                Duration.ofMillis(1000), Duration.ofMillis(1000)));

        assertThat(values).allMatch(PointValue::success);
        assertThat(values).extracting(PointValue::value).containsExactly("123", "789");
        assertThat(registry.activeCount()).isZero();
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private DevicePointTable point(Long id, String address) {
        DevicePointTable point = new DevicePointTable();
        point.setId(id);
        point.setDeviceId(9L);
        point.setPointKey("p" + id);
        point.setPointType(PointTypeValue.HOLDING_REGISTER);
        point.setAddress(address);
        point.setDataType("int");
        return point;
    }
}
