package com.mqtt.cloud.collect;

import java.util.List;

/**
 * 采集轮次结果（T-31）
 * <p>
 * 承载一轮采集的完整产出：成功的点位值、失败的点位说明、以及驱动层面的整体结论。
 * <p>
 * <b>为什么要区分「点位级失败」与「轮次级失败」</b>：两者的处置动作完全不同。
 * 点位级失败（某寄存器地址不存在）只记日志、下一轮继续；轮次级失败（设备完全连不上）
 * 应当触发设备离线判定与告警评估。前者如果被混同后者的异常路径，会让一个地址配错
 * 的点位反复触发设备离线告警，把告警系统变成噪声源。
 *
 * @param values    逐点位结果（成功与失败混排，顺序与请求点位一致）
 * @param connected 驱动是否成功建立了连接；{@code false} 表示整轮不可用
 * @param errorMessage 轮次级错误描述；{@code connected=false} 时必填
 */
public record CollectResult(List<PointValue> values, boolean connected, String errorMessage) {

    public CollectResult {
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** 整轮成功（全部点位取到值）。 */
    public static CollectResult success(List<PointValue> values) {
        return new CollectResult(values, true, null);
    }

    /** 整轮不可用（如设备连不上）；调用方应据此把该设备下所有点位记为失败。 */
    public static CollectResult failure(String errorMessage) {
        return new CollectResult(List.of(), false, errorMessage);
    }

    /** 成功点位集合。 */
    public List<PointValue> successes() {
        return values.stream().filter(PointValue::success).toList();
    }

    /** 失败点位集合。 */
    public List<PointValue> failures() {
        return values.stream().filter(v -> !v.success()).toList();
    }
}
