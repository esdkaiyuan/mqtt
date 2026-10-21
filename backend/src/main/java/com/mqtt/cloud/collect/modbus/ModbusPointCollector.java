package com.mqtt.cloud.collect.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.util.BitVector;
import com.mqtt.cloud.collect.CollectRequest;
import com.mqtt.cloud.collect.PointCollector;
import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.config.ModbusCollectProperties;
import com.mqtt.cloud.entity.DevicePointTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modbus TCP 点位采集器（T-31 P4）。
 * <p>
 * 一次 {@link #collect} 对应「一台设备、一批到期点位」：建一次连接、按功能码合并读、
 * 逐点换算、finally 断开。任何协议异常都被转成点位失败结果，<b>绝不向上抛</b>——
 * 采集是轮询作业，抛出异常会让同批其它设备一起停摆。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModbusPointCollector implements PointCollector {

    public static final String DRIVER_ID = "modbus-tcp";
    private static final int MAX_ERROR_LENGTH = 200;

    private final ModbusMasterRegistry masterRegistry;
    private final ModbusCollectProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String driverId() {
        return DRIVER_ID;
    }

    @Override
    public List<PointValue> collect(CollectRequest request) {
        if (request == null || request.points().isEmpty()) {
            return List.of();
        }
        LocalDateTime collectedAt = LocalDateTime.now();
        Map<Long, PointValue> results = new LinkedHashMap<>();
        List<DevicePointTable> supported = new ArrayList<>();
        for (DevicePointTable point : request.points()) {
            String type = PointTypeValue.normalize(point.getPointType());
            if (!isModbusPointType(type)) {
                results.put(point.getId(), PointValue.failed(point.getId(), point.getDeviceId(),
                        point.getPointKey(), "点位类型 " + type + " 不支持 Modbus 采集"));
                continue;
            }
            if (ModbusPointDispatch.parseAddress(point.getAddress()) == null) {
                results.put(point.getId(), PointValue.failed(point.getId(), point.getDeviceId(),
                        point.getPointKey(), "寄存器 / 线圈地址非法: " + point.getAddress()));
                continue;
            }
            if (point.getUnitId() != null && (point.getUnitId() < 0
                    || point.getUnitId() > ModbusPointDispatch.MAX_UNIT_ID)) {
                results.put(point.getId(), PointValue.failed(point.getId(), point.getDeviceId(),
                        point.getPointKey(), "从站地址非法（须在 0~247）: " + point.getUnitId()));
                continue;
            }
            supported.add(point);
        }
        if (supported.isEmpty()) {
            return ordered(request.points(), results);
        }

        ModbusConnectionConfig config;
        try {
            config = ModbusConnectionConfig.parse(request.driverConfig(),
                    properties.getDefaultHost(), properties.getDefaultPort(), properties.getDefaultUnitId(),
                    request.connectTimeout(), request.readTimeout(), properties.getRetry(), objectMapper);
        } catch (Exception e) {
            failAll(supported, results, "驱动配置非法: " + message(e));
            return ordered(request.points(), results);
        }

        ModbusTCPMaster master = null;
        try {
            master = masterRegistry.open(config);
            for (ModbusPointDispatch.ReadGroup group : ModbusPointDispatch.plan(supported, config.unitId())) {
                readGroup(master, config.wordOrder(), group, results, collectedAt);
            }
            for (DevicePointTable point : supported) {
                results.putIfAbsent(point.getId(), PointValue.failed(point.getId(), point.getDeviceId(),
                        point.getPointKey(), "驱动未返回该点位结果"));
            }
        } catch (Exception e) {
            failAll(supported, results, "Modbus 连接 / 读取失败: " + message(e));
        } finally {
            if (master != null) {
                masterRegistry.close(master);
            }
        }
        return ordered(request.points(), results);
    }

    private void readGroup(ModbusTCPMaster master, String wordOrder,
                           ModbusPointDispatch.ReadGroup group,
                           Map<Long, PointValue> results, LocalDateTime collectedAt) {
        int unitId = group.unitId();
        try {
            int[] raw = readRange(master, unitId, group.pointType(), group.start(), group.count());
            for (DevicePointTable point : group.points()) {
                int start = ModbusPointDispatch.parseAddress(point.getAddress());
                int width = PointTypeValue.isSingleBit(group.pointType())
                        ? 1 : ModbusValueConverter.registerWidth(point.getDataType());
                int offset = start - group.start();
                if (offset < 0 || offset + width > raw.length) {
                    results.put(point.getId(), PointValue.failed(point.getId(), point.getDeviceId(),
                            point.getPointKey(), "合并读取区间不足（offset=" + offset + "）"));
                    continue;
                }
                int[] slice = Arrays.copyOfRange(raw, offset, offset + width);
                results.put(point.getId(), ModbusValueConverter.toPointValue(point, slice, collectedAt,
                        wordOrder));
            }
        } catch (Exception batchError) {
            // 合并读失败自动降级为逐点读：一个坏地址不应拖垮同组其它点位
            log.debug("Modbus 合并读取失败，降级为逐点读: type={}, start={}, count={}, error={}",
                    group.pointType(), group.start(), group.count(), message(batchError));
            for (DevicePointTable point : group.points()) {
                readSingle(master, unitId, wordOrder, point, results, collectedAt);
            }
        }
    }

    private void readSingle(ModbusTCPMaster master, int unitId, String wordOrder,
                            DevicePointTable point,
                            Map<Long, PointValue> results, LocalDateTime collectedAt) {
        try {
            int start = ModbusPointDispatch.parseAddress(point.getAddress());
            int width = PointTypeValue.isSingleBit(point.getPointType())
                    ? 1 : ModbusValueConverter.registerWidth(point.getDataType());
            int[] raw = readRange(master, unitId, PointTypeValue.normalize(point.getPointType()), start, width);
            results.put(point.getId(), ModbusValueConverter.toPointValue(point, raw, collectedAt, wordOrder));
        } catch (Exception e) {
            results.put(point.getId(), PointValue.failed(point.getId(), point.getDeviceId(),
                    point.getPointKey(), "读取失败: " + message(e)));
        }
    }

    private int[] readRange(ModbusTCPMaster master, int unitId, String pointType,
                            int start, int count) throws Exception {
        return switch (pointType) {
            case PointTypeValue.HOLDING_REGISTER -> {
                Register[] registers = master.readMultipleRegisters(unitId, start, count);
                yield unsigned(registers);
            }
            case PointTypeValue.INPUT_REGISTER -> {
                InputRegister[] registers = master.readInputRegisters(unitId, start, count);
                yield unsigned(registers);
            }
            case PointTypeValue.COIL -> {
                BitVector bits = master.readCoils(unitId, start, count);
                yield bits(bits, count);
            }
            case PointTypeValue.DISCRETE_INPUT -> {
                BitVector bits = master.readInputDiscretes(unitId, start, count);
                yield bits(bits, count);
            }
            default -> throw new IllegalArgumentException("不支持的点位类型: " + pointType);
        };
    }

    private int[] unsigned(Register[] registers) {
        int[] result = new int[registers.length];
        for (int i = 0; i < registers.length; i++) {
            result[i] = registers[i].toUnsignedShort();
        }
        return result;
    }

    private int[] unsigned(InputRegister[] registers) {
        int[] result = new int[registers.length];
        for (int i = 0; i < registers.length; i++) {
            result[i] = registers[i].toUnsignedShort();
        }
        return result;
    }

    private int[] bits(BitVector vector, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = vector.getBit(i) ? 1 : 0;
        }
        return result;
    }

    private boolean isModbusPointType(String type) {
        return switch (type) {
            case PointTypeValue.HOLDING_REGISTER, PointTypeValue.INPUT_REGISTER,
                 PointTypeValue.COIL, PointTypeValue.DISCRETE_INPUT -> true;
            default -> false;
        };
    }

    private void failAll(List<DevicePointTable> points, Map<Long, PointValue> results, String reason) {
        for (DevicePointTable point : points) {
            results.put(point.getId(), PointValue.failed(point.getId(), point.getDeviceId(),
                    point.getPointKey(), reason));
        }
    }

    private List<PointValue> ordered(List<DevicePointTable> points, Map<Long, PointValue> results) {
        List<PointValue> ordered = new ArrayList<>(points.size());
        for (DevicePointTable point : points) {
            PointValue value = results.get(point.getId());
            if (value == null) {
                value = PointValue.failed(point.getId(), point.getDeviceId(), point.getPointKey(),
                        "未产生采集结果");
            }
            ordered.add(value);
        }
        return ordered;
    }

    private String message(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
