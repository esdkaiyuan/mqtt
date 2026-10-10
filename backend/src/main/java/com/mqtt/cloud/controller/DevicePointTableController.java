package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.PointTableUpsertRequest;
import com.mqtt.cloud.dto.response.PointTableDTO;
import com.mqtt.cloud.service.DevicePointTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备点位表控制器（T-31 P2）。
 * <p>
 * 点表是「设备地址与换算规则的唯一声明处」，配置错误会直接表现为采不到值或值不对，
 * 因此写入需归属校验、读取对属主开放；ADMIN 语义由 {@code SecurityUtils} 在服务层统一裁决。
 * <p>
 * <b>鉴权口径</b>：路径形如「/devices 下设备ID/points」，在 {@code SecurityConfig} 中登记为
 * {@code .authenticated()}，归属（ownerId）与 ADMIN 特权由控制器传入服务层校验，
 * 与 T-26 设备拓扑端点保持同一模式。
 */
@Tag(name = "设备点位表", description = "设备点位（寄存器/线圈/tag）配置，平台侧与边缘侧共用")
@RestController
@RequestMapping("/devices/{deviceId}/points")
public class DevicePointTableController {

    private final DevicePointTableService pointTableService;

    public DevicePointTableController(DevicePointTableService pointTableService) {
        this.pointTableService = pointTableService;
    }

    @Operation(summary = "查询设备点位表",
            description = "按设备返回启用中的点位，并附带最近一次采集状态与失败原因，"
                    + "用于区分「点位没配」「配了但采不到」两类现场问题。"
                    + "executor 可选 EDGE / PLATFORM；不传表示不区分执行方。"
                    + "ADMIN 可查看任意设备，普通用户仅限本人设备")
    @GetMapping
    public Result<List<PointTableDTO>> list(
            @Parameter(description = "设备ID") @PathVariable Long deviceId,
            @Parameter(description = "执行方筛选：EDGE / PLATFORM，留空表示全部")
            @RequestParam(required = false) String executor) {
        return Result.success(pointTableService.listPoints(currentUserIdOrNull(), deviceId, executor));
    }

    @Operation(summary = "批量保存设备点位表",
            description = "按 pointKey 执行 upsert（新增与更新合并），单批整体成功或整体失败。"
                    + "同一设备下点位执行方必须一致，混入另一执行方将整批拒绝（6281）——"
                    + "因为同一点位被两条采集路线写入会污染最新值并在属性历史中产生重复点。"
                    + "本批点位的执行方与设备既有点位不一致时同样整批拒绝，需先显式清理。"
                    + "常用字段默认值：采集周期 60000ms、数据类型 float、执行方 EDGE、启用 1")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or isAuthenticated()")
    public Result<Integer> save(
            @Parameter(description = "设备ID") @PathVariable Long deviceId,
            @Valid @RequestBody List<PointTableUpsertRequest> points) {
        return Result.success(pointTableService.savePoints(currentUserIdOrNull(), deviceId, points));
    }

    @Operation(summary = "删除设备点位",
            description = "删除点位并连带清理其采集日志（点位删除后日志即无主数据，"
                    + "而采集日志按周期增长，残留会持续占用存储）。"
                    + "跨设备删除按「不存在」处理")
    @DeleteMapping("/{pointId}")
    @PreAuthorize("hasRole('ADMIN') or isAuthenticated()")
    public Result<Void> delete(
            @Parameter(description = "设备ID") @PathVariable Long deviceId,
            @Parameter(description = "点位ID") @PathVariable Long pointId) {
        pointTableService.deletePoint(currentUserIdOrNull(), deviceId, pointId);
        return Result.success();
    }

    /**
     * 当前用户ID；ADMIN 传 {@code null} 以跳过归属校验（服务层按设备存在性放行）。
     * <p>
     * 这样 ADMIN 可管理任意设备的点表，普通用户只能改自己的设备，而不必在控制器里
     * 为两种身份写两套分支。
     */
    private Long currentUserIdOrNull() {
        return SecurityUtils.isAdmin() ? null : SecurityUtils.requireUserId();
    }
}