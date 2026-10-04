package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.SceneExecutionQuery;
import com.mqtt.cloud.dto.request.SceneQuery;
import com.mqtt.cloud.dto.request.SceneRequest;
import com.mqtt.cloud.dto.request.SceneTestRequest;
import com.mqtt.cloud.dto.response.SceneExecutionDetail;
import com.mqtt.cloud.dto.response.SceneTestResult;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.service.SceneExecutionService;
import com.mqtt.cloud.service.SceneService;
import com.mqtt.cloud.service.SceneTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 自动化 / 场景联动控制台接口（T-23 设计文档 §10.1）。
 * <p>
 * 场景与执行记录均按当前登录用户隔离，仅可读写本人数据；设备归属越权返回 {@code 2003}，
 * 场景 / 执行记录资源越权返回 {@code 403}。
 * <p>
 * 执行记录的两个静态段端点（{@code /scenes/executions}）先于 {@code /scenes/{id}} 声明，
 * 避免动态段吞掉静态路径（{@code GET /scenes/executions} 与 {@code GET /scenes/{id}} 同为单段路径）。
 */
@Tag(name = "场景联动", description = "自动化场景配置、执行记录、试运行与手动执行，仅可操作本人数据")
@RestController
@RequestMapping("/scenes")
public class SceneController {

    private final SceneService sceneService;
    private final SceneExecutionService sceneExecutionService;
    private final SceneTestService sceneTestService;

    public SceneController(SceneService sceneService,
                           SceneExecutionService sceneExecutionService,
                           SceneTestService sceneTestService) {
        this.sceneService = sceneService;
        this.sceneExecutionService = sceneExecutionService;
        this.sceneTestService = sceneTestService;
    }

    @Operation(summary = "场景分页列表", description = "返回本人场景，可按触发源 / 启用态 / 关键字过滤")
    @GetMapping
    public Result<IPage<SceneDefinition>> listScenes(SceneQuery query) {
        return Result.success(sceneService.listScenes(SecurityUtils.requireUserId(), query));
    }

    @Operation(summary = "创建场景", description = "校验触发源 / 条件组 / 步骤流 / 目标 / 动作配置，"
            + "非法返回 6241，不支持的动作类型返回 6242，超上限返回 6244，非法触发源返回 6245，越权设备返回 2003")
    @PostMapping
    public Result<SceneDefinition> createScene(@RequestBody SceneRequest request) {
        return Result.success(sceneService.createScene(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "执行记录分页", description = "本人执行记录，可按场景 / 触发设备 / 状态过滤，按创建时间倒序")
    @GetMapping("/executions")
    public Result<IPage<SceneExecution>> listExecutions(SceneExecutionQuery query) {
        return Result.success(sceneExecutionService.page(SecurityUtils.requireUserId(), query));
    }

    @Operation(summary = "执行记录详情", description = "含步骤明细与转发载荷快照；不存在返回 6243，越权返回 403")
    @GetMapping("/executions/{id}")
    public Result<SceneExecutionDetail> getExecution(
            @Parameter(description = "执行记录 ID", required = true) @PathVariable Long id) {
        return Result.success(sceneExecutionService.getDetail(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "手动重试", description = "仅失败（FAILED）记录可重试，从失败步骤继续；"
            + "非失败状态返回 409，不存在返回 6243，越权返回 403")
    @PostMapping("/executions/{id}/retry")
    public Result<Void> retryExecution(
            @Parameter(description = "执行记录 ID", required = true) @PathVariable Long id) {
        sceneExecutionService.manualRetry(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "场景详情", description = "含条件组与步骤流；不存在返回 6240，越权返回 403")
    @GetMapping("/{id}")
    public Result<SceneDefinition> getScene(
            @Parameter(description = "场景 ID", required = true) @PathVariable Long id) {
        return Result.success(sceneService.getScene(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新场景", description = "整体替换步骤流，校验同创建；不存在返回 6240，越权返回 403")
    @PutMapping("/{id}")
    public Result<SceneDefinition> updateScene(
            @Parameter(description = "场景 ID", required = true) @PathVariable Long id,
            @RequestBody SceneRequest request) {
        return Result.success(sceneService.updateScene(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除场景", description = "逻辑删除，执行记录保留；不存在返回 6240，越权返回 403")
    @DeleteMapping("/{id}")
    public Result<Void> deleteScene(
            @Parameter(description = "场景 ID", required = true) @PathVariable Long id) {
        sceneService.deleteScene(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "启用 / 停用场景", description = "请求体 {enabled:true/false}；不存在返回 6240，越权返回 403")
    @PutMapping("/{id}/enabled")
    public Result<Void> setSceneEnabled(
            @Parameter(description = "场景 ID", required = true) @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        if (body == null || body.get("enabled") == null) {
            throw new BusinessException(ResultCode.SCENE_INVALID, "缺少 enabled 字段");
        }
        sceneService.setEnabled(SecurityUtils.requireUserId(), id, body.get("enabled"));
        return Result.success();
    }

    @Operation(summary = "场景试运行", description = "干跑判定触发 / 条件组 / 冷却并返回步骤摘要，无副作用；"
            + "不存在返回 6240，非法返回 6241，越权设备返回 2003")
    @PostMapping("/{id}/test")
    public Result<SceneTestResult> testScene(
            @Parameter(description = "场景 ID", required = true) @PathVariable Long id,
            @RequestBody SceneTestRequest request) {
        return Result.success(sceneTestService.test(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "手动执行一次", description = "按当前配置真实触发一次（trigger_source=MANUAL），"
            + "返回执行记录 ID；场景无启用步骤返回 6241，不存在返回 6240，越权返回 403")
    @PostMapping("/{id}/run")
    public Result<Long> runScene(
            @Parameter(description = "场景 ID", required = true) @PathVariable Long id) {
        return Result.success(sceneService.runManually(SecurityUtils.requireUserId(), id));
    }
}