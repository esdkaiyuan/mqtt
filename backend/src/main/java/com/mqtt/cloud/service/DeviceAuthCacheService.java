package com.mqtt.cloud.service;

/**
 * 设备认证元数据缓存。
 * <p>
 * 认证热路径每次连接都要查产品与设备两张表；缓存把这两次查询收敛为一次 Redis 读取。
 * 缓存只保存判定所需的元数据，<b>不保存明文密钥、也不保存比较结果</b>：
 * BCrypt 校验必须逐次计算（见 {@link DeviceSecretService#matches}），
 * 因此即使缓存被读取也无法直接还原凭据。
 */
public interface DeviceAuthCacheService {

    /** 读取元数据；未命中、缓存关闭或 Redis 不可用时返回 {@code null}（调用方回源查库）。 */
    AuthMeta get(String productKey, String deviceKey);

    /** 回填元数据；缓存关闭时不写入。 */
    void put(String productKey, String deviceKey, AuthMeta meta);

    /** 失效单台设备；密钥重置、设备禁用/删除时调用。 */
    void evict(String productKey, String deviceKey);

    /** 失效某产品下全部设备；产品停用/删除、产品标识变更时调用。 */
    void evictProduct(String productKey);

    /**
     * 认证判定所需的产品/设备元数据快照。
     */
    record AuthMeta(Long productId, String productStatus, Integer deviceEnabled, String secretHash) {
    }
}