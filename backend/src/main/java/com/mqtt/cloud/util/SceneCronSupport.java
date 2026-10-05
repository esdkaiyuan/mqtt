package com.mqtt.cloud.util;

import org.springframework.scheduling.support.CronExpression;

/**
 * 场景定时触发 cron 解析适配（T-23 设计文档 §2.1 / §8.2）。
 * <p>
 * 设计文档、数据库列注释、部署手册与前端表单统一约定 **5 字段 cron（分钟级）**，
 * 字段顺序为「分 时 日 月 周」，例如 {@code *&#47;5 * * * *}、{@code 0 8 * * *}。
 * <p>
 * 但 Spring 的 {@link CronExpression} 要求 **6 字段（含秒）**，直接解析 5 字段会抛
 * {@code IllegalArgumentException: Cron expression must consist of 6 fields}，
 * 导致保存期被 {@code 6241} 拒收、运行期定时场景永不命中。
 * <p>
 * 本工具在解析前按字段数归一：5 字段补前导秒位 {@code 0}（即“每分钟第 0 秒触发”，
 * 保持分钟级语义不变）；6 字段原样交由 Spring 解析以兼容显式带秒的表达式；
 * 其余字段数交由 Spring 抛错，错误信息原样透出。校验（{@code SceneValidator}）与
 * 命中（{@code SceneMatcher}）两处**共用同一入口**，避免口径再次漂移。
 */
public final class SceneCronSupport {

    private SceneCronSupport() {
    }

    /**
     * 解析 cron 表达式：5 字段按分钟级补秒后交由 Spring 解析。
     *
     * @param cron 原始表达式（约定 5 字段；亦兼容 6 字段）
     * @return Spring {@link CronExpression}
     * @throws IllegalArgumentException 表达式为空或非法（含字段数不为 5 / 6）
     */
    public static CronExpression parse(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("cron 表达式不能为空");
        }
        String normalized = cron.trim();
        if (normalized.split("\\s+").length == 5) {
            // 补秒位：分钟级语义等价于“每分钟第 0 秒”
            normalized = "0 " + normalized;
        }
        return CronExpression.parse(normalized);
    }
}
