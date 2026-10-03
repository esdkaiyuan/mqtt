package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mqtt.cloud.common.PropertyHistoryConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DashboardProperties;
import com.mqtt.cloud.config.PropertyHistoryProperties;
import com.mqtt.cloud.dto.request.DashboardSaveDTO;
import com.mqtt.cloud.dto.response.DashboardConfig;
import com.mqtt.cloud.dto.response.DashboardDetailVO;
import com.mqtt.cloud.dto.response.DashboardSummaryVO;
import com.mqtt.cloud.entity.Dashboard;
import com.mqtt.cloud.mapper.DashboardMapper;
import com.mqtt.cloud.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 可保存看板实现（T-21 设计文档 §6.2 / §7.1）。
 * <p>
 * 归属口径与 {@code RuleServiceImpl} 一致：{@code user_id} 服务端注入，查询 / 更新 / 删除统一走
 * {@code getOwned}，非本人一律 {@code 6228}。{@code config} 在服务层解析为 {@link DashboardConfig}
 * 后逐字段白名单校验（数量 / 桶 / 图型 / 聚合 / 跨度），非法 → {@code 6229}，再以规范化 JSON 落库。
 * <p>
 * 面板为空**允许**（新建后进入编辑模式逐个添加面板），但一旦有面板，则每个面板的
 * 设备集合 / 属性 / 桶 / 图型 / 聚合都必须合法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_PANEL_TITLE_LENGTH = 64;
    private static final String DEFAULT_PANEL_TITLE = "未命名面板";
    private static final String DEFAULT_CHART_TYPE = "line";
    private static final String DEFAULT_AGGREGATION = "avg";
    private static final int DEFAULT_RANGE_DAYS = 1;

    private final DashboardMapper dashboardMapper;
    private final DashboardProperties properties;
    private final PropertyHistoryProperties propertyHistoryProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<DashboardSummaryVO> list(Long userId) {
        List<Dashboard> rows = dashboardMapper.selectList(new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getUserId, userId)
                .orderByDesc(Dashboard::getUpdatedAt)
                .orderByDesc(Dashboard::getId));
        List<DashboardSummaryVO> result = new ArrayList<>(rows.size());
        for (Dashboard row : rows) {
            DashboardSummaryVO vo = new DashboardSummaryVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setUpdatedAt(row.getUpdatedAt());
            vo.setPanelCount(parseQuietly(row.getConfig()).getPanels().size());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DashboardDetailVO create(Long userId, DashboardSaveDTO dto) {
        int maxCount = maxCount();
        Long existing = dashboardMapper.selectCount(new LambdaQueryWrapper<Dashboard>()
                .eq(Dashboard::getUserId, userId));
        if (existing != null && existing >= maxCount) {
            throw new BusinessException(ResultCode.DASHBOARD_LIMIT_EXCEEDED,
                    "看板数量已达上限 " + maxCount);
        }
        String name = normalizeName(dto);
        DashboardConfig config = validateConfig(dto);

        Dashboard dashboard = new Dashboard();
        // user_id 服务端注入：忽略客户端传值，杜绝越权落库
        dashboard.setUserId(userId);
        dashboard.setName(name);
        dashboard.setConfig(serialize(config));
        dashboardMapper.insert(dashboard);
        return toDetail(dashboard, config);
    }

    @Override
    public DashboardDetailVO detail(Long userId, Long id) {
        Dashboard dashboard = getOwned(userId, id);
        return toDetail(dashboard, parseStrict(dashboard.getConfig()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DashboardDetailVO update(Long userId, Long id, DashboardSaveDTO dto) {
        Dashboard dashboard = getOwned(userId, id);
        String name = normalizeName(dto);
        DashboardConfig config = validateConfig(dto);

        dashboard.setName(name);
        dashboard.setConfig(serialize(config));
        // updated_at 由表 ON UPDATE 维护，此处不显式赋值
        dashboardMapper.updateById(dashboard);
        return toDetail(dashboard, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        Dashboard dashboard = getOwned(userId, id);
        dashboardMapper.deleteById(dashboard.getId());
    }

    /** 归属校验：不存在或非本人一律 {@code 6228}（不区分，避免探测）。 */
    private Dashboard getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.DASHBOARD_NOT_FOUND);
        }
        Dashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null || !dashboard.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.DASHBOARD_NOT_FOUND);
        }
        return dashboard;
    }

    /** 名称：去空白，非空且 ≤ 64 字符，否则 {@code 6229}。 */
    private String normalizeName(DashboardSaveDTO dto) {
        String name = dto == null ? null : dto.getName();
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw invalid("看板名称不能为空");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw invalid("看板名称不能超过 " + MAX_NAME_LENGTH + " 字符");
        }
        return trimmed;
    }

    /** 解析并逐字段校验配置，返回**规范化**后的模型（面板 / 桶 / 图型 / 聚合均已归一化）。 */
    private DashboardConfig validateConfig(DashboardSaveDTO dto) {
        String raw = dto == null ? null : dto.getConfig();
        if (raw == null || raw.isBlank()) {
            throw invalid("看板配置不能为空");
        }
        DashboardConfig config;
        try {
            config = objectMapper.readValue(raw, DashboardConfig.class);
        } catch (Exception e) {
            throw invalid("看板配置不是合法 JSON");
        }
        if (config == null) {
            throw invalid("看板配置不能为空");
        }
        if (config.getPanels() == null) {
            config.setPanels(new ArrayList<>());
        }
        int maxPanels = maxPanels();
        if (config.getPanels().size() > maxPanels) {
            throw invalid("面板数量（" + config.getPanels().size() + "）超过上限 " + maxPanels);
        }
        int maxRangeDays = maxRangeDays();
        for (DashboardConfig.Panel panel : config.getPanels()) {
            validatePanel(panel, maxRangeDays);
        }
        return config;
    }

    /** 单面板校验 + 归一化：设备集合 / 属性必填，桶 / 图型 / 聚合走白名单，跨度不超上限。 */
    private void validatePanel(DashboardConfig.Panel panel, int maxRangeDays) {
        if (panel == null) {
            throw invalid("面板不能为空");
        }
        Set<Long> deviceIds = new LinkedHashSet<>();
        if (panel.getDeviceIds() != null) {
            for (Long deviceId : panel.getDeviceIds()) {
                if (deviceId != null) {
                    deviceIds.add(deviceId);
                }
            }
        }
        if (deviceIds.isEmpty()) {
            throw invalid("面板至少需要一台设备");
        }
        panel.setDeviceIds(new ArrayList<>(deviceIds));

        String identifier = panel.getIdentifier() == null ? null : panel.getIdentifier().trim();
        if (identifier == null || identifier.isEmpty()) {
            throw invalid("面板属性标识符不能为空");
        }
        panel.setIdentifier(identifier);

        String title = panel.getTitle() == null ? null : panel.getTitle().trim();
        if (title == null || title.isEmpty()) {
            title = DEFAULT_PANEL_TITLE;
        }
        if (title.length() > MAX_PANEL_TITLE_LENGTH) {
            throw invalid("面板标题不能超过 " + MAX_PANEL_TITLE_LENGTH + " 字符");
        }
        panel.setTitle(title);

        String bucket = panel.getBucket() == null ? null : panel.getBucket().trim().toLowerCase(Locale.ROOT);
        if (bucket == null || bucket.isEmpty()) {
            bucket = PropertyHistoryConstants.DEFAULT_BUCKET;
        }
        if (!PropertyHistoryConstants.SUPPORTED_BUCKETS.contains(bucket)) {
            throw invalid("不支持的时间桶：" + panel.getBucket());
        }
        panel.setBucket(bucket);

        String chartType = panel.getChartType() == null
                ? null : panel.getChartType().trim().toLowerCase(Locale.ROOT);
        if (chartType == null || chartType.isEmpty()) {
            chartType = DEFAULT_CHART_TYPE;
        }
        if (!PropertyHistoryConstants.SUPPORTED_CHART_TYPES.contains(chartType)) {
            throw invalid("不支持的图型：" + panel.getChartType());
        }
        panel.setChartType(chartType);

        String aggregation = panel.getAggregation() == null
                ? null : panel.getAggregation().trim().toLowerCase(Locale.ROOT);
        if (aggregation == null || aggregation.isEmpty()) {
            aggregation = DEFAULT_AGGREGATION;
        }
        if (!PropertyHistoryConstants.SUPPORTED_AGGREGATIONS.contains(aggregation)) {
            throw invalid("不支持的聚合口径：" + panel.getAggregation());
        }
        panel.setAggregation(aggregation);

        Integer rangeDays = panel.getRangeDays();
        if (rangeDays == null || rangeDays <= 0) {
            rangeDays = DEFAULT_RANGE_DAYS;
        }
        if (rangeDays > maxRangeDays) {
            throw invalid("面板默认跨度（" + rangeDays + " 天）超过上限 " + maxRangeDays + " 天");
        }
        panel.setRangeDays(rangeDays);
    }

    private String serialize(DashboardConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw invalid("看板配置序列化失败");
        }
    }

    /** 详情读取：存量配置理论上已校验过，仍按严格口径解析，脏数据按 {@code 6229} 暴露而非静默吞掉。 */
    private DashboardConfig parseStrict(String raw) {
        try {
            DashboardConfig config = objectMapper.readValue(raw, DashboardConfig.class);
            if (config == null) {
                throw invalid("看板配置不能为空");
            }
            if (config.getPanels() == null) {
                config.setPanels(new ArrayList<>());
            }
            return config;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("看板配置解析失败");
        }
    }

    /** 列表计数用：脏配置不阻断列表，回退为空面板。 */
    private DashboardConfig parseQuietly(String raw) {
        try {
            DashboardConfig config = objectMapper.readValue(raw, DashboardConfig.class);
            if (config == null) {
                return new DashboardConfig();
            }
            if (config.getPanels() == null) {
                config.setPanels(new ArrayList<>());
            }
            return config;
        } catch (Exception e) {
            log.warn("看板配置解析失败，列表以空面板呈现: {}", e.getMessage());
            return new DashboardConfig();
        }
    }

    private DashboardDetailVO toDetail(Dashboard dashboard, DashboardConfig config) {
        DashboardDetailVO vo = new DashboardDetailVO();
        vo.setId(dashboard.getId());
        vo.setName(dashboard.getName());
        vo.setConfig(config);
        vo.setPanelCount(config.getPanels().size());
        vo.setCreatedAt(dashboard.getCreatedAt());
        vo.setUpdatedAt(dashboard.getUpdatedAt());
        return vo;
    }

    private int maxCount() {
        int configured = properties.getMaxCount();
        return configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_MAX_DASHBOARD_COUNT;
    }

    private int maxPanels() {
        int configured = properties.getMaxPanels();
        return configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_MAX_DASHBOARD_PANELS;
    }

    private int maxRangeDays() {
        int configured = propertyHistoryProperties.getMaxRangeDays();
        return configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_MAX_RANGE_DAYS;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.DASHBOARD_INVALID, message);
    }
}
