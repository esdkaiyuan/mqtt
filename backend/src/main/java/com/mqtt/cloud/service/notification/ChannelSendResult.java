package com.mqtt.cloud.service.notification;

/**
 * 单渠道投递结果（T-27 设计文档 §4.4）。
 *
 * @param success      是否投递成功
 * @param skip         是否「无匹配目标」；为 true 时不写投递记录，避免表被无关事件撑大
 * @param target       投递目标摘要（邮箱、脱敏手机号、URL 主机），无目标时为 {@code null}
 * @param errorMessage 失败摘要，成功或跳过时为 {@code null}
 */
public record ChannelSendResult(boolean success, boolean skip, String target, String errorMessage) {

    /** 投递成功。 */
    public static ChannelSendResult success(String target) {
        return new ChannelSendResult(true, false, target, null);
    }

    /** 投递失败，由分发器写 {@code FAILED} 记录并交由巡检重试。 */
    public static ChannelSendResult failure(String target, String errorMessage) {
        return new ChannelSendResult(false, false, target, errorMessage);
    }

    /** 无匹配目标（事件未订阅、设备缺失等），不写投递记录。 */
    public static ChannelSendResult skipped() {
        return new ChannelSendResult(false, true, null, null);
    }
}