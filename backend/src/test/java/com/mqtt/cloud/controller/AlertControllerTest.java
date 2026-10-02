package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.dto.request.AlertRuleRequest;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.AlertRuleService;
import com.mqtt.cloud.service.AlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警中心控制台接口单测（T-17 实施计划 P7）。
 * <p>
 * 聚焦「当前用户透传 + 参数下传 + 结果封装」，鉴权注解与 HTTP 状态码由
 * {@code SecurityConfig} 与全局异常处理器保证。
 */
@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long ALERT_ID = 5L;

    @Mock
    private AlertRuleService alertRuleService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listRules_should_pass_filters_for_current_user() {
        authenticate();
        AlertRule rule = new AlertRule();
        rule.setId(3L);
        when(alertRuleService.list(USER_ID, "THRESHOLD", true)).thenReturn(List.of(rule));

        Result<List<AlertRule>> result = controller.listRules("THRESHOLD", true);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(rule);
    }

    @Test
    void createRule_should_delegate_with_current_user() {
        authenticate();
        AlertRuleRequest request = new AlertRuleRequest();
        AlertRule created = new AlertRule();
        when(alertRuleService.create(USER_ID, request)).thenReturn(created);

        assertThat(controller.createRule(request).getData()).isSameAs(created);
    }

    @Test
    void getRule_should_delegate() {
        authenticate();
        AlertRule rule = new AlertRule();
        when(alertRuleService.getOwned(USER_ID, 3L)).thenReturn(rule);

        assertThat(controller.getRule(3L).getData()).isSameAs(rule);
    }

    @Test
    void updateRule_should_delegate() {
        authenticate();
        AlertRuleRequest request = new AlertRuleRequest();
        AlertRule updated = new AlertRule();
        when(alertRuleService.update(USER_ID, 3L, request)).thenReturn(updated);

        assertThat(controller.updateRule(3L, request).getData()).isSameAs(updated);
    }

    @Test
    void deleteRule_should_delegate() {
        authenticate();

        controller.deleteRule(3L);

        verify(alertRuleService).delete(USER_ID, 3L);
    }

    @Test
    void pageAlerts_should_delegate_with_query() {
        authenticate();
        AlertQuery query = new AlertQuery();
        when(alertService.page(eq(USER_ID), any())).thenReturn(new Page<>(1, 10));

        IPage<AlertRecord> page = controller.pageAlerts(query).getData();

        assertThat(page).isNotNull();
        verify(alertService).page(USER_ID, query);
    }

    @Test
    void getAlert_should_delegate() {
        authenticate();
        AlertRecord record = new AlertRecord();
        when(alertService.getOwned(USER_ID, ALERT_ID)).thenReturn(record);

        assertThat(controller.getAlert(ALERT_ID).getData()).isSameAs(record);
    }

    @Test
    void acknowledge_should_delegate() {
        authenticate();

        controller.acknowledge(ALERT_ID);

        verify(alertService).acknowledge(USER_ID, ALERT_ID);
    }

    @Test
    void recover_should_delegate() {
        authenticate();

        controller.recover(ALERT_ID);

        verify(alertService).recover(USER_ID, ALERT_ID);
    }

    @Test
    void unreadCount_should_return_current_user_count() {
        authenticate();
        when(alertService.unreadCount(USER_ID)).thenReturn(2L);

        assertThat(controller.unreadCount().getData()).isEqualTo(2L);
    }

    @Test
    void recentAlerts_should_delegate_with_limit() {
        authenticate();
        AlertRecord record = new AlertRecord();
        when(alertService.recent(USER_ID, 10)).thenReturn(List.of(record));

        assertThat(controller.recentAlerts(10).getData()).containsExactly(record);
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}