package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景版本摘要（T-51）；不返回完整快照，避免列表响应过大。
 */
@Data
public class SceneVersionVO {

    private Long id;

    private Integer versionNo;

    /** CREATE / UPDATE / RESTORE */
    private String changeType;

    private String sceneName;

    private String triggerType;

    private Integer stepCount;

    private Long operatorId;

    private LocalDateTime createdAt;
}
