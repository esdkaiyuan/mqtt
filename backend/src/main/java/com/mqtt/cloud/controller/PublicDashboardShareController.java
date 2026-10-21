package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.response.DashboardSharePublicVO;
import com.mqtt.cloud.service.DashboardShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开大屏分享入口（T-46）。
 * <p>
 * 无登录态、只读；令牌无效 / 停用 / 看板全部不可用一律 {@code 6305}，不区分原因。
 * 该路径在 {@code SecurityConfig} 中 permitAll。
 */
@Tag(name = "公开大屏", description = "凭分享令牌读取看板配置，无需登录")
@RestController
@RequestMapping("/public/dashboard-shares")
public class PublicDashboardShareController {

    private final DashboardShareService dashboardShareService;

    public PublicDashboardShareController(DashboardShareService dashboardShareService) {
        this.dashboardShareService = dashboardShareService;
    }

    @Operation(summary = "公开看板分享", description = "凭令牌返回看板配置、设备名与轮播间隔；无效令牌返回 6305")
    @GetMapping("/{token}")
    public Result<DashboardSharePublicVO> view(
            @Parameter(description = "分享令牌", required = true) @PathVariable String token) {
        return Result.success(dashboardShareService.publicView(token));
    }
}
