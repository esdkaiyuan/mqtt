package com.mqtt.cloud.common.security;

import com.mqtt.cloud.config.MfaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * MFA 密钥加密器（T-58）。
 * <p>
 * TOTP 密钥等价于第二因子，绝不明文落库：用 AES-256-GCM 加密后以 Base64 存入
 * {@code sys_user.mfa_secret}。GCM 同时提供完整性校验，密文被篡改时解密直接失败。
 * <p>
 * 密钥来源：{@code app.mfa.encryption-key} 优先，否则由 {@code JWT_SECRET} 派生
 * （派生口径 {@code SHA-256(JWT_SECRET + ":mfa")}）。轮换 {@code JWT_SECRET} 会使历史密文无法解密，
 * 需引导用户重新绑定 MFA。
 */
@Component
public class MfaCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String DERIVE_SUFFIX = ":mfa";

    private final MfaProperties properties;
    private final String jwtSecret;
    private final SecureRandom random = new SecureRandom();

    public MfaCrypto(@Value("${app.jwt.secret}") String jwtSecret, MfaProperties properties) {
        this.jwtSecret = jwtSecret;
        this.properties = properties;
    }

    /** 加密为 Base64（IV || 密文 || Tag）。 */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, payload, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("MFA 密钥加密失败", e);
        }
    }

    /** 解密 Base64 密文；失败（密钥不匹配 / 密文篡改）返回 {@code null}。 */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKeySpec key() {
        byte[] raw = resolveKeyBytes();
        return new SecretKeySpec(raw, "AES");
    }

    private byte[] resolveKeyBytes() {
        String configured = properties.getEncryptionKey();
        byte[] raw;
                if (configured == null || configured.isBlank()) {
            raw = sha256(jwtSecret + DERIVE_SUFFIX);
        } else {
            try {
                raw = Base64.getDecoder().decode(configured);
            } catch (Exception e) {
                raw = configured.getBytes(StandardCharsets.UTF_8);
            }
            // AES-256 只接受 16/24/32 字节密钥；任意长度统一收敛为 32 字节派生值，
            // 避免用户填了 41 字节却得到 InvalidKeyException
            if (raw.length != 32) {
                raw = sha256(raw);
            }
        }
        return raw;
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private byte[] sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}