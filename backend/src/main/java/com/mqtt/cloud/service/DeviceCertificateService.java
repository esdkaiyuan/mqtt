package com.mqtt.cloud.service;

import java.time.LocalDateTime;

/**
 * 设备证书服务（T-25）。
 * <p>
 * 承载 X.509 通路的证书生命周期：签发、吊销与吊销态查询。
 * 签发返回的明文私钥不落库、不落日志，仅随该次响应返回；
 * 吊销后须立即失效该设备的认证缓存，使下一次回调读到吊销态。
 */
public interface DeviceCertificateService {

    /**
     * 为设备签发证书：要求设备存在且其产品 `auth_mode` 为 `X509`。
     * <p>
     * 以配置的 CA 证书与私钥签发主体为 `CN={productKey}.{deviceKey},O=MQTT Cloud` 的证书，
     * 序列号随机生成，私钥只随返回对象携带。重复签发表明重新签发，写新行，旧证书不自动吊销。
     */
    IssuedCertificate issue(Long deviceId);

    /**
     * 吊销设备的当前有效证书：写入吊销时间与原因，并清空该设备认证缓存。
     * 设备无有效证书行时抛 `DEVICE_CERTIFICATE_REVOKE_FAILED`。
     */
    RevokedCertificate revoke(Long deviceId, String reason);

    /**
     * 判定设备是否处于「无可用证书」状态：无未吊销行、已过期或设备不存在时均返回 true。
     * <p>
     * 认证回调的 X.509 分支据此拒绝接入，与「认证失败一律 deny」的口径一致。
     */
    boolean isRevoked(Long deviceId);

    /** 签发结果：明文私钥仅本次返回，PEM 文本直接交给设备侧。 */
    record IssuedCertificate(Long deviceId,
                             String subject,
                             String serial,
                             LocalDateTime notBefore,
                             LocalDateTime notAfter,
                             String certificatePem,
                             String privateKeyPem) {
    }

    /** 吊销结果。 */
    record RevokedCertificate(Long deviceId,
                              String serial,
                              LocalDateTime revokedAt,
                              String reason) {
    }
}
