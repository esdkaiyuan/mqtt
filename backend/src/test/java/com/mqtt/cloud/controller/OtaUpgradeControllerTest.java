package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.request.OtaTaskCreateRequest;
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.dto.response.OtaTaskVO;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.OtaUpgradeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OTA 升级任务控制台接口单测（T-22 实施计划 P6）。
 * <p>
 * 聚焦「当前用户透传 + 分页与状态参数下传 + 结果封装」；请求体结构校验由 Bean Validation、
 * 鉴权注解与 HTTP 状态码由 {@code SecurityConfig} 与全局异常处理器保证。
 */
@ExtendWith(MockitoExtension.class)
class OtaUpgradeControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long TASK_ID = 5L;
    private static final Long FIRMWARE_ID = 1L;

    @Mock
    private OtaUpgradeService otaUpgradeService;

    @InjectMocks
    private OtaUpgradeController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_should_delegate_with_current_user() {
        authenticate();
        OtaTaskCreateRequest request = request();
        OtaTaskDetailVO detail = new OtaTaskDetailVO();
        when(otaUpgradeService.createTask(USER_ID, request)).thenReturn(detail);

        Result<OtaTaskDetailVO> result = controller.create(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(detail);
    }

    @Test
    void list_should_pass_paging() {
        authenticate();
        IPage<OtaTaskVO> page = new Page<>(2, 20);
        when(otaUpgradeService.listTasks(USER_ID, 2L, 20L)).thenReturn(page);

        assertThat(controller.list(2L, 20L).getData()).isSameAs(page);
    }

    @Test
    void detail_should_delegate() {
        authenticate();
        OtaTaskDetailVO detail = new OtaTaskDetailVO();
        when(otaUpgradeService.detail(USER_ID, TASK_ID)).thenReturn(detail);

        assertThat(controller.detail(TASK_ID).getData()).isSameAs(detail);
    }

    @Test
    void records_should_pass_status_and_paging() {
        authenticate();
        IPage<OtaRecordVO> page = new Page<>(1, 50);
        when(otaUpgradeService.listRecords(USER_ID, TASK_ID, "FAILED", 1L, 50L)).thenReturn(page);

        assertThat(controller.records(TASK_ID, "FAILED", 1L, 50L).getData()).isSameAs(page);
        verify(otaUpgradeService).listRecords(USER_ID, TASK_ID, "FAILED", 1L, 50L);
    }

    @Test
    void retry_should_return_reset_count() {
        authenticate();
        when(otaUpgradeService.retry(USER_ID, TASK_ID)).thenReturn(3);

        assertThat(controller.retry(TASK_ID).getData()).isEqualTo(3);
    }

    @Test
    void remove_should_delegate() {
        authenticate();

        controller.remove(TASK_ID);

        verify(otaUpgradeService).remove(USER_ID, TASK_ID);
    }

    private OtaTaskCreateRequest request() {
        OtaTaskCreateRequest request = new OtaTaskCreateRequest();
        request.setName("整批升级");
        request.setFirmwareId(FIRMWARE_ID);
        request.setTarget(new BatchTargetRequest());
        return request;
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "OPERATOR");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}