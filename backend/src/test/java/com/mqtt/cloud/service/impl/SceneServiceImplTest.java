package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.request.SceneQuery;
import com.mqtt.cloud.dto.request.SceneRequest;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.mapper.SceneStepMapper;
import com.mqtt.cloud.service.SceneEvaluationService;
import com.mqtt.cloud.util.SceneValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 场景定义服务单测（T-23 设计文档 §14.1 / §7）。
 * <p>
 * 校验委托 {@link SceneValidator}、执行启动委托 {@link SceneExecutionLauncher}，二者在本测试中 mock，
 * 仅保留本类自身口径：分页归一化、上限与重名、越权 / 不存在、步骤整体替换、启停、缓存失效。
 * <p>
 * {@code SceneServiceImpl} 继承 MyBatis-Plus {@code ServiceImpl}，{@code baseMapper} 为父类字段，
 * 故用 {@link ReflectionTestUtils} 注入；方法命名遵循 {@code x_should_y_when_z} 约定。
 */
class SceneServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final Long SCENE_ID = 5L;
    private static final Long EXECUTION_ID = 900L;

    private SceneDefinitionMapper definitionMapper;
    private SceneStepMapper stepMapper;
    private SceneValidator validator;
    private SceneEvaluationService evaluationService;
    private SceneExecutionLauncher launcher;
    private SceneProperties properties;
    private SceneServiceImpl service;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(SceneDefinitionMapper.class);
        stepMapper = mock(SceneStepMapper.class);
        validator = mock(SceneValidator.class);
        evaluationService = mock(SceneEvaluationService.class);
        launcher = mock(SceneExecutionLauncher.class);
        properties = new SceneProperties();
        service = new SceneServiceImpl(stepMapper, validator, evaluationService, launcher,
                properties, new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", definitionMapper);
        // 插入时回填主键，模拟自增 ID
        when(definitionMapper.insert(any(SceneDefinition.class))).thenAnswer(invocation -> {
            ((SceneDefinition) invocation.getArgument(0)).setId(SCENE_ID);
            return 1;
        });
    }

    // ---------- 列表 ----------

    @Test
    void listScenes_should_clamp_paging_and_normalize_filters() {
        SceneQuery query = new SceneQuery();
        query.setPageNum(0);
        query.setPageSize(500);
        query.setTriggerType("  property ");
        query.setKeyword("  温度  ");
        query.setEnabled(true);
        when(definitionMapper.pageByUser(any(), eq(USER_ID), any(), any(), any())).thenReturn(pageOf(scene(SCENE_ID)));

        service.listScenes(USER_ID, query);

        ArgumentCaptor<Page<SceneDefinition>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(definitionMapper).pageByUser(pageCaptor.capture(), eq(USER_ID), eq("PROPERTY"), eq(true), eq("温度"));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);
    }

    @Test
    void listScenes_should_use_default_page_size_when_absent() {
        when(definitionMapper.pageByUser(any(), eq(USER_ID), any(), any(), any())).thenReturn(pageOf());

        service.listScenes(USER_ID, null);

        ArgumentCaptor<Page<SceneDefinition>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(definitionMapper).pageByUser(pageCaptor.capture(), eq(USER_ID), isNull(), isNull(), isNull());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    void listScenes_should_parse_conditions_without_loading_steps() {
        SceneDefinition scene = scene(SCENE_ID);
        scene.setConditionConfig(conditionJson());
        when(definitionMapper.pageByUser(any(), eq(USER_ID), any(), any(), any())).thenReturn(pageOf(scene));

        IPage<SceneDefinition> result = service.listScenes(USER_ID, new SceneQuery());

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getConditionsView()).hasSize(1);
        assertThat(result.getRecords().get(0).getStepsView()).isNull();
        verifyNoInteractions(stepMapper);
    }

    @Test
    void listScenes_should_tolerate_blank_condition_json() {
        SceneDefinition scene = scene(SCENE_ID);
        scene.setConditionConfig("not-a-json");
        when(definitionMapper.pageByUser(any(), eq(USER_ID), any(), any(), any())).thenReturn(pageOf(scene));

        IPage<SceneDefinition> result = service.listScenes(USER_ID, new SceneQuery());

        assertThat(result.getRecords().get(0).getConditionsView()).isEmpty();
    }

    // ---------- 创建校验 ----------

    @Test
    void createScene_should_reject_when_scene_count_reaches_limit() {
        properties.setMaxScenesPerUser(2);
        when(definitionMapper.countByUser(USER_ID)).thenReturn(2);

        assertCode(() -> service.createScene(USER_ID, new SceneRequest()), ResultCode.SCENE_LIMIT_EXCEEDED);

        verifyNoInteractions(validator);
        verify(definitionMapper, never()).insert(any(SceneDefinition.class));
    }

    @Test
    void createScene_should_reject_when_name_duplicated() {
        when(validator.validate(eq(USER_ID), any())).thenReturn(validated("回家场景", List.of(step(1))));
        when(definitionMapper.countByUserAndName(eq(USER_ID), eq("回家场景"), isNull())).thenReturn(1);

        assertCode(() -> service.createScene(USER_ID, new SceneRequest()), ResultCode.SCENE_INVALID);

        verify(definitionMapper, never()).insert(any(SceneDefinition.class));
        verify(evaluationService, never()).evictCache(any());
    }

    // ---------- 创建成功 ----------

    @Test
    void createScene_should_persist_scene_and_steps() {
        when(validator.validate(eq(USER_ID), any())).thenReturn(validated("回家场景", List.of(step(1), step(2))));
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of(step(1), step(2)));

        SceneDefinition created = service.createScene(USER_ID, new SceneRequest());

        assertThat(created.getId()).isEqualTo(SCENE_ID);
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        verify(definitionMapper).insert(created);
        ArgumentCaptor<SceneStep> stepCaptor = ArgumentCaptor.forClass(SceneStep.class);
        verify(stepMapper, times(2)).insert(stepCaptor.capture());
        assertThat(stepCaptor.getAllValues()).allSatisfy(step -> assertThat(step.getSceneId()).isEqualTo(SCENE_ID));
        verify(evaluationService).evictCache(USER_ID);
        assertThat(created.getStepsView()).hasSize(2);
    }

    // ---------- 更新 / 删除 / 启停 ----------

    @Test
    void updateScene_should_replace_steps_wholesale() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene(SCENE_ID, USER_ID));
        when(validator.validate(eq(USER_ID), any())).thenReturn(validated("更新后", List.of(step(1))));
        when(definitionMapper.countByUserAndName(eq(USER_ID), eq("更新后"), eq(SCENE_ID))).thenReturn(0);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of(step(1)));

        service.updateScene(USER_ID, SCENE_ID, new SceneRequest());

        ArgumentCaptor<SceneDefinition> updateCaptor = ArgumentCaptor.forClass(SceneDefinition.class);
        verify(definitionMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(SCENE_ID);
        assertThat(updateCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        verify(stepMapper).deleteBySceneId(SCENE_ID);
        verify(stepMapper).insert(any(SceneStep.class));
        verify(evaluationService).evictCache(USER_ID);
    }

    @Test
    void updateScene_should_reject_when_scene_missing() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(null);

        assertCode(() -> service.updateScene(USER_ID, SCENE_ID, new SceneRequest()), ResultCode.SCENE_NOT_FOUND);

        verify(definitionMapper, never()).updateById(any(SceneDefinition.class));
        verifyNoInteractions(validator);
    }

    @Test
    void updateScene_should_reject_when_not_owner() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene(SCENE_ID, OTHER_USER_ID));

        assertCode(() -> service.updateScene(USER_ID, SCENE_ID, new SceneRequest()), ResultCode.FORBIDDEN);

        verify(definitionMapper, never()).updateById(any(SceneDefinition.class));
        verifyNoInteractions(validator);
    }

    @Test
    void deleteScene_should_remove_scene_and_steps() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene(SCENE_ID, USER_ID));

        service.deleteScene(USER_ID, SCENE_ID);

        verify(definitionMapper).deleteById(SCENE_ID);
        verify(stepMapper).deleteBySceneId(SCENE_ID);
        verify(evaluationService).evictCache(USER_ID);
    }

    @Test
    void deleteScene_should_reject_when_not_owner() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene(SCENE_ID, OTHER_USER_ID));

        assertCode(() -> service.deleteScene(USER_ID, SCENE_ID), ResultCode.FORBIDDEN);

        verify(definitionMapper, never()).deleteById(any(Long.class));
        verify(stepMapper, never()).deleteBySceneId(any());
    }

    @Test
    void setEnabled_should_flip_flag_and_evict_cache() {
        SceneDefinition stored = scene(SCENE_ID, USER_ID);
        stored.setEnabled(0);
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(stored);

        service.setEnabled(USER_ID, SCENE_ID, true);

        ArgumentCaptor<SceneDefinition> captor = ArgumentCaptor.forClass(SceneDefinition.class);
        verify(definitionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        verify(evaluationService).evictCache(USER_ID);
    }

    @Test
    void setEnabled_should_disable_when_flag_false() {
        SceneDefinition stored = scene(SCENE_ID, USER_ID);
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(stored);

        service.setEnabled(USER_ID, SCENE_ID, false);

        ArgumentCaptor<SceneDefinition> captor = ArgumentCaptor.forClass(SceneDefinition.class);
        verify(definitionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isZero();
        verify(evaluationService).evictCache(USER_ID);
    }

    // ---------- 详情与越权 ----------

    @Test
    void getScene_should_reject_when_id_null() {
        assertCode(() -> service.getScene(USER_ID, null), ResultCode.SCENE_NOT_FOUND);

        verifyNoInteractions(stepMapper);
    }

    @Test
    void getScene_should_reject_when_missing() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(null);

        assertCode(() -> service.getScene(USER_ID, SCENE_ID), ResultCode.SCENE_NOT_FOUND);
    }

    @Test
    void getScene_should_reject_when_not_owner() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene(SCENE_ID, OTHER_USER_ID));

        assertCode(() -> service.getScene(USER_ID, SCENE_ID), ResultCode.FORBIDDEN);
    }

    @Test
    void getScene_should_populate_conditions_and_steps() {
        SceneDefinition stored = scene(SCENE_ID, USER_ID);
        stored.setConditionConfig(conditionJson());
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(stored);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of(step(1)));

        SceneDefinition result = service.getScene(USER_ID, SCENE_ID);

        assertThat(result.getConditionsView()).hasSize(1);
        assertThat(result.getStepsView()).hasSize(1);
    }

    // ---------- 手动执行 ----------

    @Test
    void runManually_should_reject_when_no_enabled_step() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene(SCENE_ID, USER_ID));
        SceneStep disabled = step(1);
        disabled.setEnabled(0);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of(disabled));

        assertCode(() -> service.runManually(USER_ID, SCENE_ID), ResultCode.SCENE_INVALID);

        verifyNoInteractions(launcher);
    }

    @Test
    void runManually_should_treat_null_enabled_as_enabled() {
        SceneDefinition stored = scene(SCENE_ID, USER_ID);
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(stored);
        SceneStep legacy = step(1);
        legacy.setEnabled(null);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of(legacy));
        when(launcher.launch(eq(stored), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_MANUAL))).thenReturn(EXECUTION_ID);

        Long executionId = service.runManually(USER_ID, SCENE_ID);

        assertThat(executionId).isEqualTo(EXECUTION_ID);
        verify(launcher).launch(eq(stored), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_MANUAL));
    }

    @Test
    void runManually_should_reject_when_not_owner() {
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene(SCENE_ID, OTHER_USER_ID));

        assertCode(() -> service.runManually(USER_ID, SCENE_ID), ResultCode.FORBIDDEN);

        verifyNoInteractions(launcher);
    }

    @Test
    void evictCache_should_delegate_to_evaluation_service() {
        service.evictCache(USER_ID);

        verify(evaluationService).evictCache(USER_ID);
    }

    // ---------- 辅助 ----------

    private Page<SceneDefinition> pageOf(SceneDefinition... records) {
        Page<SceneDefinition> page = new Page<>(1, 10);
        page.setRecords(List.of(records));
        return page;
    }

    private SceneDefinition scene(Long id) {
        return scene(id, USER_ID);
    }

    private static SceneDefinition scene(Long id, Long userId) {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(id);
        scene.setUserId(userId);
        scene.setName("场景-" + id);
        scene.setTriggerType(SceneConstants.TRIGGER_PROPERTY);
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        scene.setCooldownSeconds(0);
        scene.setEnabled(1);
        return scene;
    }

    private static SceneStep step(int seq) {
        SceneStep step = new SceneStep();
        step.setId((long) seq);
        step.setSeq(seq);
        step.setDelaySeconds(0);
        step.setActionType(SceneConstants.ACTION_FORWARD_MQTT);
        step.setTargetType(SceneConstants.TARGET_TRIGGER);
        step.setEnabled(1);
        return step;
    }

    private static SceneValidator.ValidatedScene validated(String name, List<SceneStep> steps) {
        SceneDefinition definition = new SceneDefinition();
        definition.setName(name);
        definition.setTriggerType(SceneConstants.TRIGGER_PROPERTY);
        definition.setConditionLogic(SceneConstants.LOGIC_AND);
        definition.setCooldownSeconds(0);
        definition.setEnabled(1);
        return new SceneValidator.ValidatedScene(definition, steps);
    }

    private static String conditionJson() {
        return "[{\"deviceId\":100,\"identifier\":\"temperature\",\"operator\":\"GT\",\"threshold\":\"40\"}]";
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }
}
