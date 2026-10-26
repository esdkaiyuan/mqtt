package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.response.DeviceImportResultDTO;
import com.mqtt.cloud.service.DeviceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 设备 CSV 导入（T-53）。
 * <p>
 * 端点挂在 {@code /devices} 前缀下，但独立成控制器，避免 {@code DeviceController}
 * 继续膨胀；路径为静态段（import / import-template），不会被 {@code /devices/{id}} 吞掉。
 */
@Tag(name = "设备导入", description = "CSV 台账导入与模板下载；逐行独立事务，单行失败不阻断整批")
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceImportController {

    private final DeviceImportService deviceImportService;

    @Operation(summary = "下载设备导入模板", description = "返回 UTF-8 BOM CSV 模板；列：deviceKey, deviceName, "
            + "productKey, deviceType, topic, description, firmwareVersion")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadTemplate() {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("device-import-template.csv", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(deviceImportService.template());
    }

    @Operation(summary = "导入设备台账", description = "上传 UTF-8 CSV（可带 BOM），单次最多 1000 行、2MB；"
            + "deviceKey / deviceName / productKey 为必需列，firmwareVersion 可选；每台设备签发一次性密钥，"
            + "仅本次响应返回。文件格式非法返回 6316；逐行失败见 failedItems，不阻断整批")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<DeviceImportResultDTO> importDevices(
            @Parameter(description = "CSV 文件", required = true)
                    @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file == null ? null : file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "读取上传文件失败");
        }
        return Result.success(deviceImportService.importCsv(
                SecurityUtils.requireUserId(), content,
                file == null ? null : file.getOriginalFilename()));
    }
}