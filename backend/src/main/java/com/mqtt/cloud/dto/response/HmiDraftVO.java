package com.mqtt.cloud.dto.response;

import java.util.List;

/**
 * 上位机生成草稿（T-38）：不落库，供前端预览后再保存。
 *
 * @param notices 生成过程中的降级说明（未建模 / 控件被截断等）
 */
public record HmiDraftVO(Long deviceId,
                         String deviceKey,
                         String deviceName,
                         String suggestedName,
                         HmiConfig config,
                         List<String> notices) {
}
