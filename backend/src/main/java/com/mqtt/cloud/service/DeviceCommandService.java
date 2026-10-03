package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.entity.DeviceCommandRecord;

import java.util.List;

/**
 * 命令下发与服务调用入口（T-15 / T-16）。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 * <p>
 * 语义：一次 {@link #invoke(CommandInvoke)} 对应 {@code device_command_record} 中的一行，
 * 状态流转 {@code PENDING → SENT → ACKED/FAILED/TIMEOUT}；回执关联键为下行载荷的 {@code id}
 * （即记录的 {@code commandId}），由 {@code CommandReplyService} 在摄取落库后旁路更新。
 * <p>
 * T-16 扩展：设备离线且 {@code type=property_set} 时置 {@code QUEUED}（不发布），设备上线后由
 * {@link #flushQueued(Long, int)} 补发；补发失败按指数退避重试，由 {@link #sweepQueuedRetries()} 巡检兜底。
 */
public interface DeviceCommandService {

    String TYPE_PROPERTY_SET = "property_set";
    String TYPE_SERVICE = "service";

    String CALL_TYPE_SYNC = "sync";
    String CALL_TYPE_ASYNC = "async";

    String SOURCE_CONSOLE = "CONSOLE";
    String SOURCE_OPEN_API = "OPEN_API";
    /** 来源：消息规则动作（T-19）。仅用于区分来源，不改既有语义。 */
    String SOURCE_RULE = "RULE";

    /** 可下发能力：供前端生成动态表单；无物模型时 {@code version=0} 且两个集合为空。 */
    CommandCapability getCapability(Long productId);

    /**
     * 下发命令：按物模型校验参数 → 落库 {@code PENDING} → 发布 {@code device/{deviceKey}/cmd/down}。
     * <p>
     * 发布成功置 {@code SENT}；发布异常置 {@code FAILED} 并抛 {@code 4001}。
     * {@code callType=sync} 时等待回执到终态（超时置 {@code TIMEOUT}）再返回。
     * <p>
     * T-16：{@code type=property_set} 时同时写入影子 {@code desired}；设备离线则置 {@code QUEUED}（不发布、
     * 不抛错），设备上线后补发；{@code property_set} 的发布异常也转 {@code QUEUED} 退避重试（不抛错）。
     */
    DeviceCommandRecord invoke(CommandInvoke command);

    /** 命令记录分页，按创建时间倒序。 */
    IPage<DeviceCommandRecord> listCommands(Long deviceId, long page, long size);

    /** 超时巡检兜底：把长期停留 {@code PENDING/SENT} 的记录置 {@code TIMEOUT}，返回受影响行数。 */
    int sweepTimeouts();

    /**
     * 统计指定设备处于 {@code QUEUED} 的命令数（不看到期与否），供上线补发的轻量短路：
     * 为 0 时调用方无需再走补发链路。
     */
    int countQueued(Long deviceId);

    /**
     * 补发指定设备的 {@code QUEUED} 命令（按 {@code next_attempt_at} 升序，最多 {@code limit} 条）。
     * <p>
     * 逐条发布并迁移状态：成功 {@code QUEUED → SENT}；失败 {@code attempt_count+1} 并按指数退避重设时间，
     * 次数耗尽置 {@code FAILED}。逐条隔离异常，返回成功补发条数。
     */
    int flushQueued(Long deviceId, int limit);

    /**
     * 退避重试巡检：选出到期的 {@code QUEUED} 命令所在设备（设备在线、启用且产品启用），
     * 对每个设备调用 {@link #flushQueued(Long, int)}。返回成功补发条数。
     */
    int sweepQueuedRetries();

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