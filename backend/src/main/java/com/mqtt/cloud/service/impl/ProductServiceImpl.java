package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.ProductMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String DEFAULT_TOPIC_PREFIX = "device/{deviceKey}";
    private static final String DEFAULT_PAYLOAD_FORMAT = "JSON";
    private static final String AUTH_MODE_SECRET = "SECRET";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String RESERVED_KEY = "platform";

    private final ProductMapper productMapper;

    private final DeviceMapper deviceMapper;

    private final DeviceAuthCacheService authCacheService;

    @Override
    @Transactional
    public Product create(ProductRequest request) {
        String productKey = request.getProductKey();
        if (RESERVED_KEY.equalsIgnoreCase(productKey)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品标识 " + RESERVED_KEY + " 为保留名");
        }
        if (existsByKey(productKey, null)) {
            throw new BusinessException(ResultCode.PRODUCT_KEY_EXISTS);
        }
        Product product = new Product();
        product.setProductKey(productKey);
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setAuthMode(AUTH_MODE_SECRET);
        product.setTopicPrefix(StringUtils.hasText(request.getTopicPrefix())
                ? request.getTopicPrefix() : DEFAULT_TOPIC_PREFIX);
        product.setPayloadFormat(StringUtils.hasText(request.getPayloadFormat())
                ? request.getPayloadFormat() : DEFAULT_PAYLOAD_FORMAT);
        product.setMetadataSchema(request.getMetadataSchema());
        product.setStatus(STATUS_ENABLED);
        productMapper.insert(product);
        return product;
    }

    @Override
    public List<Product> list() {
        return productMapper.selectList(
                Wrappers.<Product>lambdaQuery().orderByAsc(Product::getId));
    }

    @Override
    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product existing = requireById(id);
        if (existsByKey(request.getProductKey(), id)) {
            throw new BusinessException(ResultCode.PRODUCT_KEY_EXISTS);
        }
        String previousKey = existing.getProductKey();
        existing.setProductKey(request.getProductKey());
        existing.setProductName(request.getProductName());
        existing.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getTopicPrefix())) {
            existing.setTopicPrefix(request.getTopicPrefix());
        }
        if (StringUtils.hasText(request.getPayloadFormat())) {
            existing.setPayloadFormat(request.getPayloadFormat());
        }
        existing.setMetadataSchema(request.getMetadataSchema());
        productMapper.updateById(existing);
        // 产品标识变更会改变用户名与缓存键，旧键下的元数据不再可达，主动清理
        if (!previousKey.equals(existing.getProductKey())) {
            authCacheService.evictProduct(previousKey);
        }
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = requireById(id);
        long deviceCount = deviceMapper.selectCount(
                Wrappers.<com.mqtt.cloud.entity.Device>lambdaQuery()
                        .eq(com.mqtt.cloud.entity.Device::getProductId, id));
        if (deviceCount > 0) {
            throw new BusinessException(ResultCode.PRODUCT_HAS_DEVICES);
        }
        productMapper.deleteById(id);
        // 产品停用/删除后其下设备不应再通过认证；停用走状态变更时同样需要失效
        authCacheService.evictProduct(product.getProductKey());
    }

    @Override
    @Transactional
    public void disableProduct(Long id) {
        setProductStatus(id, STATUS_DISABLED);
    }

    @Override
    @Transactional
    public void enableProduct(Long id) {
        setProductStatus(id, STATUS_ENABLED);
    }

    /**
     * 统一设置产品状态。状态变更会改变旗下设备的认证判定结果，
     * 因此必须失效该产品下全部设备的认证缓存，避免 TTL 内旧元数据继续放行。
     */
    private void setProductStatus(Long id, String status) {
        Product product = requireById(id);
        if (status.equals(product.getStatus())) {
            return;
        }
        product.setStatus(status);
        productMapper.updateById(product);
        authCacheService.evictProduct(product.getProductKey());
    }

    @Override
    public Product getByProductKey(String productKey) {
        return productMapper.selectOne(
                Wrappers.<Product>lambdaQuery().eq(Product::getProductKey, productKey));
    }

    @Override
    public Product requireById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private boolean existsByKey(String productKey, Long excludeId) {
        return productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getProductKey, productKey)
                .ne(excludeId != null, Product::getId, excludeId)) > 0;
    }
}