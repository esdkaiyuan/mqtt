package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "产品管理", description = "设备类型模板（产品）的增删改查")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

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
}