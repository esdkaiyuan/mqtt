package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.DashboardShareRequest;
import com.mqtt.cloud.dto.response.DashboardShareVO;
import com.mqtt.cloud.service.DashboardShareService;
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
 * 看板分享控制台接口（T-46）。
 * <p>
 * 分享按创建者隔离；令牌明文只在创建 / 轮换响应返回，列表仅返回前缀。
 */
@Tag(name = "看板分享", description = "看板大屏分享令牌与轮播配置，仅可操作本人分享")
@RestController
@RequestMapping("/dashboards/shares")
public class DashboardShareController {

    private final DashboardShareService dashboardShareService;

    public DashboardShareController(DashboardShareService dashboardShareService) {
        this.dashboardShareService = dashboardShareService;
    }

    @Operation(summary = "分享列表", description = "返回本人分享（不含明文令牌）；按更新时间倒序")
    @GetMapping
    public Result<List<DashboardShareVO>> list() {
        return Result.success(dashboardShareService.list(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "创建分享", description = "返回明文令牌与大屏路径（仅此一次）；看板须为本人所有")
    @PostMapping
    public Result<DashboardShareVO> create(@RequestBody DashboardShareRequest request) {
        return Result.success(dashboardShareService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "更新分享", description = "更新名称 / 看板集合 / 轮播间隔 / 启停；令牌不变")
    @PutMapping("/{id}")
    public Result<DashboardShareVO> update(
            @Parameter(description = "分享 ID", required = true) @PathVariable Long id,
            @RequestBody DashboardShareRequest request) {
        return Result.success(dashboardShareService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除分享", description = "删除后旧链接立即失效")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "分享 ID", required = true) @PathVariable Long id) {
        dashboardShareService.delete(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "轮换令牌", description = "旧链接立即失效，返回新明文令牌与大屏路径")
    @PostMapping("/{id}/rotate")
    public Result<DashboardShareVO> rotate(
            @Parameter(description = "分享 ID", required = true) @PathVariable Long id) {
        return Result.success(dashboardShareService.rotate(SecurityUtils.requireUserId(), id));
    }
}
