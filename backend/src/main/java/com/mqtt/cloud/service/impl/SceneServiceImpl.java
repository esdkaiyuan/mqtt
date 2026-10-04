package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.request.SceneQuery;
import com.mqtt.cloud.dto.request.SceneRequest;
import com.mqtt.cloud.dto.response.SceneConditionView;
import com.mqtt.cloud.dto.response.SceneStepView;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.mapper.SceneStepMapper;
import com.mqtt.cloud.service.SceneEvaluationService;
import com.mqtt.cloud.service.SceneService;
import com.mqtt.cloud.util.SceneValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 场景联动服务实现（T-23 设计文档 §7 / §10.1）。
 * <p>
 * 校验委托 {@link SceneValidator}（触发源 / 条件组 / 步骤 / 目标 / 动作配置）；
 * 名称唯一性与场景数上限依赖 {@link SceneDefinitionMapper} 查询，在本类处理。
 * 写入后主动失效本副本场景缓存（{@link SceneEvaluationService#evictCache(Long)}）。
 * <p>
 * 更新采用**步骤整体替换**（{@link SceneStepMapper#deleteBySceneId(Long)} 后重新插入，同一事务）。
 * 返回前端时把库中 JSON 文本反序列化为对象视图（{@code conditions} / {@code steps}）。
 */
@Slf4j
@Service
public class SceneServiceImpl extends ServiceImpl<SceneDefinitionMapper, SceneDefinition> implements SceneService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final SceneStepMapper sceneStepMapper;
    private final SceneValidator sceneValidator;
    private final SceneEvaluationService sceneEvaluationService;
    private final SceneExecutionLauncher sceneExecutionLauncher;
    private final SceneProperties properties;
    private final ObjectMapper objectMapper;

    public SceneServiceImpl(SceneStepMapper sceneStepMapper,
                            SceneValidator sceneValidator,
                            SceneEvaluationService sceneEvaluationService,
                            SceneExecutionLauncher sceneExecutionLauncher,
                            SceneProperties properties,
                            ObjectMapper objectMapper) {
        this.sceneStepMapper = sceneStepMapper;
        this.sceneValidator = sceneValidator;
        this.sceneEvaluationService = sceneEvaluationService;
        this.sceneExecutionLauncher = sceneExecutionLauncher;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public IPage<SceneDefinition> listScenes(Long userId, SceneQuery query) {
        SceneQuery effective = query == null ? new SceneQuery() : query;
        int pageNum = (effective.getPageNum() == null || effective.getPageNum() < 1)
                ? 1 : effective.getPageNum();
        int pageSize = (effective.getPageSize() == null || effective.getPageSize() < 1)
                ? DEFAULT_PAGE_SIZE : Math.min(effective.getPageSize(), MAX_PAGE_SIZE);

        IPage<SceneDefinition> result = baseMapper.pageByUser(new Page<>(pageNum, pageSize), userId,
                normalizeUpper(effective.getTriggerType()), effective.getEnabled(), trimToNull(effective.getKeyword()));
        result.getRecords().forEach(this::populateConditionView);
        return result;
    }

    @Override
    public SceneDefinition getScene(Long userId, Long id) {
        SceneDefinition scene = getOwned(userId, id);
        populateViews(scene);
        return scene;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SceneDefinition createScene(Long userId, SceneRequest request) {
        if (baseMapper.countByUser(userId) >= properties.getMaxScenesPerUser()) {
            throw new BusinessException(ResultCode.SCENE_LIMIT_EXCEEDED,
                    "场景数量已达上限 " + properties.getMaxScenesPerUser());
        }
        SceneValidator.ValidatedScene validated = sceneValidator.validate(userId, request);
        checkNameUnique(userId, validated.definition().getName(), null);

        SceneDefinition scene = validated.definition();
        scene.setUserId(userId);
        save(scene);
        insertSteps(scene.getId(), validated.steps());
        sceneEvaluationService.evictCache(userId);
        populateViews(scene);
        return scene;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SceneDefinition updateScene(Long userId, Long id, SceneRequest request) {
        getOwned(userId, id);
        SceneValidator.ValidatedScene validated = sceneValidator.validate(userId, request);
        checkNameUnique(userId, validated.definition().getName(), id);

        SceneDefinition updated = validated.definition();
        updated.setId(id);
        updated.setUserId(userId);
        updateById(updated);
        sceneStepMapper.deleteBySceneId(id);
        insertSteps(id, validated.steps());
        sceneEvaluationService.evictCache(userId);
        populateViews(updated);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(Long userId, Long id) {
        getOwned(userId, id);
        removeById(id);
        sceneStepMapper.deleteBySceneId(id);
        sceneEvaluationService.evictCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long userId, Long id, boolean enabled) {
        SceneDefinition scene = getOwned(userId, id);
        scene.setEnabled(enabled ? 1 : 0);
        updateById(scene);
        sceneEvaluationService.evictCache(userId);
    }

    @Override
    public void evictCache(Long userId) {
        sceneEvaluationService.evictCache(userId);
    }

    @Override
    public Long runManually(Long userId, Long id) {
        SceneDefinition scene = getOwned(userId, id);
        boolean hasEnabledStep = sceneStepMapper.selectBySceneId(id).stream()
                .anyMatch(step -> step.getEnabled() == null || step.getEnabled() == 1);
        if (!hasEnabledStep) {
            throw new BusinessException(ResultCode.SCENE_INVALID, "场景无启用步骤，无法执行");
        }
        return sceneExecutionLauncher.launch(scene, SceneExecutionLauncher.TriggerContext.empty(),
                SceneConstants.SOURCE_MANUAL);
    }

    // ---------- 内部 ----------

    private SceneDefinition getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.SCENE_NOT_FOUND);
        }
        SceneDefinition scene = getById(id);
        if (scene == null) {
            throw new BusinessException(ResultCode.SCENE_NOT_FOUND);
        }
        if (!userId.equals(scene.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return scene;
    }

    private void checkNameUnique(Long userId, String name, Long excludeId) {
        if (baseMapper.countByUserAndName(userId, name, excludeId) > 0) {
            throw new BusinessException(ResultCode.SCENE_INVALID, "场景名称已存在");
        }
    }

    private void insertSteps(Long sceneId, List<SceneStep> steps) {
        for (SceneStep step : steps) {
            step.setSceneId(sceneId);
            sceneStepMapper.insert(step);
        }
    }

    /** 列表：仅解析条件组（不查步骤，避免 N+1）。 */
    private void populateConditionView(SceneDefinition scene) {
        scene.setConditionsView(readConditions(scene.getConditionConfig()));
    }

    /** 详情 / 创建 / 更新：解析条件组与步骤流。 */
    private void populateViews(SceneDefinition scene) {
        populateConditionView(scene);
        List<SceneStep> steps = sceneStepMapper.selectBySceneId(scene.getId());
        scene.setStepsView(steps.stream().map(this::toStepView).toList());
    }

    private List<SceneConditionView> readConditions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<SceneConditionView> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("场景条件组解析失败: sceneId 解析跳过, error={}", e.getMessage());
            return List.of();
        }
    }

    private SceneStepView toStepView(SceneStep step) {
        SceneStepView view = new SceneStepView();
        view.setId(step.getId());
        view.setSeq(step.getSeq());
        view.setDelaySeconds(step.getDelaySeconds());
        view.setActionType(step.getActionType());
        view.setTargetType(step.getTargetType());
        view.setTargetConfig(readTree(step.getTargetConfig()));
        view.setActionConfig(readTree(step.getActionConfig()));
        view.setEnabled(step.getEnabled());
        return view;
    }

    private Object readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("场景步骤 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }
}