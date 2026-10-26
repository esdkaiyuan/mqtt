package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.ProductMapper;
import com.mqtt.cloud.service.PayloadCodecDefinition;
import com.mqtt.cloud.service.PayloadCodecService;
import com.mqtt.cloud.util.PayloadCodecValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Payload Codec 服务实现（T-55）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayloadCodecServiceImpl implements PayloadCodecService {

    private final ProductMapper productMapper;
    private final PayloadCodecCache cache;
    private final ObjectMapper objectMapper;

    @Override
    public String validateAndNormalize(String raw) {
        return PayloadCodecValidator.normalize(raw, objectMapper);
    }

    @Override
    public PayloadCodecDefinition getForProduct(Long productId) {
        if (productId == null) {
            return PayloadCodecDefinition.EMPTY;
        }
        PayloadCodecDefinition cached = cache.get(productId);
        if (cached != null) {
            return cached;
        }
        PayloadCodecDefinition definition = PayloadCodecDefinition.EMPTY;
        Product product = productMapper.selectById(productId);
        String raw = product == null ? null : product.getPayloadCodec();
        if (raw != null && !raw.isBlank()) {
            try {
                PayloadCodecDefinition parsed =
                        PayloadCodecValidator.parseDefinition(raw, objectMapper, false);
                if (parsed != null) {
                    definition = parsed;
                } else {
                    log.warn("产品 Payload Codec 解析失败，按原始载荷处理: productId={}", productId);
                }
            } catch (Exception e) {
                log.warn("产品 Payload Codec 加载失败，按原始载荷处理: productId={}, err={}",
                        productId, e.getMessage());
            }
        }
        cache.put(productId, definition);
        return definition;
    }

    @Override
    public void evict(Long productId) {
        cache.evict(productId);
    }
}