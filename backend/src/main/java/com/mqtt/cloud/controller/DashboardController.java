package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.DashboardSaveDTO;
import com.mqtt.cloud.dto.response.DashboardDetailVO;
import com.mqtt.cloud.dto.response.DashboardSummaryVO;
import com.mqtt.cloud.service.DashboardService;
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

import java.util.List;

/**
 * 可保存看板控制器（T-21 设计文档 §6.2）。
 * <p>
 * 看板**仅归属创建者本人**：{@code user_id} 一律由登录态注入，忽略请求体传值；
 * 非本人（或不存在）一律 {@code 6228}，不区分「不存在」与「无权限」，避免探测。
 * <p>
 * 配置为客户端 JSON，服务层解析为强类型后逐字段白名单校验（面板数 / 设备 / 属性 / 桶 / 图型 / 聚合 / 跨度），
 * 非法返回 {@code 6229}；超出面板数上限返回 {@code 6229}，超出看板数上限返回 {@code 6230}。
 */
@Tag(name = "看板", description = "可保存看板的增删改查，仅可操作本人数据")
@RestController
@RequestMapping("/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "看板列表", description = "返回本人看板摘要（按更新时间倒序），含面板数量")
    @GetMapping
    public Result<List<DashboardSummaryVO>> listDashboards() {
        return Result.success(dashboardService.list(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "创建看板", description = "校验名称与配置，非法返回 6229，超出看板数上限返回 6230")
    @PostMapping
    public Result<DashboardDetailVO> createDashboard(@RequestBody DashboardSaveDTO dto) {
        return Result.success(dashboardService.create(SecurityUtils.requireUserId(), dto));
    }

    @Operation(summary = "看板详情", description = "按 ID 查询本人看板；不存在或非本人返回 6228")
    @GetMapping("/{id}")
    public Result<DashboardDetailVO> getDashboard(
            @Parameter(description = "看板 ID", required = true) @PathVariable Long id) {
        return Result.success(dashboardService.detail(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新看板", description = "校验同创建；不存在或非本人返回 6228，非法返回 6229")
    @PutMapping("/{id}")
    public Result<DashboardDetailVO> updateDashboard(
            @Parameter(description = "看板 ID", required = true) @PathVariable Long id,
            @RequestBody DashboardSaveDTO dto) {
        return Result.success(dashboardService.update(SecurityUtils.requireUserId(), id, dto));
    }

    @Operation(summary = "删除看板", description = "物理删除本人看板；不存在或非本人返回 6228")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDashboard(
            @Parameter(description = "看板 ID", required = true) @PathVariable Long id) {
        dashboardService.remove(SecurityUtils.requireUserId(), id);
        return Result.success();
    }
}
