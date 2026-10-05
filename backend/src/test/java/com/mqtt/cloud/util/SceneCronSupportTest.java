package com.mqtt.cloud.util;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 定时触发 cron 解析适配单测（T-23 实施计划 P7）。
 * <p>
 * 设计文档 / 前端表单统一约定 **5 字段（分钟级）**，而 Spring {@link CronExpression} 要求 6 字段：
 * 本工具把 5 字段补秒为 {@code 0 <cron>}；6 字段原样透传；空值 / 非法字段数抛
 * {@link IllegalArgumentException}。
 */
class SceneCronSupportTest {

    @Test
    void should_parse_five_field_cron_as_minute_level() {
        // 5 字段「分 时 日 月 周」补秒位 0 → 每分钟第 0 秒
        CronExpression expression = SceneCronSupport.parse("0 8 * * *");

        LocalDateTime next = expression.next(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
    }

    @Test
    void should_fire_every_minute_for_star_cron() {
        CronExpression expression = SceneCronSupport.parse("* * * * *");

        LocalDateTime next = expression.next(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 1));
    }

    @Test
    void should_honour_step_expression_in_five_field_cron() {
        CronExpression expression = SceneCronSupport.parse("*/5 * * * *");

        // 上一分钟为 00:04 → 下一次命中 00:05
        LocalDateTime next = expression.next(LocalDateTime.of(2026, 1, 1, 0, 4));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 5));
    }

    @Test
    void should_tolerate_six_field_cron_with_seconds() {
        CronExpression expression = SceneCronSupport.parse("30 0 8 * * *");

        LocalDateTime next = expression.next(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0, 30));
    }

    @Test
    void should_trim_surrounding_whitespace() {
        CronExpression expression = SceneCronSupport.parse("  0 8 * * *  ");

        assertThat(expression.next(LocalDateTime.of(2026, 1, 1, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
    }

    @Test
    void should_reject_blank_cron() {
        assertThatThrownBy(() -> SceneCronSupport.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SceneCronSupport.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_illegal_field_count() {
        assertThatThrownBy(() -> SceneCronSupport.parse("not-a-cron"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SceneCronSupport.parse("1 2 3 4 5 6 7"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
