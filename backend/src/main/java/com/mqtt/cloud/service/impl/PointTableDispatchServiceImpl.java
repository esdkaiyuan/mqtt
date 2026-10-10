package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.collect.PointTablePayloadBuilder;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.PointDispatchConstants;
import com.mqtt.cloud.common.constant.PointExecutorValue;
import com.mqtt.cloud.common.constant.ProtocolTypeValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.response.PointDispatchResultDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DevicePointTableService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.PointTableDispatchService;
import com.mqtt.cloud.service.ProductService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 点表下发实现（T-31 P3，路线 A）。
 * <p>
 * 全流程只有三步：<b>校验 → 组装载荷 → 交给 {@link DeviceCommandService} 下发</b>。
 * 本类不碰 MQTT 客户端、不建队列、不做重试 —— 这些都是命令通道已有的能力，
 * 在这里重写一遍只会引入第二套幂等语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointTableDispatchServiceImpl implements PointTableDispatchService {

    private static final String RESULT_SENT = "SENT";
    private static final String RESULT_QUEUED = "QUEUED";
    private static final String RESULT_FAILED = "FAILED";

    private static final int DEVICE_ENABLED = 1;

    /**
     * 版本号兜底基数：取一个明显大于「人类手打数字」的值，
     * 使退化版本号与 Redis 版本号即便混用也不会出现「退化号盖掉正常号」的反向跳跃。
     */
    private static final long FALLBACK_VERSION_BASE = 1_000_000_000_000L;

    private final DeviceService deviceService;
    private final ProductService productService;
    private final DevicePointTableService pointTableService;
    private final DeviceCommandService deviceCommandService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /** Redis 不可用时的进程内版本兜底（跨副本不保证单调，见类注释与 P6 文档限制说明）。 */
    private final AtomicLong fallbackVersion = new AtomicLong(0);

    @Override
    public PointDispatchResultDTO dispatch(Long ownerId, Long deviceId) {
        Device device = requireEdgeCapableDevice(ownerId, deviceId);

        List<DevicePointTable> points = pointTableService.listEnabledForCollect(deviceId, PointExecutorValue.EDGE);

        String executor = pointTableService.resolveExecutor(deviceId);
        if (PointExecutorValue.isPlatform(executor)) {
            // 该设备点表由平台侧主站采集，下发到网关没有执行方会读它，纯属误操作。
            count(RESULT_FAILED);
            throw new BusinessException(ResultCode.POINT_DISPATCH_NOT_EDGE);
        }
        if (points.isEmpty()) {
            // 空点表在边缘侧语义是「清空所有点位」，误发的破坏性远大于不下发，宁可报错。
            count(RESULT_FAILED);
            throw new BusinessException(ResultCode.POINT_DISPATCH_NO_POINTS);
        }

        long version = nextVersion(deviceId);
        String paramsJson = buildParams(deviceId, version, points);

        DeviceCommandRecord command = deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                deviceId, device.getProductId(),
                DeviceCommandService.TYPE_SERVICE, DeviceCommandService.SERVICE_POINT_TABLE_DISPATCH,
                paramsJson, DeviceCommandService.CALL_TYPE_ASYNC,
                DeviceCommandService.SOURCE_POINT_TABLE, ownerId));

        String status = command == null ? null : command.getStatus();
        count(RESULT_QUEUED.equals(status) ? RESULT_QUEUED : RESULT_SENT);
        log.info("点表已下发: deviceId={}, version={}, points={}, status={}, commandId={}",
                deviceId, version, points.size(), status, command == null ? null : command.getCommandId());

        return PointDispatchResultDTO.builder()
                .commandId(command == null ? null : command.getCommandId())
                .status(status)
                .version(version)
                .pointCount(points.size())
                .build();
    }

    // ---------- 校验 ----------

    /**
     * 设备必须存在、启用、且接入协议支持「边缘网关代采」。
     * <p>
     * 协议判定用 {@code GATEWAY_MQTT}：只有网关类产品才存在「点表下发给网关」这件事。
     * 直连 MQTT / HTTP 的设备由设备自身上报，点表对它没有执行方；Modbus / OPC-UA 产品
     * 若要走路线 A，同样必须先被建模为网关（{@code protocol_type=GATEWAY_MQTT}）——
     * 这与 T-30 的架构判断一致：协议转换在边缘侧完成，平台不直连现场网络。
     */
    private Device requireEdgeCapableDevice(Long ownerId, Long deviceId) {
        Device device = deviceId == null ? null : deviceService.getById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (ownerId != null && !Objects.equals(device.getOwnerId(), ownerId)) {
            // 与点表读写同一口径：越权访问一律按「不存在」处理，不泄漏设备是否存在
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (device.getEnabled() == null || device.getEnabled() != DEVICE_ENABLED) {
            throw new BusinessException(ResultCode.POINT_DISPATCH_DEVICE_INVALID);
        }
        Product product = productService.requireById(device.getProductId());
        if (!isEdgeCapableProtocol(product.getProtocolType())) {
            throw new BusinessException(ResultCode.POINT_DISPATCH_DEVICE_INVALID,
                    "仅网关类产品（protocol_type=GATEWAY_MQTT）支持点表下发");
        }
        return device;
    }

    /**
     * 协议是否支持边缘下发。
     * <p>
     * <b>{@code null} 视为支持</b>：T-30 之前的产品没有 {@code protocol_type} 列，
     * 存量网关设备该列全为 {@code NULL}。若严格要求等于 {@code GATEWAY_MQTT}，
     * 上线这版代码后所有存量网关会立刻无法下发点表 —— 这是升级即破坏。
     * <p>
     * 真正的守门是执行方：{@code executor=EDGE} 本身就是「这台设备由网关代采」的显式声明，
     * 协议列只是额外的一道防误配校验，因此对未知值放行、对<b>明确声明为其它协议</b>的设备拒绝。
     */
    private boolean isEdgeCapableProtocol(String protocolType) {
        return protocolType == null || protocolType.isBlank()
                || ProtocolTypeValue.GATEWAY_MQTT.equals(protocolType);
    }

    // ---------- 版本 ----------

    /**
     * 生成单调递增的点表版本号。
     * <p>
     * 优先用 Redis {@code INCR}：跨副本天然单调，且下发是低频操作（人工点按钮或改完配置触发），
     * 每次多一次 Redis 往返完全可接受。
     * <p>
     * Redis 不可用时退化为进程内自增（基数 {@link #FALLBACK_VERSION_BASE}）。
     * <b>该退化路径只保证单副本单调</b>，多副本部署下可能出现版本号回退，
     * 后果是网关丢弃一份更新的点表（表现为「配置改了但没生效」，重新下发即可恢复，不会写坏数据）。
     * 之所以仍要提供退化而不是直接失败：Redis 短暂不可用不该让运维改不了配置。
     */
    private long nextVersion(Long deviceId) {
        String key = PointDispatchConstants.VERSION_KEY_PREFIX + deviceId;
        try {
            Long incremented = redisTemplate.opsForValue().increment(key);
            return incremented == null ? fallbackVersion() : incremented;
        } catch (Exception e) {
            log.warn("点表版本号生成降级为进程内自增（跨副本不保证单调）: deviceId={}, key={}", deviceId, key, e);
            return fallbackVersion();
        }
    }

    private long fallbackVersion() {
        return FALLBACK_VERSION_BASE + fallbackVersion.incrementAndGet();
    }

    // ---------- 载荷 ----------

    private String buildParams(Long deviceId, long version, List<DevicePointTable> points) {
        ObjectNode root = PointTablePayloadBuilder.build(objectMapper, deviceId, version,
                System.currentTimeMillis(), points.stream().map(PointTableDispatchServiceImpl::toEntry).toList());
        return root.toString();
    }

    private static PointTablePayloadBuilder.PointTableEntry toEntry(DevicePointTable point) {
        return new PointTablePayloadBuilder.PointTableEntry(
                point.getPointKey(), point.getPointType(), point.getAddress(), point.getDataType(),
                point.getScale(), point.getOffset(), point.getUnit(), point.getReadIntervalMs());
    }

    private void count(String result) {
        meterRegistry.counter(PointDispatchConstants.METRIC_DISPATCH, "result", result).increment();
    }
}
