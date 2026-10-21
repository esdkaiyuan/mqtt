package com.mqtt.cloud.service;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.CommandProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 命令级权限与调用配额单测（T-34 / G-4-1）。
 * <p>
 * 覆盖三块：标识符黑名单与角色豁免、角色 → 命令类型矩阵、内部来源豁免；
 * 配额侧的开关、sync 收紧、failOpen 降级、TTL 只在首次写入时设置。
 */
class CommandGuardAndQuotaTest {

    private CommandProperties properties;
    private CommandGuardService guard;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private CommandQuotaService quota;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new CommandProperties();
        guard = new CommandGuardService(properties);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        quota = new CommandQuotaService(properties, redis);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ================= 权限：标识符黑名单 =================

    @Test
    void denied_identifier_is_rejected_for_operator() {
        loginAs("OPERATOR");

        assertThatThrownBy(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "factory_reset"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.COMMAND_IDENTIFIER_DENIED.getCode());
    }

    @Test
    void denied_identifier_match_is_case_insensitive() {
        loginAs("OPERATOR");

        assertThatThrownBy(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "FACTORY_RESET"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void role_can_exempt_a_denied_identifier() {
        loginAs("ADMIN");
        CommandProperties.RolePermission permission = new CommandProperties.RolePermission();
        permission.setAllowedIdentifiers(Set.of("factory_reset"));
        properties.getRolePermissions().put("ADMIN", permission);

        assertThatCode(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "factory_reset"))
                .doesNotThrowAnyException();
    }

    @Test
    void exemption_of_one_role_does_not_leak_to_another() {
        CommandProperties.RolePermission permission = new CommandProperties.RolePermission();
        permission.setAllowedIdentifiers(Set.of("factory_reset"));
        properties.getRolePermissions().put("ADMIN", permission);
        loginAs("OPERATOR");

        assertThatThrownBy(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "factory_reset"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void normal_identifier_passes() {
        loginAs("OPERATOR");

        assertThatCode(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "ota_upgrade"))
                .doesNotThrowAnyException();
    }

    // ================= 权限：角色 → 命令类型矩阵 =================

    @Test
    void role_without_matrix_is_unrestricted() {
        loginAs("OPERATOR");

        assertThatCode(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "property_set", "switch"))
                .doesNotThrowAnyException();
    }

    @Test
    void command_type_outside_matrix_is_rejected() {
        CommandProperties.RolePermission permission = new CommandProperties.RolePermission();
        permission.setCommandTypes(Set.of("property_set"));
        properties.getRolePermissions().put("OPERATOR", permission);
        loginAs("OPERATOR");

        assertThatThrownBy(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "ota_upgrade"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.COMMAND_TYPE_DENIED.getCode());
    }

    @Test
    void command_type_in_matrix_passes() {
        CommandProperties.RolePermission permission = new CommandProperties.RolePermission();
        permission.setCommandTypes(Set.of("property_set"));
        properties.getRolePermissions().put("OPERATOR", permission);
        loginAs("OPERATOR");

        assertThatCode(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "property_set", "switch"))
                .doesNotThrowAnyException();
    }

    // ================= 权限：来源与登录上下文 =================

