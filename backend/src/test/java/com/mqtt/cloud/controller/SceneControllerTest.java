package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.SceneExecutionQuery;
import com.mqtt.cloud.dto.request.SceneQuery;
import com.mqtt.cloud.dto.request.SceneRequest;
import com.mqtt.cloud.dto.request.SceneTestRequest;
import com.mqtt.cloud.dto.response.SceneExecutionDetail;
import com.mqtt.cloud.dto.response.SceneTestResult;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.SceneExecutionService;
import com.mqtt.cloud.service.SceneService;
import com.mqtt.cloud.service.SceneTestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 场景联动控制台接口单测（T-23 设计文档 §10.1 / §14.1）。
 * <p>
 * 聚焦「当前用户透传 + 参数下传 + 结果封装 + 重试状态冲突透传」；鉴权注解与 HTTP 状态码由
 * {@code SecurityConfig} 与全局异常处理器保证。另以 Stand-alone {@link MockMvc} 覆盖静态段
 * （{@code /scenes/executions}）与动态段（{@code /scenes/{id}}）的路由优先级，避免动态段吞掉静态路径。
 * <p>
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
@ExtendWith(MockitoExtension.class)
class SceneControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long SCENE_ID = 5L;
    private static final Long EXECUTION_ID = 900L;

    @Mock
    private SceneService sceneService;

    @Mock
    private SceneExecutionService sceneExecutionService;

    @Mock
    private SceneTestService sceneTestService;

    @InjectMocks
    private SceneController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- 场景 CRUD ----------

    @Test
    void listScenes_should_delegate_with_current_user() {
        authenticate();
        SceneQuery query = new SceneQuery();
        when(sceneService.listScenes(eq(USER_ID), any())).thenReturn(new Page<>(1, 10));

        IPage<SceneDefinition> page = controller.listScenes(query).getData();

        assertThat(page).isNotNull();
        verify(sceneService).listScenes(USER_ID, query);
    }

    @Test
    void createScene_should_delegate_with_current_user() {
        authenticate();
        SceneRequest request = new SceneRequest();
        SceneDefinition created = new SceneDefinition();
        when(sceneService.createScene(USER_ID, request)).thenReturn(created);

        assertThat(controller.createScene(request).getData()).isSameAs(created);
    }

    @Test
    void getScene_should_delegate() {
        authenticate();
        SceneDefinition scene = new SceneDefinition();
        when(sceneService.getScene(USER_ID, SCENE_ID)).thenReturn(scene);

        assertThat(controller.getScene(SCENE_ID).getData()).isSameAs(scene);
    }

    @Test
    void updateScene_should_delegate() {
        authenticate();
        SceneRequest request = new SceneRequest();
        SceneDefinition updated = new SceneDefinition();
        when(sceneService.updateScene(USER_ID, SCENE_ID, request)).thenReturn(updated);

        assertThat(controller.updateScene(SCENE_ID, request).getData()).isSameAs(updated);
    }

    @Test
    void deleteScene_should_delegate() {
        authenticate();

        controller.deleteScene(SCENE_ID);

        verify(sceneService).deleteScene(USER_ID, SCENE_ID);
    }

    @Test
    void setSceneEnabled_should_reject_missing_flag() {
        authenticate();

        assertThatThrownBy(() -> controller.setSceneEnabled(SCENE_ID, Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SCENE_INVALID.getCode());
        verify(sceneService, never()).setEnabled(any(), any(), any(Boolean.class));
    }

    @Test
    void setSceneEnabled_should_delegate() {
        authenticate();

        controller.setSceneEnabled(SCENE_ID, Map.of("enabled", false));

        verify(sceneService).setEnabled(USER_ID, SCENE_ID, false);
    }

    @Test
    void testScene_should_delegate() {
        authenticate();
        SceneTestRequest request = new SceneTestRequest();
        SceneTestResult result = new SceneTestResult();
        when(sceneTestService.test(USER_ID, SCENE_ID, request)).thenReturn(result);

        assertThat(controller.testScene(SCENE_ID, request).getData()).isSameAs(result);
    }

    @Test
    void runScene_should_delegate_and_return_execution_id() {
        authenticate();
        when(sceneService.runManually(USER_ID, SCENE_ID)).thenReturn(EXECUTION_ID);

        assertThat(controller.runScene(SCENE_ID).getData()).isEqualTo(EXECUTION_ID);
    }

    // ---------- 执行记录 ----------

    @Test
    void listExecutions_should_delegate_with_current_user() {
        authenticate();
        SceneExecutionQuery query = new SceneExecutionQuery();
        when(sceneExecutionService.page(eq(USER_ID), any())).thenReturn(new Page<>(1, 10));

        IPage<SceneExecution> page = controller.listExecutions(query).getData();

        assertThat(page).isNotNull();
        verify(sceneExecutionService).page(USER_ID, query);
    }

    @Test
    void getExecution_should_delegate() {
        authenticate();
        SceneExecution execution = new SceneExecution();
        SceneExecutionDetail detail = new SceneExecutionDetail(execution, List.of());
        when(sceneExecutionService.getDetail(USER_ID, EXECUTION_ID)).thenReturn(detail);

        assertThat(controller.getExecution(EXECUTION_ID).getData()).isSameAs(detail);
    }

    @Test
    void retryExecution_should_delegate() {
        authenticate();

        controller.retryExecution(EXECUTION_ID);

        verify(sceneExecutionService).manualRetry(USER_ID, EXECUTION_ID);
    }

    @Test
    void retryExecution_should_propagate_conflict() {
        authenticate();
        doThrow(new BusinessException(ResultCode.SCENE_INVALID, HttpStatus.CONFLICT, "状态冲突"))
                .when(sceneExecutionService).manualRetry(USER_ID, EXECUTION_ID);

        assertThatThrownBy(() -> controller.retryExecution(EXECUTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---------- 路由优先级 ----------

    @Test
    @DisplayName("GET /scenes/executions 命中静态段，不被 /scenes/{id} 动态段吞掉")
    void route_executions_static_segment_should_win_over_dynamic_id() throws Exception {
        authenticate();
        when(sceneExecutionService.page(eq(USER_ID), any())).thenReturn(new Page<>(1, 10));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/scenes/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sceneExecutionService).page(eq(USER_ID), any());
        verify(sceneService, never()).getScene(any(), any());
    }

    @Test
    @DisplayName("GET /scenes/{id} 命中动态段，解析为场景详情")
    void route_dynamic_id_should_resolve_scene_detail() throws Exception {
        authenticate();
        SceneDefinition scene = new SceneDefinition();
        scene.setId(SCENE_ID);
        when(sceneService.getScene(USER_ID, SCENE_ID)).thenReturn(scene);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/scenes/" + SCENE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sceneService).getScene(USER_ID, SCENE_ID);
        verify(sceneExecutionService, never()).page(any(), any());
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
