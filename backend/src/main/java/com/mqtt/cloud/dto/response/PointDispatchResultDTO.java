package com.mqtt.cloud.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 点表下发结果（T-31 P3）。
 * <p>
 * 之所以回「命令ID + 状态」而不是只回一个布尔：下发可能是<b>已排队</b>而非已送达
 * （设备离线时按 T-16 语义入队补发）。只回布尔会让调用方以为配置已经生效，
 * 而实际上要等设备上线才生效 —— 这个信息差在现场排查时非常致命。
 * <p>
 * 下发历史不在此返回，走命令记录接口按 {@code source=POINT_TABLE} 过滤，
 * 避免同一份历史有两套查询入口而两者结果不一致。
 */
@Data
@Builder
public class PointDispatchResultDTO {

    /** 本次下发对应的命令记录ID，是回执关联键 */
    private String commandId;

    /**
     * 命令状态：{@code SENT} 已发布 / {@code QUEUED} 设备离线已入队待补发 /
     * {@code PENDING} 发布中 / {@code FAILED} 发布失败。
     */
    private String status;

    /** 本次下发的点表版本号（单调递增），网关据此丢弃乱序到达的旧版本 */
    private Long version;

    /** 本次下发的启用点位数 */
    private Integer pointCount;
}
