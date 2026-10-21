package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 命令下发与服务调用配置（T-15 建，T-34 增权限与配额）。
 * <p>
 * T-34 补齐改造前的两个洞：**控制台命令下发端点没有任何角色限制**（VIEWER 也能下发命令），
 * 以及**用户维度无任何调用配额**（仅开放接口有 API Key 维度限流）。
 * <p>
 * 三层约束，从粗到细：
 * <ol>
 *   <li><b>角色闸门</b> —— 由 {@code SecurityConfig} 对 {@code POST /devices/{deviceKey}/commands}
 *       限定 ADMIN/OPERATOR，本类只管配置化的标识符豁免；</li>
 *   <li><b>命令类型 / 标识符权限</b> —— {@link #rolePermissions} 与 {@link #deniedIdentifiers}；</li>
 *   <li><b>调用配额</b> —— {@link #quota}，按操作者维度固定窗口计数，对齐
 *       {@code RateLimitProperties} 的口径（Redis 键计数 + 固定窗口）。</li>
 * </ol>
 * <p>
 * <b>为什么默认宽松</b>：{@code rolePermissions} 缺省为空表示「不额外限制」，
 * {@code quota.enabled} 默认 {@code false}。与项目其它能力开关一致 ——
 * 未显式配置就强制限流，会把正常调用直接打成 429。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.command")
public class CommandProperties {

    /** 同步调用等待回执的超时（毫秒）。 */
    private long syncTimeoutMs = 5000;

    /** 同步等待的轮询间隔（毫秒）。 */
    private long pollIntervalMs = 200;

    /**
     * 超时巡检间隔（毫秒），0 关闭。
     * <p>巡检把长期停留 {@code PENDING/SENT} 的记录兜底置 {@code TIMEOUT}，
     * 保证异步命令也不会永远停在 {@code SENT}。
     */
    private long timeoutSweepIntervalMs = 30000;

    /** 异步命令判定超时的时长（毫秒）。 */
    private long asyncTimeoutMs = 60000;

    /** 命令参数 JSON 大小上限（字节），防止超大载荷压垮 Broker。 */
    private int maxParamsBytes = 16384;

    // ---------- T-34 · 命令级权限 ----------

    /** 角色 → 命令权限配置，键为角色名（{@code ADMIN} / {@code OPERATOR}）。 */
    private Map<String, RolePermission> rolePermissions = new LinkedHashMap<>();

    /**
     * 全局危险命令标识符黑名单（T-34）。
     * <p>
     * 命中的标识符对**所有角色**拒绝下发，除非该角色在 {@link #rolePermissions} 里显式豁免。
     * 大小写不敏感。默认内置 {@code factory_reset} —— 恢复出厂不可逆，
     * 不该因为「有下发权限」就直接放行。
     */
    private Set<String> deniedIdentifiers = new LinkedHashSet<>(Set.of("factory_reset"));

    /** 单个角色的命令权限配置（T-34）。 */
    @Data
    public static class RolePermission {

        /**
         * 该角色允许下发的命令类型；为空表示「不限类型，仅受全局黑名单约束」。
         * <p>取值：{@code property_set} / {@code service}。缺省即不限，
         * 这样新增角色不必改配置就能用。
         */
        private Set<String> commandTypes = new LinkedHashSet<>();

        /** 该角色可豁免的标识符（从 {@link CommandProperties#deniedIdentifiers} 中挖掉）。 */
        private Set<String> allowedIdentifiers = new LinkedHashSet<>();
    }

    // ---------- T-34 · 调用配额 ----------

    /** 调用配额（T-34）。 */
    private Quota quota = new Quota();

    /** 命令调用配额（T-34）。 */
    @Data
    public static class Quota {

        /** 配额总开关；false 时不限流（与 {@code app.rate-limit} 一致的降级语义）。 */
        private boolean enabled = false;

        /** 固定窗口长度（秒）。 */
        private int windowSeconds = 60;

        /** 单个操作者在一个窗口内的最大命令下发数。 */
        private int maxPerWindow = 600;

        /**
         * 同步调用（{@code callType=sync}）的额外倍数（T-34）。
         * <p>
         * 同步调用会阻塞等待设备回执，代价远高于异步，实际阈值取 {@code 阈值 × 本值}。
         * 默认 1 表示不额外收紧。
         */
        private int syncMultiplier = 1;

        /** Redis 键前缀，与 API Key 限流键空间区分。 */
        private String keyPrefix = "mqtt:quota:command:";

        /** Redis 不可用时是否放行。true = 可用性优先（降级放行），false = 严格限流。 */
        private boolean failOpen = true;
    }
}