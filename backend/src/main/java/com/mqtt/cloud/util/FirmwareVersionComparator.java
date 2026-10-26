package com.mqtt.cloud.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 固件版本比较（T-54）。
 * <p>
 * 现场版本号并不严格遵循语义化版本（{@code 1.2.3}），还会出现 {@code v1.2}、{@code 1.0.0-rc1}、
 * {@code 2.0.0+build7} 等写法。这里采用「按数字段 / 非数字段交替比较」的宽松规则：
 * <ul>
 *   <li>数字段按数值比较（{@code 1.2.10 > 1.2.9}），避免字符串比较把 10 排在 9 前面；</li>
 *   <li>{@code v} / {@code V} 前缀与 {@code +build} 元数据忽略；</li>
 *   <li>{@code 1.0} 与 {@code 1.0.0} 视为相等（缺失数字段按 0）；</li>
 *   <li>非数字后缀视为预发布版本：{@code 1.0.0-beta < 1.0.0}，{@code -rc1 < -rc2}。</li>
 * </ul>
 * 纯函数、无状态；无法解析的版本按字符串兜底比较，保证不抛异常。
 */
public final class FirmwareVersionComparator {

    private FirmwareVersionComparator() {
    }

    /**
     * @return 负数表示 {@code left < right}，0 表示相等，正数表示 {@code left > right}
     */
    public static int compare(String left, String right) {
        List<Token> leftTokens = tokenize(left);
        List<Token> rightTokens = tokenize(right);
        int common = Math.min(leftTokens.size(), rightTokens.size());
        for (int i = 0; i < common; i++) {
            Token l = leftTokens.get(i);
            Token r = rightTokens.get(i);
            int cmp = compareToken(l, r);
            if (cmp != 0) {
                return cmp;
            }
        }
        if (leftTokens.size() == rightTokens.size()) {
            return 0;
        }
        if (leftTokens.size() > rightTokens.size()) {
            return compareExtra(leftTokens, common);
        }
        return -compareExtra(rightTokens, common);
    }

    /** 候选版本是否低于当前版本（用于降级拦截）。 */
    public static boolean isDowngrade(String candidate, String current) {
        if (candidate == null || candidate.isBlank() || current == null || current.isBlank()) {
            return false;
        }
        return compare(candidate, current) < 0;
    }

    private static int compareToken(Token left, Token right) {
        if (left.numeric && right.numeric) {
            return new BigInteger(left.text).compareTo(new BigInteger(right.text));
        }
        // 数字段优先于非数字段：1.0.1 > 1.0.0-beta
        if (left.numeric != right.numeric) {
            return left.numeric ? 1 : -1;
        }
        return left.text.compareToIgnoreCase(right.text);
    }

    /**
     * 一侧 token 已耗尽：剩余数字段按 0 补齐（1.0 == 1.0.0），
     * 剩余非数字段视为预发布后缀（1.0.0-beta < 1.0.0）。
     */
    private static int compareExtra(List<Token> longer, int from) {
        for (int i = from; i < longer.size(); i++) {
            Token token = longer.get(i);
            if (!token.numeric) {
                return -1;
            }
            if (new BigInteger(token.text).signum() != 0) {
                return 1;
            }
        }
        return 0;
    }

    private static List<Token> tokenize(String raw) {
        if (raw == null) {
            return List.of();
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        if (value.charAt(0) == 'v' || value.charAt(0) == 'V') {
            value = value.substring(1);
        }
        int metadata = value.indexOf('+');
        if (metadata >= 0) {
            value = value.substring(0, metadata);
        }

        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            char c = value.charAt(index);
            if (isSeparator(c)) {
                index++;
                continue;
            }
            boolean numeric = Character.isDigit(c);
            int start = index;
            while (index < value.length()) {
                char current = value.charAt(index);
                if (isSeparator(current) || Character.isDigit(current) != numeric) {
                    break;
                }
                index++;
            }
            tokens.add(new Token(numeric, value.substring(start, index)));
        }
        return tokens;
    }

    private static boolean isSeparator(char c) {
        return c == '.' || c == '-' || c == '_';
    }

    private record Token(boolean numeric, String text) {
    }
}