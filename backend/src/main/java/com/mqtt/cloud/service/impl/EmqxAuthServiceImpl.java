package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.EmqxAuthService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmqxAuthServiceImpl implements EmqxAuthService {

    private static final String PRODUCT_ENABLED = "ENABLED";

    private final AccessControlProperties properties;
    private final DeviceSecretService deviceSecretService;
    private final ProductService productService;
    private final DeviceService deviceService;
    private final DeviceAuthCacheService authCacheService;

    @Override
    public boolean authenticate(String username, String password, String clientId) {
        if (!properties.isEnforceAuth()) {
            return true;
        }
        if (!StringUtils.hasText(username) || password == null) {
            return false;
        }
        if (deviceSecretService.isPlatformUsername(username)) {
            return password.equals(properties.getPlatformSecret());
        }
        String[] parts = deviceSecretService.parseUsername(username);
        if (parts == null) {
            log.debug("拒绝认证：用户名格式非法 username={}", username);
            return false;
        }
        String productKey = parts[0];
        String deviceKey = parts[1];

        // 热路径：优先命中缓存，避免每次连接都查产品与设备两张表
        DeviceAuthCacheService.AuthMeta meta = authCacheService.get(productKey, deviceKey);
        if (meta == null) {
            meta = loadMeta(productKey, deviceKey);
            if (meta == null) {
                log.debug("拒绝认证：产品或设备不存在 productKey={}, deviceKey={}", productKey, deviceKey);
                return false;
            }
            authCacheService.put(productKey, deviceKey, meta);
        }

        if (!PRODUCT_ENABLED.equals(meta.productStatus())) {
            log.debug("拒绝认证：产品不存在或已停用 productKey={}", productKey);
            return false;
        }
        if (meta.deviceEnabled() == null || meta.deviceEnabled() != 1) {
            log.debug("拒绝认证：设备已被禁用 deviceKey={}", deviceKey);
            return false;
        }
        // BCrypt 校验不可缓存：缓存只消除 DB 查询，比较必须逐次计算
        return deviceSecretService.matches(password, meta.secretHash());
    }

    /**
     * 回源查库并组装元数据；产品不存在、设备不存在/不属于该产品时返回 {@code null}（不缓存否定结果）。
     */
    private DeviceAuthCacheService.AuthMeta loadMeta(String productKey, String deviceKey) {
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