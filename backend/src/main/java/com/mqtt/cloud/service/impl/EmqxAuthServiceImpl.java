package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
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
        Product product = productService.getByProductKey(parts[0]);
        if (product == null || !PRODUCT_ENABLED.equals(product.getStatus())) {
            log.debug("拒绝认证：产品不存在或已停用 productKey={}", parts[0]);
            return false;
        }
        Device device;
        try {
            device = deviceService.getDeviceByKey(parts[1]);
        } catch (Exception e) {
            log.debug("拒绝认证：设备不存在 deviceKey={}", parts[1]);
            return false;
        }
        if (device == null || !product.getId().equals(device.getProductId())) {
            log.debug("拒绝认证：设备不属于该产品 deviceKey={}", parts[1]);
            return false;
        }
        if (device.getEnabled() == null || device.getEnabled() != 1) {
            log.debug("拒绝认证：设备已被禁用 deviceKey={}", parts[1]);
            return false;
        }
        return deviceSecretService.matches(password, device.getDeviceSecretHash());
    }
}