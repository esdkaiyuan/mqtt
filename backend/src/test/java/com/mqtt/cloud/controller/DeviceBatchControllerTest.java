package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.request.BatchAssignRequest;
import com.mqtt.cloud.dto.request.BatchCommandRequest;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.response.BatchOperationResult;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceBatchService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备批量操作控制台接口单测（T-18 实施计划 P5）。
 * <p>
 * 聚焦「当前用户透传 + 启用禁用布尔映射 + 关联 action 映射（缺省 ADD）」。
 */
@ExtendWith(MockitoExtension.class)
class DeviceBatchControllerTest {

    private static final Long USER_ID = 10L;

    @Mock
    private DeviceBatchService deviceBatchService;

    @InjectMocks
    private DeviceBatchController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void commands_should_delegate_with_current_user() {
        authenticate();
        BatchCommandRequest request = new BatchCommandRequest();
        BatchOperationResult result = BatchOperationResult.of(List.of());
        when(deviceBatchService.sendCommands(USER_ID, request)).thenReturn(result);

        assertThat(controller.commands(request).getData()).isSameAs(result);
    }

    @Test
    void enable_should_call_set_enabled_true() {
        authenticate();
        BatchTargetRequest request = new BatchTargetRequest();

        controller.enable(request);

        verify(deviceBatchService).setEnabled(USER_ID, request, true);
    }

    @Test
    void disable_should_call_set_enabled_false() {
        authenticate();
        BatchTargetRequest request = new BatchTargetRequest();

        controller.disable(request);

        verify(deviceBatchService).setEnabled(USER_ID, request, false);
    }

    @Test
    void assignGroup_should_default_to_add() {
        authenticate();
        BatchAssignRequest request = new BatchAssignRequest();

        controller.assignGroup(request);

        verify(deviceBatchService).assignGroup(USER_ID, request, true);
    }

    @Test
    void assignGroup_should_map_remove_action() {
        authenticate();
        BatchAssignRequest request = new BatchAssignRequest();
        request.setAction("REMOVE");

        controller.assignGroup(request);

        verify(deviceBatchService).assignGroup(USER_ID, request, false);
    }

    @Test
    void assignTag_should_map_remove_action_case_insensitively() {
        authenticate();
        BatchAssignRequest request = new BatchAssignRequest();
        request.setAction("remove");

        controller.assignTag(request);

        verify(deviceBatchService).assignTag(USER_ID, request, false);
    }

    @Test
    void assignTag_should_default_to_add() {
        authenticate();
        BatchAssignRequest request = new BatchAssignRequest();

        controller.assignTag(request);

        verify(deviceBatchService).assignTag(USER_ID, request, true);
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}