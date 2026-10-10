package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.AuthModeValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.AuthExtensionProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceTokenService;
import com.mqtt.cloud.service.ProductService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 设备令牌服务实现（T-25）。
 * <p>
 * 令牌使用 HS256 与 {@code app.auth-extension.jwt.secret} 派生的密钥签名，
 * 有效期取 {@code app.auth-extension.jwt.ttl-seconds}。
 * 密钥按请求从配置派生，不缓存，因而轮换密钥后下一次签发与校验立即生效；
 * 派生成本为一次字节拷贝，无需为此增加失效机制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceMapper deviceMapper;
    private final ProductService productService;
    private final DeviceSecretService deviceSecretService;
    private final AuthExtensionProperties authExtensionProperties;

    @Override
    public DeviceToken issueToken(Long deviceId) {
        Device device = requireDevice(deviceId);
        Product product = productService.requireById(device.getProductId());
        AuthExtensionProperties.Jwt jwt = authExtensionProperties.getJwt();
        if (!jwt.isEnabled() || !AuthModeValue.JWT.equals(product.getAuthMode())) {
            // 开关未开与产品未选 JWT 同属「该模式不可签发」，不区分提示以免暴露部署配置
            throw new BusinessException(ResultCode.AUTH_MODE_UNSUPPORTED);
        }
        try {
            return sign(device, product, jwt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("设备令牌签发失败，deviceId={}", deviceId, e);
            throw new BusinessException(ResultCode.DEVICE_TOKEN_ISSUE_FAILED);
        }
    }

    @Override
    public boolean verify(String token, String username) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(username)) {
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return username.equals(claims.getSubject());
        } catch (Exception e) {
            // 签名不符、过期、格式非法与密钥不可用一律拒入，与认证回调的 deny 语义一致
            log.debug("设备令牌校验失败：{}", e.getMessage());
            return false;
        }
    }

    private DeviceToken sign(Device device, Product product, AuthExtensionProperties.Jwt jwt) {
        String username = deviceSecretService.buildUsername(product.getProductKey(), device.getDeviceKey());
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwt.getTtlSeconds(), ChronoUnit.SECONDS);
        String token = Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
        log.info("设备令牌已签发，deviceId={}，expiresAt={}", device.getId(), expiresAt);
        return new DeviceToken(device.getId(), username, token,
                LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
    }

    private Device requireDevice(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || Integer.valueOf(1).equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(resolveKeyBytes(authExtensionProperties.getJwt().getSecret()));
    }

    /**
     * 密钥材料按 Base64 优先解析，失败时回落 UTF-8 原文，
     * 与 {@code JwtUtil} 保持同一套取值口径，运维无需关心密钥书写形式。
     */
    private byte[] resolveKeyBytes(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(ResultCode.DEVICE_TOKEN_ISSUE_FAILED);
        }
        try {
            return Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
