package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 产品-设备准入判定：认证回调与授权（ACL）回调共用的唯一判定入口。
 * <p>
 * 两个回调都要回答同一个问题 ——「这台设备此刻是否被允许接入」。若各写一套，
 * 禁用/停用的语义就会分叉：认证拒绝连接、授权却继续放行已连接会话。
 * 这里统一走 {@link DeviceAuthCacheService}（缓存优先、未命中回源并回填），
 * 使禁用/停用经主动失效后对<b>连接与收发两个环节同时生效</b>。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAccessGuard {

    private static final String PRODUCT_ENABLED = "ENABLED";

    private final ProductService productService;
    private final DeviceService deviceService;
    private final DeviceAuthCacheService authCacheService;

    /**
     * 读取准入判定所需的元数据：缓存优先，未命中回源查库并回填。
     * 产品不存在、设备不存在或设备不属于该产品时返回 {@code null}（否定结果不缓存）。
     */
    public DeviceAuthCacheService.AuthMeta resolve(String productKey, String deviceKey) {
        DeviceAuthCacheService.AuthMeta meta = authCacheService.get(productKey, deviceKey);
        if (meta != null) {
            return meta;
        }
        meta = loadFromDb(productKey, deviceKey);
        if (meta != null) {
            authCacheService.put(productKey, deviceKey, meta);
        }
        return meta;
    }

    /** 准入判定：产品启用且设备启用；元数据缺失（不存在）一律视为不允许。 */
    public boolean isPermitted(DeviceAuthCacheService.AuthMeta meta) {
        return meta != null
                && PRODUCT_ENABLED.equals(meta.productStatus())
                && meta.deviceEnabled() != null
                && meta.deviceEnabled() == 1;
    }

    private DeviceAuthCacheService.AuthMeta loadFromDb(String productKey, String deviceKey) {
        Product product = productService.getByProductKey(productKey);
        if (product == null) {
            return null;
        }
        Device device;
        try {
            device = deviceService.getDeviceByKey(deviceKey);
        } catch (Exception e) {
            return null;
        }
        if (device == null || !product.getId().equals(device.getProductId())) {
            return null;
        }
        return new DeviceAuthCacheService.AuthMeta(
                product.getId(), product.getStatus(), device.getEnabled(), device.getDeviceSecretHash());
    }
}