package com.mqtt.cloud.service;

/**
 * 产品级 Payload Codec 服务（T-55）。
 * <p>
 * 负责：
 * <ul>
 *   <li>保存期校验与规范化（{@link #validateAndNormalize(String)}）；</li>
 *   <li>解析期按产品取模板（进程内缓存，TTL 与物模型缓存一致）；</li>
 *   <li>产品更新后主动失效本副本缓存。</li>
 * </ul>
 */
public interface PayloadCodecService {

    /**
     * 校验并规范化 Payload Codec JSON；空值返回 {@code null}。
     *
     * @throws com.mqtt.cloud.common.exception.BusinessException 配置非法（{@code 6317}）
     */
    String validateAndNormalize(String raw);

    /** 取产品的 Codec 定义；未配置或解析失败返回 {@link PayloadCodecDefinition#EMPTY}。 */
    PayloadCodecDefinition getForProduct(Long productId);

    /** 产品更新 / 删除后失效本副本缓存。 */
    void evict(Long productId);
}