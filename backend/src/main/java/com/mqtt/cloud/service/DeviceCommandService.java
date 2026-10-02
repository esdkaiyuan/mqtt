package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.entity.DeviceCommandRecord;

import java.util.List;

/**
 * 命令下发与服务调用入口（T-15）。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 * <p>
 * 语义：一次 {@link #invoke(CommandInvoke)} 对应 {@code device_command_record} 中的一行，
 * 状态流转 {@code PENDING → SENT → ACKED/FAILED/TIMEOUT}；回执关联键为下行载荷的 {@code id}
 * （即记录的 {@code commandId}），由 {@code CommandReplyService} 在摄取落库后旁路更新。
 */
public interface DeviceCommandService {

    String TYPE_PROPERTY_SET = "property_set";
    String TYPE_SERVICE = "service";

    String CALL_TYPE_SYNC = "sync";
    String CALL_TYPE_ASYNC = "async";

    String SOURCE_CONSOLE = "CONSOLE";
    String SOURCE_OPEN_API = "OPEN_API";

    /** 可下发能力：供前端生成动态表单；无物模型时 {@code version=0} 且两个集合为空。 */
    CommandCapability getCapability(Long productId);

    /**
     * 下发命令：按物模型校验参数 → 落库 {@code PENDING} → 发布 {@code device/{deviceKey}/cmd/down}。
     * <p>
     * 发布成功置 {@code SENT}；发布异常置 {@code FAILED} 并抛 {@code 4001}。
     * {@code callType=sync} 时等待回执到终态（超时置 {@code TIMEOUT}）再返回。
     */
    DeviceCommandRecord invoke(CommandInvoke command);

    /** 命令记录分页，按创建时间倒序。 */
    IPage<DeviceCommandRecord> listCommands(Long deviceId, long page, long size);

    /** 超时巡检兜底：把长期停留 {@code PENDING/SENT} 的记录置 {@code TIMEOUT}，返回受影响行数。 */
    int sweepTimeouts();

    /**
     * 下发入参。
     *
     * @param paramsJson 请求参数原文（JSON 对象文本）
     */
    record CommandInvoke(Long deviceId,
                         Long productId,
                         String commandType,
                         String identifier,
                         String paramsJson,
                         String callType,
                         String source,
                         Long operatorId) {
    }

    /**
     * 可下发能力投影。
     *
     * @param version    物模型版本；0 表示未建模
     * @param properties 可写（{@code accessMode=rw}）属性
     * @param services   已定义服务（含入参）
     */
    record CommandCapability(int version,
                             List<ThingModelDefinition.PropertySpec> properties,
                             List<ThingModelDefinition.ServiceSpec> services) {

        public boolean modeled() {
            return version > 0;
        }
    }
}