package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.util.AfterCommit;
import com.mqtt.cloud.config.ThingModelProperties;
import com.mqtt.cloud.dto.response.ThingModelResponse;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.ProductMapper;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.service.ThingModelValidationError;
import com.mqtt.cloud.service.ThingModelValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 物模型读写实现。
 * <p>
 * 版本语义：每次有效保存 / 清空都 +1，不做合并；清空已为空的物模型为幂等空操作。
 * 缓存失效放在事务提交后，避免并发读在本事务未提交时把旧定义重新灌回缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThingModelServiceImpl implements ThingModelService {

    private final ProductMapper productMapper;
    private final ThingModelCache cache;
    private final ThingModelProperties properties;

    @Override
    public ThingModelResponse get(Long productId) {
        return toResponse(requireProduct(productId));
    }

    @Override
    @Transactional
    public ThingModelResponse save(Long productId, String thingModelJson) {
        Product product = requireProduct(productId);
        requireWithinSizeLimit(thingModelJson);
        requireValid(thingModelJson);
        persistModel(product, thingModelJson);
        return toResponse(product);
    }

    @Override
    @Transactional
    public ThingModelResponse clear(Long productId) {
        Product product = requireProduct(productId);
        if (product.getThingModel() == null) {
            return toResponse(product);
        }
        persistModel(product, null);
        return toResponse(product);
    }

    @Override
    public ThingModelValidationResult validate(Long productId, String thingModelJson) {
        requireProduct(productId);
        return ThingModelValidator.validate(thingModelJson);
    }

    @Override
    public ThingModelExport export(Long productId) {
        Product product = requireProduct(productId);
        if (product.getThingModel() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该产品尚未定义物模型");
        }
        String fileName = "thing-model-" + product.getProductKey() + "-v" + version(product) + ".json";
        return new ThingModelExport(fileName, product.getThingModel());
    }

    @Override
    @Transactional
    public ThingModelResponse importModel(Long productId, String thingModelJson) {
        return save(productId, thingModelJson);
    }

    @Override
    public ThingModelDefinition getForProduct(Long productId) {
        if (productId == null || !properties.isInterpretEnabled()) {
            return ThingModelDefinition.EMPTY;
        }
        ThingModelDefinition cached = cache.get(productId);
        if (cached != null) {
            return cached;
        }
        ThingModelDefinition definition = loadDefinition(productId);
        cache.put(productId, definition);
        return definition;
    }

    /**
     * 热路径回源：产品不存在或物模型非法时按「未建模」处理，绝不抛异常中断摄取批次。
     */
    private ThingModelDefinition loadDefinition(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getThingModel() == null) {
            return ThingModelDefinition.EMPTY;
        }
        try {
            return ThingModelValidator.parseDefinition(product.getThingModel(), version(product));
        } catch (Exception e) {
            log.warn("物模型投影解析失败，按未建模处理: productId={}", productId, e);
            return ThingModelDefinition.EMPTY;
        }
    }

    private void persistModel(Product product, String thingModelJson) {
        product.setThingModel(thingModelJson);
        product.setThingModelVersion(version(product) + 1);
        product.setThingModelUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        Long productId = product.getId();
        AfterCommit.run(() -> cache.evict(productId));
    }

    private void requireWithinSizeLimit(String thingModelJson) {
        int max = properties.getMaxSizeBytes();
        if (max <= 0 || thingModelJson == null) {
            return;
        }
        if (thingModelJson.getBytes(StandardCharsets.UTF_8).length > max) {
            throw new BusinessException(ResultCode.THING_MODEL_TOO_LARGE,
                    "物模型内容超过大小上限 " + max + " 字节");
        }
    }

    private void requireValid(String thingModelJson) {
        ThingModelValidationResult result = ThingModelValidator.validate(thingModelJson);
        ThingModelValidationError first = result.firstError();
        if (result.parseFailed()) {
            throw new BusinessException(ResultCode.THING_MODEL_PARSE_FAILED,
                    first == null ? ResultCode.THING_MODEL_PARSE_FAILED.getMessage() : first.message());
        }
        if (!result.valid()) {
            throw new BusinessException(ResultCode.THING_MODEL_INVALID,
                    first == null ? ResultCode.THING_MODEL_INVALID.getMessage()
                            : first.path() + " " + first.message());
        }
    }

    private Product requireProduct(Long productId) {
        Product product = productId == null ? null : productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private ThingModelResponse toResponse(Product product) {
        return new ThingModelResponse(product.getThingModel(), version(product),
                product.getThingModelUpdatedAt());
    }

    private int version(Product product) {
        return product.getThingModelVersion() == null ? 0 : product.getThingModelVersion();
    }
}