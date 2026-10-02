package com.mqtt.cloud.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物模型查询 / 保存响应。未建模时 {@code thingModel} 为 {@code null}、{@code version} 为 0。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThingModelResponse {

    private String thingModel;

    private Integer version;

    private LocalDateTime updatedAt;
}