package com.mqtt.cloud.util;

import java.security.SecureRandom;
import java.math.BigInteger;

/**
 * API密钥生成工具
 * 生成64位十六进制随机字符串作为API Key
 */
public class ApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成64位十六进制随机密钥
     */
    public static String generateKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return String.format("%064x", new BigInteger(1, bytes));
    }

    /**
     * 生成可读前缀（用于显示，如 ak_XXXXXXXX）
     */
    public static String generatePrefix() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return "ak_" + String.format("%08x", new BigInteger(1, bytes));
    }
}
