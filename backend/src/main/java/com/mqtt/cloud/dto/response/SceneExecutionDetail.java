package com.mqtt.cloud.dto.response;

import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.entity.SceneStepRun;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 场景执行详情（T-23 设计文档 §5.5）。
 * <p>
 * 执行记录 + 步骤明细（按 {@code seq} 升序）。步骤明细含 {@code forwardPayload} 载荷快照
 * （体积可能较大，故仅详情返回，列表不返回）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneExecutionDetail {

    /** 执行记录。 */
    private SceneExecution execution;

    /** 步骤明细，按 {@code seq} 升序。 */
    private List<SceneStepRun> steps;
}