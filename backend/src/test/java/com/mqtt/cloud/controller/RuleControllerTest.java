package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.ExecutionQueryDTO;
import com.mqtt.cloud.dto.request.RuleQueryDTO;
import com.mqtt.cloud.dto.request.RuleRequest;
import com.mqtt.cloud.dto.request.RuleTestRequest;
import com.mqtt.cloud.dto.response.RuleTestResult;
import com.mqtt.cloud.entity.RuleDefinition;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.RuleExecutionService;
import com.mqtt.cloud.service.RuleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消息规则控制台接口单测（T-19 实施计划 P7）。
 * <p>
 * 聚焦「当前用户透传 + 参数下传 + 结果封装 + 重试状态冲突透传」；鉴权注解与 HTTP 状态码由
 * {@code SecurityConfig} 与全局异常处理器保证。
 */
@ExtendWith(MockitoExtension.class)
class RuleControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long RULE_ID = 5L;
    private static final Long EXECUTION_ID = 900L;

    @Mock
    private RuleService ruleService;

    @Mock
    private RuleExecutionService ruleExecutionService;

    @InjectMocks
    private RuleController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listRules_should_delegate_with_query() {
        authenticate();
        RuleQueryDTO query = new RuleQueryDTO();
        when(ruleService.page(eq(USER_ID), any())).thenReturn(new Page<>(1, 10));

        IPage<RuleDefinition> page = controller.listRules(query).getData();

        assertThat(page).isNotNull();
        verify(ruleService).page(USER_ID, query);
    }

    @Test
    void createRule_should_delegate_with_current_user() {
        authenticate();
        RuleRequest request = new RuleRequest();
        RuleDefinition created = new RuleDefinition();
        when(ruleService.create(USER_ID, request)).thenReturn(created);

        assertThat(controller.createRule(request).getData()).isSameAs(created);
    }

    @Test
    void getRule_should_delegate() {
        authenticate();
        RuleDefinition rule = new RuleDefinition();
        when(ruleService.getOwned(USER_ID, RULE_ID)).thenReturn(rule);

        assertThat(controller.getRule(RULE_ID).getData()).isSameAs(rule);
    }

    @Test
    void updateRule_should_delegate() {
        authenticate();
        RuleRequest request = new RuleRequest();
        RuleDefinition updated = new RuleDefinition();
        when(ruleService.update(USER_ID, RULE_ID, request)).thenReturn(updated);

        assertThat(controller.updateRule(RULE_ID, request).getData()).isSameAs(updated);
    }

    @Test
    void deleteRule_should_delegate() {
        authenticate();

        controller.deleteRule(RULE_ID);

        verify(ruleService).delete(USER_ID, RULE_ID);
    }

    @Test
    void setRuleEnabled_should_reject_missing_flag() {
        authenticate();

        assertThatThrownBy(() -> controller.setRuleEnabled(RULE_ID, Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.RULE_INVALID.getCode());
        verify(ruleService, never()).setEnabled(any(), any(), any(Boolean.class));
    }

    @Test
    void setRuleEnabled_should_delegate() {
        authenticate();

        controller.setRuleEnabled(RULE_ID, Map.of("enabled", false));

        verify(ruleService).setEnabled(USER_ID, RULE_ID, false);
    }

    @Test
    void testRule_should_delegate() {
        authenticate();
        RuleTestRequest request = new RuleTestRequest();
        RuleTestResult result = new RuleTestResult();
        when(ruleService.test(USER_ID, RULE_ID, request)).thenReturn(result);

        assertThat(controller.testRule(RULE_ID, request).getData()).isSameAs(result);
    }

    @Test
    void listExecutions_should_delegate_with_query() {
        authenticate();
        ExecutionQueryDTO query = new ExecutionQueryDTO();
        when(ruleExecutionService.page(eq(USER_ID), any())).thenReturn(new Page<>(1, 10));

        IPage<RuleExecution> page = controller.listExecutions(query).getData();

        assertThat(page).isNotNull();
        verify(ruleExecutionService).page(USER_ID, query);
    }

    @Test
    void getExecution_should_delegate() {
        authenticate();
        RuleExecution execution = new RuleExecution();
        when(ruleExecutionService.getOwned(USER_ID, EXECUTION_ID)).thenReturn(execution);

        assertThat(controller.getExecution(EXECUTION_ID).getData()).isSameAs(execution);
    }

    @Test
    void retryExecution_should_delegate() {
        authenticate();

        controller.retryExecution(EXECUTION_ID);

        verify(ruleExecutionService).manualRetry(USER_ID, EXECUTION_ID);
    }

    @Test
    void retryExecution_should_propagate_conflict() {
        authenticate();
        doThrow(new BusinessException(ResultCode.RULE_INVALID, HttpStatus.CONFLICT, "状态冲突"))
                .when(ruleExecutionService).manualRetry(USER_ID, EXECUTION_ID);

        assertThatThrownBy(() -> controller.retryExecution(EXECUTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}