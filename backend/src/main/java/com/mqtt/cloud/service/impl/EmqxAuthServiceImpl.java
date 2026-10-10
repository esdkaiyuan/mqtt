package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.constant.AuthModeValue;
import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCertificateService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceTokenService;
import com.mqtt.cloud.service.EmqxAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * EMQX 认证回调实现（T-25 起承载三种认证方式）。
 * <p>
 * 三条通路共用同一段准入前置判定：用户名解析、产品与设备存在性、产品启用与设备启用。
 * 差别只在「凭据如何验证」一步，因此停用产品与禁用设备对三种方式同时生效，
 * 与 ACL 回调的判定同源，不会出现「认证拒绝、授权放行」的语义分叉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmqxAuthServiceImpl implements EmqxAuthService {

    private final AccessControlProperties properties;
    private final DeviceSecretService deviceSecretService;
    private final DeviceAccessGuard deviceAccessGuard;
    private final DeviceCertificateService deviceCertificateService;
    private final DeviceTokenService deviceTokenService;

    @Override
    public boolean authenticate(String username, String password, String clientId,
                                String certSubject, String certCommonName) {
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
        return verifyCredential(resolveAuthMode(meta), username, password, certCommonName, meta);
    }

    /**
     * 按产品认证方式验证凭据。
     * <p>
     * 认证方式为空的记录按 {@code SECRET} 处理：新增列对存量数据有默认值，
     * 但缓存快照可能来自尚未回填的旧记录，回落可避免认证整体不可用。
     */
    private boolean verifyCredential(String authMode, String username, String password,
                                     String certCommonName, DeviceAuthCacheService.AuthMeta meta) {
        if (AuthModeValue.X509.equals(authMode)) {
            return verifyByCertificate(username, certCommonName, meta);
        }
        if (AuthModeValue.JWT.equals(authMode)) {
            // JWT 模式下口令位承载设备令牌，验签与主体比对在令牌服务内完成
            return deviceTokenService.verify(password, username);
        }
        // BCrypt 校验不可缓存：缓存只消除 DB 查询，比较必须逐次计算
        return deviceSecretService.matches(password, meta.secretHash());
    }

    /**
     * X.509 分支以证书 CN 判定归属。
     * <p>
     * 签发接口把 CN 固定为 {@code {productKey}.{deviceKey}}，与 EMQX 传入的用户名同源，
     * 因此逐字比对即可确认证书属于该设备；再查一次吊销态排除已吊销或已过期的证书。
     * 非 TLS 连接下 EMQX 传入空串，直接拒入而非退化为口令校验。
     */
    private boolean verifyByCertificate(String username, String certCommonName,
                                       DeviceAuthCacheService.AuthMeta meta) {
        if (!StringUtils.hasText(certCommonName) || !certCommonName.equals(username)) {
            log.debug("拒绝认证：证书 CN 与用户名不符 username={}, certCommonName={}", username, certCommonName);
            return false;
        }
        if (deviceCertificateService.isRevoked(meta.deviceId())) {
            log.debug("拒绝认证：设备证书已吊销或已过期 deviceId={}", meta.deviceId());
            return false;
        }
        return true;
    }

    private String resolveAuthMode(DeviceAuthCacheService.AuthMeta meta) {
        String authMode = meta.authMode();
        return StringUtils.hasText(authMode) ? authMode : AuthModeValue.SECRET;
    }
}
