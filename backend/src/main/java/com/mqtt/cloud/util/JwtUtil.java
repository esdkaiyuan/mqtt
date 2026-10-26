package com.mqtt.cloud.util;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * <p>
 * 负责 Token 的签发与解析。签名密钥在首次使用时构建并缓存，避免每次请求重复派生。
 */
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_MFA = "mfa";

    /** 二步验证临时令牌有效期（毫秒）：只在密码校验通过后、动态口令校验前有效。 */
    private static final long MFA_TOKEN_TTL_MS = 300_000L;

    private final String secret;
    private final long expiration;

    private volatile SecretKey signingKey;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration}") long expiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT_SECRET：请在环境变量或 .env 中设置至少 32 字节的签名密钥");
        }
        if (resolveKeyBytes(secret).length < 32) {
            throw new IllegalStateException("JWT_SECRET 解码后必须至少 32 字节");
        }
        this.secret = secret;
        this.expiration = expiration;
    }

    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(signingKey())
                .compact();
    }

    /**
     * 签发二步验证临时令牌（T-58）：密码已通过、等待动态口令。
     * <p>
     * 带 {@code mfa=true} 声明，防止把普通访问令牌当二步令牌使用；
     * 有效期固定 5 分钟，与访问令牌分离。
     */
    public String generateMfaToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_MFA, true)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + MFA_TOKEN_TTL_MS))
                .signWith(signingKey())
                .compact();
    }

    /** 是否为二步验证临时令牌。 */
    public boolean isMfaToken(Claims claims) {
        return Boolean.TRUE.equals(claims.get(CLAIM_MFA, Boolean.class));
    }

    /**
     * 解析并校验 Token，失败时抛出对应的业务异常。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public String getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public long getExpirationSeconds() {
        return expiration / 1000;
    }

    private SecretKey signingKey() {
        SecretKey key = this.signingKey;
        if (key == null) {
            synchronized (this) {
                key = this.signingKey;
                if (key == null) {
                    key = Keys.hmacShaKeyFor(resolveKeyBytes());
                    this.signingKey = key;
                }
            }
        }
        return key;
    }

    private byte[] resolveKeyBytes() {
        return resolveKeyBytes(secret);
    }

    private static byte[] resolveKeyBytes(String value) {
        try {
            return Decoders.BASE64.decode(value);
        } catch (Exception e) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}