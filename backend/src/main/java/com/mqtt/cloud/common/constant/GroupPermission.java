package com.mqtt.cloud.common.constant;

import java.util.Set;

/**
 * 分组成员共享权限（T-48）。
 * <p>
 * {@code OWNER} 不落库，仅用于访问判定结果；可持久化的共享权限只有 {@code VIEW} / {@code OPERATE}。
 */
public final class GroupPermission {

    public static final String VIEW = "VIEW";
    public static final String OPERATE = "OPERATE";
    public static final String OWNER = "OWNER";
    public static final String NONE = "NONE";

    /** 可持久化的共享权限。 */
    public static final Set<String> SHARED = Set.of(VIEW, OPERATE);
    /** 全部访问级别（含所有者）。 */
    public static final Set<String> LEVELS = Set.of(VIEW, OPERATE, OWNER);

    private GroupPermission() {
    }

    public static int rank(String permission) {
        if (OWNER.equals(permission)) {
            return 3;
        }
        if (OPERATE.equals(permission)) {
            return 2;
        }
        if (VIEW.equals(permission)) {
            return 1;
        }
        return 0;
    }

    public static String max(String first, String second) {
        return rank(first) >= rank(second) ? first : second;
    }

    public static boolean canView(String level) {
        return rank(level) >= rank(VIEW);
    }

    public static boolean canOperate(String level) {
        return rank(level) >= rank(OPERATE);
    }
}
