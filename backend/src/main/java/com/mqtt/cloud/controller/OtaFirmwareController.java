package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.response.OtaFirmwareVO;
import com.mqtt.cloud.service.OtaFirmwareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OTA 固件包控制器（T-22 设计文档 §6.2）。
 * <p>
 * 提供固件包的上传、列表、详情、下载与删除；操作者身份一律由登录态注入
 * （{@code SecurityUtils.requireUserId()}），请求体不得传 {@code userId}。
 * <p>
 * 授权：{@code /ota/**} 由 {@code SecurityConfig} 统一限定 {@code ADMIN} / {@code OPERATOR}。
 * <b>固件文件下载地址（{@code downloadUrl}）由 nginx 静态托管，不经本接口鉴权</b>
 * ——设备侧无法携带 JWT，故设备经 {@code app.ota.public-base-url} 直连拉取。
 */
@Tag(name = "OTA 固件包", description = "固件包上传、列表、详情、下载与删除；仅 ADMIN/OPERATOR")
@RestController
@RequestMapping("/ota/firmwares")
public class OtaFirmwareController {

    private final OtaFirmwareService otaFirmwareService;

    public OtaFirmwareController(OtaFirmwareService otaFirmwareService) {
        this.otaFirmwareService = otaFirmwareService;
    }

    @Operation(summary = "上传固件包", description = "校验产品存在（不存在返回 6002）、版本格式与文件大小（非法返回 6232）、"
            + "同产品版本唯一（重复返回 6233）；落盘失败返回 6235")
    @PostMapping
    public Result<OtaFirmwareVO> upload(
            @Parameter(description = "所属产品 ID", required = true) @RequestParam Long productId,
            @Parameter(description = "固件版本号", required = true) @RequestParam String version,
            @Parameter(description = "版本说明，可空") @RequestParam(required = false) String description,
            @Parameter(description = "固件文件本体", required = true) @RequestPart("file") MultipartFile file) {
        return Result.success(otaFirmwareService.upload(
                SecurityUtils.requireUserId(), productId, version, description, file));
    }

    @Operation(summary = "固件包列表", description = "返回当前用户可见的固件包（按创建时间倒序）；productId 为空时返回全部")
    @GetMapping
    public Result<List<OtaFirmwareVO>> list(
            @Parameter(description = "按产品过滤，可空") @RequestParam(required = false) Long productId) {
        return Result.success(otaFirmwareService.list(SecurityUtils.requireUserId(), productId));
    }

    @Operation(summary = "固件包详情", description = "按 ID 查询；不存在或不属于当前用户返回 6231")
    @GetMapping("/{id}")
    public Result<OtaFirmwareVO> detail(
            @Parameter(description = "固件包 ID", required = true) @PathVariable Long id) {
        return Result.success(otaFirmwareService.detail(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "下载固件包", description = "以附件流式写回文件本体；不存在返回 6231。"
            + "注：设备侧拉取走 nginx 静态托管地址，不经本接口")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @Parameter(description = "固件包 ID", required = true) @PathVariable Long id) {
        OtaFirmwareService.Download download = otaFirmwareService.download(SecurityUtils.requireUserId(), id);
        Resource resource = new FileSystemResource(download.filePath());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @Operation(summary = "删除固件包", description = "删除记录并清理磁盘文件；被升级任务引用返回 6234，不存在返回 6231")
    @DeleteMapping("/{id}")
    public Result<Void> remove(
            @Parameter(description = "固件包 ID", required = true) @PathVariable Long id) {
        otaFirmwareService.remove(SecurityUtils.requireUserId(), id);
        return Result.success();
    }
}
