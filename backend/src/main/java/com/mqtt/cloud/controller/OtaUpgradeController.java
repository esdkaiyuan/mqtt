package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.OtaTaskCreateRequest;
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.dto.response.OtaTaskVO;
import com.mqtt.cloud.service.OtaUpgradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTA 升级任务控制器（T-22 设计文档 §6.2）。
 * <p>
 * 提供升级任务的创建、列表、详情、逐台记录分页、重投与删除；操作者身份一律由登录态注入，
 * 请求体不得传 {@code userId}。任务创建时解析目标集合、剔除与固件产品不一致的设备并即时下发。
 * <p>
 * 授权：{@code /ota/**} 由 {@code SecurityConfig} 统一限定 {@code ADMIN} / {@code OPERATOR}。
 */
@Tag(name = "OTA 升级任务", description = "升级任务创建、列表、详情、逐台记录与重投；仅 ADMIN/OPERATOR")
@RestController
@RequestMapping("/ota/tasks")
public class OtaUpgradeController {

    private final OtaUpgradeService otaUpgradeService;

    public OtaUpgradeController(OtaUpgradeService otaUpgradeService) {
        this.otaUpgradeService = otaUpgradeService;
    }

    @Operation(summary = "创建升级任务", description = "解析目标集合、剔除产品不匹配设备、落库任务与逐台记录并即时下发。"
            + "固件不存在或不属于当前用户返回 6231；目标为空、超限或与固件产品不匹配返回 6237")
    @PostMapping
    public Result<OtaTaskDetailVO> create(@Valid @RequestBody OtaTaskCreateRequest request) {
        return Result.success(otaUpgradeService.createTask(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "升级任务列表", description = "按创建时间倒序分页返回任务的聚合计数与状态")
    @GetMapping
    public Result<IPage<OtaTaskVO>> list(
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        return Result.success(otaUpgradeService.listTasks(SecurityUtils.requireUserId(), page, size));
    }

    @Operation(summary = "升级任务详情", description = "含创建时的目标快照；不存在或不属于当前用户返回 6236")
    @GetMapping("/{id}")
    public Result<OtaTaskDetailVO> detail(
            @Parameter(description = "任务 ID", required = true) @PathVariable Long id) {
        return Result.success(otaUpgradeService.detail(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "逐台升级记录", description = "按任务分页返回逐台记录（含进度与回传信息）；status 为空时不过滤；"
            + "任务不存在或不属于当前用户返回 6236")
    @GetMapping("/{id}/records")
    public Result<IPage<OtaRecordVO>> records(
            @Parameter(description = "任务 ID", required = true) @PathVariable Long id,
            @Parameter(description = "记录状态过滤，可空") @RequestParam(required = false) String status,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        return Result.success(otaUpgradeService.listRecords(SecurityUtils.requireUserId(), id, status, page, size));
    }

    @Operation(summary = "重投未成功设备", description = "将 PENDING/FAILED/TIMEOUT 记录重置为 PENDING 并重新下发，返回重置并按批尝试下发的记录数。"
            + "任务不存在返回 6236；任务已成功（SUCCESS）返回 6238")
    @PostMapping("/{id}/retry")
    public Result<Integer> retry(
            @Parameter(description = "任务 ID", required = true) @PathVariable Long id) {
        return Result.success(otaUpgradeService.retry(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "删除升级任务", description = "删除任务并级联清理逐台记录；任务处于 RUNNING 返回 6238，不存在返回 6236")
    @DeleteMapping("/{id}")
    public Result<Void> remove(
            @Parameter(description = "任务 ID", required = true) @PathVariable Long id) {
        otaUpgradeService.remove(SecurityUtils.requireUserId(), id);
        return Result.success();
    }
}
