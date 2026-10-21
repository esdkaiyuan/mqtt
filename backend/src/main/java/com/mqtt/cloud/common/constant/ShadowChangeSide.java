package com.mqtt.cloud.common.constant;

import java.util.Set;

/**
 * 设备影子变更侧（T-45）。
 * <p>
 * {@code REPORTED} / {@code DESIRED} 是真实变更侧；{@code ANY} 仅作为场景触发配置的「不限侧」，
 * 不会出现在影子变更事件中。
 */
public final class ShadowChangeSide {

    public static final String REPORTED = "REPORTED";
    public static final String DESIRED = "DESIRED";
    public static final String ANY = "ANY";

    /** 场景触发配置允许的全部取值（含不限侧）。 */
    public static final Set<String> ALL = Set.of(REPORTED, DESIRED, ANY);

    /** 影子变更事件允许的取值（不含不限侧）。 */
    public static final Set<String> EVENT_SIDES = Set.of(REPORTED, DESIRED);

    private ShadowChangeSide() {
    }
}
