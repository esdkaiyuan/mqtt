package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.PointExecutorValue;
import com.mqtt.cloud.common.constant.PointTypeValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.PointTableUpsertRequest;
import com.mqtt.cloud.dto.response.PointTableDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePointRun;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.mapper.DevicePointRunMapper;
import com.mqtt.cloud.mapper.DevicePointTableMapper;
import com.mqtt.cloud.service.DevicePointTableService;
import com.mqtt.cloud.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 设备点位表服务实现（T-31）
 * <p>
 * 持久化细节只出现在本包（ArchUnit 规则 4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePointTableServiceImpl
        extends ServiceImpl<DevicePointTableMapper, DevicePointTable>
        implements DevicePointTableService {

    /** 采集周期默认值（毫秒），与 V18 建表默认一致 */
    private static final int DEFAULT_READ_INTERVAL_MS = 60_000;

    /** 数据类型默认值：工业量测场景以浮点为主 */
    private static final String DEFAULT_DATA_TYPE = "float";

    private final DevicePointTableMapper pointTableMapper;
    private final DevicePointRunMapper pointRunMapper;
    private final DeviceService deviceService;

    // ---------- 写入 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int savePoints(Long ownerId, Long deviceId, List<PointTableUpsertRequest> request) {
        requireOwnedDevice(ownerId, deviceId);

        if (request == null || request.isEmpty()) {
            throw new BusinessException(ResultCode.POINT_BATCH_EMPTY);
        }

        // 1) 逐条规范化 + 合法性校验（先于任何写库动作，失败不留半成品）
        List<DevicePointTable> normalized = request.stream().map(this::normalize).toList();

        // 2) 批内标识去重：同一批里出现两个相同 pointKey 时，若都走 upsert 语义
        //    后者会静默覆盖前者，调用方看到的却是「全部成功」，这类覆盖极难排查。
        Set<String> batchKeys = new HashSet<>();
        for (DevicePointTable point : normalized) {
            if (!batchKeys.add(point.getPointKey())) {
                throw new BusinessException(ResultCode.POINT_KEY_DUPLICATED);
            }
        }

        // 3) 执行方一致性（点表的核心约束）
        String executor = resolveExecutor(deviceId);
        Set<String> executors = new HashSet<>();
        if (executor != null) {
            executors.add(executor);
        }
        normalized.forEach(p -> executors.add(p.getExecutor()));
        if (executors.size() > 1) {
            log.warn("拒绝写入点表：设备 {} 的执行方不一致，既有 {} 而本批 {}",
                    deviceId, executor, executors);
            throw new BusinessException(ResultCode.POINT_EXECUTOR_MIXED);
        }

        // 4) 按标识 upsert（新增 / 更新二合一，对齐调用方「提交一份完整点表」的预期）
        int saved = 0;
        for (DevicePointTable point : normalized) {
            DevicePointTable existing = point.getId() != null
                    ? pointTableMapper.selectById(point.getId())
                    : findByPointKey(deviceId, point.getPointKey());
            if (existing != null) {
                // 保留创建时间，避免运维按创建时间排序时点位「复活」
                point.setId(existing.getId());
                point.setCreatedAt(existing.getCreatedAt());
                // 更新场景不覆盖未提交的既有值：以请求为准（调用方持有完整点表视图）
            }
            point.setDeviceId(deviceId);
            point.setUpdatedAt(LocalDateTime.now());
            if (pointTableMapper.insertOrUpdate(point)) {
                saved++;
            }
        }
        return saved;
    }

    // ---------- 查询 ----------

    @Override
    public List<PointTableDTO> listPoints(Long ownerId, Long deviceId, String executor) {
        requireOwnedDevice(ownerId, deviceId);

        List<DevicePointTable> points = (executor == null || executor.isBlank())
                ? pointTableMapper.findEnabledByDevice(deviceId)
                : pointTableMapper.findEnabledByDeviceAndExecutor(deviceId,
                        PointExecutorValue.normalize(executor));

        // 逐点回查最近采集状态：点表规模通常为十~百级（单设备），此处 N+1 可接受；
        // 若将来出现单设备上千点位，再改为一次 GROUP BY 聚合。
        return points.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePoint(Long ownerId, Long deviceId, Long pointId) {
        requireOwnedDevice(ownerId, deviceId);

        DevicePointTable existing = pointId == null ? null : pointTableMapper.selectById(pointId);
        if (existing == null || !Objects.equals(existing.getDeviceId(), deviceId)) {
            // 跨设备删除一律按「不存在」处理：不泄漏「该点位存在于另一台设备」这一事实
            throw new BusinessException(ResultCode.POINT_KEY_DUPLICATED);
        }
        pointTableMapper.deleteById(pointId);
        // 采集日志按点位保留会持续增长（每周期一行），点位删除后即为无主数据
        pointRunMapper.deleteByPointId(pointId);
    }

    @Override
    public List<DevicePointTable> listEnabledForCollect(Long deviceId, String executor) {
        if (deviceId == null) {
            return List.of();
        }
        return pointTableMapper.findEnabledByDeviceAndExecutor(deviceId,
                PointExecutorValue.normalize(executor));
    }

    @Override
    public String resolveExecutor(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        List<String> executors = pointTableMapper.findExecutorsByDevice(deviceId);
        if (executors.isEmpty()) {
            return null;
        }
        // 已有的执行方多于一种说明数据已被绕过服务层写入（如手工 SQL）；
        // 此时返回 null 而非任选其一，让调用方的「一致性校验」判定为「无既有约束」，
        // 由本批写入统一纠正，避免把脏数据扩散到更多点位。
        return executors.size() == 1 ? PointExecutorValue.normalize(executors.get(0)) : null;
    }

    // ---------- 内部 ----------

    private Device requireOwnedDevice(Long ownerId, Long deviceId) {
        Device device = deviceId == null ? null : deviceService.getById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        // 平台侧任务（ownerId 传 null）跳过归属校验；带 ownerId 的请求一律校验归属，
        // 防止跨租户读写他人设备的点表配置
        if (ownerId != null && !Objects.equals(device.getOwnerId(), ownerId)) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    private DevicePointTable findByPointKey(Long deviceId, String pointKey) {
        return pointTableMapper.findByPointKey(deviceId, pointKey);
    }

    /** 规范化并校验单条点位。校验失败一律抛业务异常，不返回「带错误标记的对象」。 */
    private DevicePointTable normalize(PointTableUpsertRequest req) {
        DevicePointTable point = new DevicePointTable();
        point.setId(req.getId());
        point.setPointKey(req.getPointKey().trim());

        String pointType = PointTypeValue.normalize(req.getPointType());
        if (!PointTypeValue.isSupported(pointType)) {
            throw new BusinessException(ResultCode.POINT_TYPE_UNSUPPORTED);
        }
        point.setPointType(pointType);

        // 地址：寄存器类必须是合法非负整数；TAG / NODE 允许任意非空标识名
        String address = req.getAddress() == null ? null : req.getAddress().trim();
        boolean addressRequired = switch (pointType) {
            case PointTypeValue.HOLDING_REGISTER, PointTypeValue.INPUT_REGISTER,
                 PointTypeValue.COIL, PointTypeValue.DISCRETE_INPUT -> true;
            default -> false;
        };
        if (addressRequired && (address == null || address.isBlank())) {
            throw new BusinessException(ResultCode.POINT_ADDRESS_REQUIRED);
        }
        if (address != null && !address.isBlank() && isRegisterType(pointType) && !isValidRegister(address)) {
            throw new BusinessException(ResultCode.POINT_ADDRESS_INVALID);
        }
        point.setAddress(address);

        Integer unitId = req.getUnitId();
        if (unitId != null && (unitId < 0 || unitId > 247)) {
            throw new BusinessException(ResultCode.POINT_UNIT_ID_INVALID);
        }
        point.setUnitId(unitId);

        String dataType = req.getDataType() == null || req.getDataType().isBlank()
                ? DEFAULT_DATA_TYPE : req.getDataType().trim();
        if (!switch (dataType) {
            case "int", "int32", "uint32", "int64", "uint64", "float", "double", "bool", "string" -> true;
            default -> false;
        }) {
            throw new BusinessException(ResultCode.POINT_DATA_TYPE_INVALID);
        }
        point.setDataType(dataType);

        // scale 为 0 会把所有工程值压成 0（除法场景外几乎总是配置错误），显式拒绝
        BigDecimal scale = req.getScale();
        if (scale != null && scale.signum() == 0) {
            throw new BusinessException(ResultCode.POINT_SCALE_INVALID);
        }
        point.setScale(scale);
        point.setOffset(req.getOffset());

        String executor = PointExecutorValue.normalize(req.getExecutor());
        if (!PointExecutorValue.isSupported(executor)) {
            throw new BusinessException(ResultCode.POINT_EXECUTOR_UNSUPPORTED);
        }
        point.setExecutor(executor);

        point.setUnit(req.getUnit());
        point.setReadIntervalMs(req.getReadIntervalMs() == null
                ? DEFAULT_READ_INTERVAL_MS : req.getReadIntervalMs());
        point.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        return point;
    }

    private static boolean isRegisterType(String pointType) {
        return switch (pointType) {
            case PointTypeValue.HOLDING_REGISTER, PointTypeValue.INPUT_REGISTER,
                 PointTypeValue.COIL, PointTypeValue.DISCRETE_INPUT -> true;
            default -> false;
        };
    }

    /** 寄存器号校验：现场寄存器地址允许 0 与 1xxx 扩展位，但不允许负数与非数字。 */
    private static boolean isValidRegister(String address) {
        for (int i = 0; i < address.length(); i++) {
            if (!Character.isDigit(address.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private PointTableDTO toDTO(DevicePointTable point) {
        DevicePointRun latest = latestRun(point.getId());
        return PointTableDTO.builder()
                .id(point.getId())
                .deviceId(point.getDeviceId())
                .pointKey(point.getPointKey())
                .pointType(point.getPointType())
                .address(point.getAddress())
                .unitId(point.getUnitId())
                .dataType(point.getDataType())
                .scale(point.getScale())
                .offset(point.getOffset())
                .unit(point.getUnit())
                .executor(point.getExecutor())
                .readIntervalMs(point.getReadIntervalMs())
                .enabled(point.getEnabled())
                .lastCollectedAt(latest == null ? null : latest.getCollectedAt())
                .lastRunStatus(latest == null ? null : latest.getStatus())
                .lastErrorMessage(latest == null ? null : latest.getErrorMessage())
                .createdAt(point.getCreatedAt())
                .updatedAt(point.getUpdatedAt())
                .build();
    }

    /**
     * 取该点位最近一条采集日志。
     * <p>
     * 这里刻意接受「小规模 N+1」：单设备点位通常为十~百级，而点位列表是管理面低频操作
     * （人工查看/配置），不是采集热路径。用一次聚合 SQL 去换这点开销反而会让代码变复杂。
     */
    private DevicePointRun latestRun(Long pointId) {
        return pointRunMapper.findLatestByPointId(pointId);
    }
}