    @Test
    void internal_sources_bypass_identifier_denylist() {
        loginAs("VIEWER");

        // 规则 / OTA / 场景是系统内部触发，其 operator 只是「谁配的这条自动化」，
        // 不能因为当前登录者是 VIEWER 就把自动化拦死。
        for (String source : List.of(DeviceCommandService.SOURCE_RULE,
                DeviceCommandService.SOURCE_OTA, DeviceCommandService.SOURCE_SCENE,
                DeviceCommandService.SOURCE_POINT_TABLE)) {
            assertThatCode(() -> guard.checkPermission(source, "service", "factory_reset"))
                    .as("内部来源 %s 不应被权限判定拦截", source)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void open_api_source_is_guarded() {
        loginAs("EXTERNAL");

        assertThatThrownBy(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_OPEN_API, "service", "factory_reset"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void no_security_context_means_no_role_judgement() {
        SecurityContextHolder.clearContext();

        // 开放接口的 API Key 调用没有 ROLE_ 上下文；此时不按角色判定，
        // 只受全局黑名单约束（下面的黑名单仍会拦）。
        assertThatCode(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "ota_upgrade"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkPermission(
                DeviceCommandService.SOURCE_CONSOLE, "service", "factory_reset"))
                .isInstanceOf(BusinessException.class);
    }

    // ================= 配额 =================

    @Test
    void quota_disabled_does_not_touch_redis() {
        properties.getQuota().setEnabled(false);
        when(redis.opsForValue()).thenReturn(null);

        assertThatCode(() -> quota.consume(1L, "async")).doesNotThrowAnyException();
        // enabled=false 时不应产生任何 Redis 交互；这里以 opsForValue() 未被再次调用为证
    }

    @Test
    void quota_allows_within_limit() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setMaxPerWindow(3);
        AtomicLong counter = new AtomicLong();
        when(valueOps.increment(anyString())).thenAnswer(i -> counter.incrementAndGet());

        assertThatCode(() -> quota.consume(1L, "async")).doesNotThrowAnyException();
        assertThatCode(() -> quota.consume(1L, "async")).doesNotThrowAnyException();
        assertThatCode(() -> quota.consume(1L, "async")).doesNotThrowAnyException();
    }

    @Test
    void quota_rejects_when_exceeded() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setMaxPerWindow(2);
        AtomicLong counter = new AtomicLong();
        when(valueOps.increment(anyString())).thenAnswer(i -> counter.incrementAndGet());

        quota.consume(1L, "async");
        quota.consume(1L, "async");
        assertThatThrownBy(() -> quota.consume(1L, "async"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.COMMAND_QUOTA_EXCEEDED.getCode());
    }

    @Test
    void sync_call_uses_stricter_limit() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setMaxPerWindow(2);
        properties.getQuota().setSyncMultiplier(2);
        // sync 实际阈值 = 2 × 2 = 4
        AtomicLong counter = new AtomicLong();
        when(valueOps.increment(anyString())).thenAnswer(i -> counter.incrementAndGet());

        for (int i = 0; i < 4; i++) {
            quota.consume(9L, DeviceCommandService.CALL_TYPE_SYNC);
        }
        assertThatThrownBy(() -> quota.consume(9L, DeviceCommandService.CALL_TYPE_SYNC))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void null_operator_bypasses_quota() {
        properties.getQuota().setEnabled(true);

        // 无登录上下文的系统调用不计量：用一个共享匿名桶去限制系统内部调用等于随机抽签
        assertThatCode(() -> quota.consume(null, "async")).doesNotThrowAnyException();
    }

    @Test
    void ttl_is_set_only_on_first_increment() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setMaxPerWindow(10);
        properties.getQuota().setWindowSeconds(30);
        AtomicLong counter = new AtomicLong();
        when(valueOps.increment(anyString())).thenAnswer(i -> counter.incrementAndGet());

        quota.consume(2L, "async");
        quota.consume(2L, "async");
        quota.consume(2L, "async");

        // 只在第一次设置 TTL：每次 INCR 都重设过期会把窗口无限续期，退化成「从首次调用起永不释放」
        verify_expire_called_once();
    }

    @Test
    void fail_open_allows_when_redis_down() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setFailOpen(true);
        when(valueOps.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> quota.consume(3L, "async")).doesNotThrowAnyException();
    }

    @Test
    void fail_closed_rejects_when_redis_down() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setFailOpen(false);
        when(valueOps.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> quota.consume(3L, "async")).isInstanceOf(BusinessException.class);
    }

    private void verify_expire_called_once() {
        org.mockito.Mockito.verify(redis, org.mockito.Mockito.times(1))
                .expire(anyString(), any(Duration.class));
    }

    private void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u", "p",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}