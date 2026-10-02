package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.dto.request.ThingModelRequest;
import com.mqtt.cloud.dto.response.ThingModelResponse;
import com.mqtt.cloud.dto.response.ThingModelValidationResponse;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.ProductService;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.service.ThingModelValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "产品管理", description = "设备类型模板（产品）的增删改查")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private final ThingModelService thingModelService;

    @Operation(summary = "创建产品")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Product> create(@Valid @RequestBody ProductRequest request) {
        return Result.success(productService.create(request));
    }

    @Operation(summary = "产品列表")
    @GetMapping
    public Result<List<Product>> list() {
        return Result.success(productService.list());
    }

    @Operation(summary = "更新产品")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Product> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return Result.success(productService.update(id, request));
    }

    @Operation(summary = "停用产品", description = "旗下所有设备将无法通过认证，缓存立即失效；重复停用幂等成功")
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> disableProduct(@PathVariable Long id) {
        productService.disableProduct(id);
        return Result.success();
    }

    @Operation(summary = "启用产品", description = "恢复旗下设备的认证许可，缓存立即失效；重复启用幂等成功")
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> enableProduct(@PathVariable Long id) {
        productService.enableProduct(id);
        return Result.success();
    }

    @Operation(summary = "删除产品")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success(null);
    }

    // ---------- 物模型（T-14） ----------

    @Operation(summary = "查询物模型", description = "未建模时 thingModel 为 null，version 为 0")
    @GetMapping("/{id}/thing-model")
    public Result<ThingModelResponse> getThingModel(@PathVariable Long id) {
        return Result.success(thingModelService.get(id));
    }

    @Operation(summary = "保存物模型", description = "先校验，通过后版本号 +1 并失效本副本缓存")
    @PutMapping("/{id}/thing-model")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ThingModelResponse> saveThingModel(@PathVariable Long id,
                                                     @Valid @RequestBody ThingModelRequest request) {
        return Result.success(thingModelService.save(id, request.getThingModel()));
    }

    @Operation(summary = "清空物模型", description = "版本号 +1 并失效缓存；已为空时为幂等空操作")
    @DeleteMapping("/{id}/thing-model")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ThingModelResponse> clearThingModel(@PathVariable Long id) {
        return Result.success(thingModelService.clear(id));
    }

    @Operation(summary = "校验物模型", description = "只校验不保存，返回全部错误路径")
    @PostMapping("/{id}/thing-model/validate")
    public Result<ThingModelValidationResponse> validateThingModel(
            @PathVariable Long id, @Valid @RequestBody ThingModelRequest request) {
        return Result.success(toValidationResponse(thingModelService.validate(id, request.getThingModel())));
    }

    @Operation(summary = "导出物模型", description = "以 JSON 附件返回，文件名含产品标识与版本号")
    @GetMapping("/{id}/thing-model/export")
    public ResponseEntity<byte[]> exportThingModel(@PathVariable Long id) {
        ThingModelService.ThingModelExport export = thingModelService.export(id);
        byte[] body = export.content().getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(body);
    }

    @Operation(summary = "导入物模型", description = "等价于保存，同样校验；内容上限 256 KB")
    @PostMapping("/{id}/thing-model/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ThingModelResponse> importThingModel(@PathVariable Long id,
                                                       @Valid @RequestBody ThingModelRequest request) {
        return Result.success(thingModelService.importModel(id, request.getThingModel()));
    }

    private ThingModelValidationResponse toValidationResponse(ThingModelValidationResult result) {
        List<ThingModelValidationResponse.Error> errors = result.errors().stream()
                .map(error -> new ThingModelValidationResponse.Error(error.path(), error.message()))
                .toList();
        return new ThingModelValidationResponse(result.valid(), errors);
    }
}