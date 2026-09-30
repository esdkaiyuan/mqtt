package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.EmqxAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmqxAuthServiceImpl implements EmqxAuthService {

    private final AccessControlProperties properties;
    private final DeviceSecretService deviceSecretService;
    private final DeviceAccessGuard deviceAccessGuard;

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

        // 准入判定与 ACL 回调同源（DeviceAccessGuard），避免「认证拒绝、授权放行」的语义分叉
        DeviceAuthCacheService.AuthMeta meta = deviceAccessGuard.resolve(productKey, deviceKey);
        if (meta == null) {
            log.debug("拒绝认证：产品或设备不存在 productKey={}, deviceKey={}", productKey, deviceKey);
            return false;
        }
        if (!deviceAccessGuard.isPermitted(meta)) {
            log.debug("拒绝认证：产品停用或设备禁用 productKey={}, deviceKey={}, productStatus={}, deviceEnabled={}",
                    productKey, deviceKey, meta.productStatus(), meta.deviceEnabled());
            return false;
        }
        // BCrypt 校验不可缓存：缓存只消除 DB 查询，比较必须逐次计算
        return deviceSecretService.matches(password, meta.secretHash());
    }
}