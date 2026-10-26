package com.mqtt.cloud.util;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP 动态口令工具（T-58，RFC 6238 / RFC 4226）。
 * <p>
 * 只依赖 JDK：HMAC-SHA1 + Base32 + 动态截断，不引入额外认证库。
 * 纯函数 / 无状态，便于单测；密钥与口令比较使用常量时间算法。
 */
public final class TotpUtil {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    /** 生成 Base32 密钥（默认 20 字节 → 32 字符，authenticator 可直接录入）。 */
    public static String generateSecret(int lengthBytes) {
        int length = lengthBytes < 10 ? 20 : lengthBytes;
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** 当前时间下的口令校验，容忍 {@code window} 个前后时间步。 */
    public static boolean verify(String base32Secret, String code,
                                 int timeStepSeconds, int window, int digits) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String normalized = code.replace(" ", "").trim();
        if (normalized.length() != digits) {
            return false;
        }
        long step = Math.max(timeStepSeconds, 1);
        long currentCounter = System.currentTimeMillis() / 1000L / step;
        int tolerance = Math.max(window, 0);
        for (long counter = currentCounter - tolerance; counter <= currentCounter + tolerance; counter++) {
            if (counter < 0) {
                continue;
            }
            String expected = codeAt(base32Secret, counter, digits);
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    normalized.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /** 计算指定计数器的口令（测试与排障用）。 */
    public static String codeAt(String base32Secret, long counter, int digits) {
        byte[] key = base32Decode(base32Secret);
        if (key.length == 0) {
            return "";
        }
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int modulus = (int) Math.pow(10, Math.max(digits, 1));
            return String.format("%0" + Math.max(digits, 1) + "d", binary % modulus);
        } catch (Exception e) {
            return "";
        }
    }

    /** authenticator 识别的 otpauth URI。 */
    public static String otpauthUri(String issuer, String account, String secret,
                                    int digits, int timeStepSeconds) {
        String label = urlEncode(issuer) + ":" + urlEncode(account);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + Math.max(digits, 1)
                + "&period=" + Math.max(timeStepSeconds, 1);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    /** RFC 4648 Base32 编码（无填充，便于人工录入）。 */
    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                out.append(BASE32_ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            out.append(BASE32_ALPHABET.charAt(index));
        }
        return out.toString();
    }

    /** Base32 解码（忽略空白与填充；非法字符返回空数组）。 */
    static byte[] base32Decode(String value) {
        if (value == null) {
            return new byte[0];
        }
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase();
        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[normalized.length() * 5 / 8];
        int outIndex = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int index = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (index < 0) {
                return new byte[0];
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[outIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        byte[] result = new byte[outIndex];
        System.arraycopy(out, 0, result, 0, outIndex);
        return result;
    }
}