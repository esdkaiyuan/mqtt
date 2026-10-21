package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.HmiProperties;
import com.mqtt.cloud.dto.request.HmiSaveDTO;
import com.mqtt.cloud.dto.response.HmiConfig;
import com.mqtt.cloud.dto.response.HmiDetailVO;
import com.mqtt.cloud.dto.response.HmiDraftVO;
import com.mqtt.cloud.dto.response.HmiModelVO;
import com.mqtt.cloud.dto.response.HmiSummaryVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.HmiView;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.HmiViewMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.HmiViewService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 快速上位机实现（T-38）。
 * <p>
 * 生成器与校验器共用同一套控件类型与绑定规则：生成器只产出「能通过校验」的控件，
 * 校验器则拒绝任何手工提交的非法绑定。两者若各写一套，很快就会出现
 * 「生成的草稿保存失败」或「手工配置绕过物模型限制」两类问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HmiViewServiceImpl implements HmiViewService {

    static final String TYPE_VALUE = "value";
    static final String TYPE_SWITCH = "switch";
    static final String TYPE_SLIDER = "slider";
    static final String TYPE_INPUT = "input";
    static final String TYPE_SELECT = "select";
    static final String TYPE_BUTTON = "button";
    static final String TYPE_CHART = "chart";
    static final String TYPE_EVENTS = "events";

    static final Set<String> WIDGET_TYPES = Set.of(TYPE_VALUE, TYPE_SWITCH, TYPE_SLIDER, TYPE_INPUT,
            TYPE_SELECT, TYPE_BUTTON, TYPE_CHART, TYPE_EVENTS);
    static final Set<String> NUMERIC_TYPES = Set.of("int", "integer", "long", "float", "double", "number");
    static final Set<String> BOOL_TYPES = Set.of("bool", "boolean");
    static final Set<String> ENUM_TYPES = Set.of("enum");
    static final Set<String> TEXT_TYPES = Set.of("text", "string");

    private static final int DEFAULT_MAX_COUNT = 20;
    private static final int DEFAULT_MAX_WIDGETS = 30;
    private static final int DEFAULT_SPAN = 4;
    private static final int MAX_SPAN = 12;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 64;
    private static final int MAX_UNIT_LENGTH = 16;

    private final HmiViewMapper hmiViewMapper;
    private final HmiProperties properties;
    private final DeviceService deviceService;
    private final ThingModelService thingModelService;
    private final ObjectMapper objectMapper;

    @Override
    public List<HmiSummaryVO> list(Long userId) {
        List<HmiView> rows = hmiViewMapper.selectList(new LambdaQueryWrapper<HmiView>()
                .eq(HmiView::getUserId, userId)
                .orderByDesc(HmiView::getUpdatedAt)
                .orderByDesc(HmiView::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> deviceIds = rows.stream().map(HmiView::getDeviceId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Device> devices = deviceIds.isEmpty() ? Map.of()
                : deviceService.listByIds(deviceIds).stream()
                        .collect(Collectors.toMap(Device::getId, Function.identity(), (left, right) -> left));

        List<HmiSummaryVO> result = new ArrayList<>(rows.size());
        for (HmiView row : rows) {
            HmiSummaryVO vo = new HmiSummaryVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setDeviceId(row.getDeviceId());
            Device device = devices.get(row.getDeviceId());
            if (device != null) {
                vo.setDeviceKey(device.getDeviceKey());
                vo.setDeviceName(device.getDeviceName());
            }
            HmiConfig config = parseQuietly(row.getConfig());
            vo.setWidgetCount(config.getWidgets() == null ? 0 : config.getWidgets().size());
            vo.setCreatedAt(row.getCreatedAt());
            vo.setUpdatedAt(row.getUpdatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HmiDetailVO create(Long userId, Device device, Product product, HmiSaveDTO dto) {
        int maxCount = maxCount();
        Long existing = hmiViewMapper.selectCount(new LambdaQueryWrapper<HmiView>()
                .eq(HmiView::getUserId, userId));
        if (existing != null && existing >= maxCount) {
            throw new BusinessException(ResultCode.HMI_VIEW_LIMIT_EXCEEDED,
                    "上位机数量已达上限 " + maxCount);
        }
        String name = normalizeName(dto);
        HmiConfig config = validateConfig(dto, device, product);

        HmiView view = new HmiView();
        view.setUserId(userId);
        view.setDeviceId(device.getId());
        view.setName(name);
        view.setConfig(serialize(config));
        hmiViewMapper.insert(view);
        HmiView saved = hmiViewMapper.selectById(view.getId());
        return toDetail(saved == null ? view : saved, device, config);
    }

    @Override
    public HmiDetailVO detail(Long userId, Long id) {
        HmiView view = getOwned(userId, id);
        Device device = deviceService.getDeviceById(view.getDeviceId());
        return toDetail(view, device, parseStrict(view.getConfig()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HmiDetailVO update(Long userId, Long id, Device device, Product product, HmiSaveDTO dto) {
        HmiView view = getOwned(userId, id);
        String name = normalizeName(dto);
        HmiConfig config = validateConfig(dto, device, product);

        view.setDeviceId(device.getId());
        view.setName(name);
        view.setConfig(serialize(config));
        hmiViewMapper.updateById(view);
        HmiView saved = hmiViewMapper.selectById(view.getId());
        return toDetail(saved == null ? view : saved, device, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        HmiView view = getOwned(userId, id);
        hmiViewMapper.deleteById(view.getId());
    }

    @Override
    public HmiDraftVO draft(Device device, Product product) {
        ThingModelDefinition model = thingModelService.getForProduct(product.getId());
        List<String> notices = new ArrayList<>();
        List<HmiConfig.Widget> widgets = new ArrayList<>();

        List<ThingModelDefinition.PropertySpec> propertySpecs = model.properties().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.PropertySpec::identifier))
                .toList();
        for (ThingModelDefinition.PropertySpec spec : propertySpecs) {
            if (!spec.writable()) {
                widgets.add(valueWidget(spec));
            }
        }
        for (ThingModelDefinition.PropertySpec spec : propertySpecs) {
            if (spec.writable()) {
                HmiConfig.Widget widget = controlWidget(spec, notices);
                if (widget != null) {
                    widgets.add(widget);
                }
            }
        }
        // 趋势图优先选只读数值属性（传感器量），没有时再退到可写设定值
        ThingModelDefinition.PropertySpec chartSpec = null;
        for (ThingModelDefinition.PropertySpec spec : propertySpecs) {
            if (isNumeric(spec.type()) && !spec.writable()) {
                chartSpec = spec;
                break;
            }
        }
        if (chartSpec == null) {
            for (ThingModelDefinition.PropertySpec spec : propertySpecs) {
                if (isNumeric(spec.type())) {
                    chartSpec = spec;
                    break;
                }
            }
        }
        if (chartSpec != null) {
            widgets.add(chartWidget(chartSpec));
        }
        List<ThingModelDefinition.ServiceSpec> services = model.services().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.ServiceSpec::identifier))
                .toList();
        for (ThingModelDefinition.ServiceSpec service : services) {
            widgets.add(buttonWidget(service));
        }

        int maxWidgets = maxWidgets();
        if (widgets.size() >= maxWidgets) {
            widgets = new ArrayList<>(widgets.subList(0, maxWidgets - 1));
            notices.add("控件数量超过上限 " + maxWidgets + "，已截断并保留「最近事件」面板。");
        }
        widgets.add(eventsWidget());

        if (model.version() == 0) {
            notices.add("产品尚未定义物模型，只生成了「最近事件」面板；请先在产品页定义属性与服务。");
        }
        HmiConfig config = new HmiConfig();
        config.setTitle(device.getDeviceName() + "上位机");
        config.setWidgets(widgets);
        assignIds(config);
        return new HmiDraftVO(device.getId(), device.getDeviceKey(), device.getDeviceName(),
                device.getDeviceName() + "上位机", config, notices);
    }

    private HmiConfig.Widget valueWidget(ThingModelDefinition.PropertySpec spec) {
        HmiConfig.Widget widget = baseWidget(TYPE_VALUE, spec.identifier());
        widget.setSpan(3);
        return widget;
    }

    private HmiConfig.Widget controlWidget(ThingModelDefinition.PropertySpec spec, List<String> notices) {
        String type = typeOf(spec.type());
        if (BOOL_TYPES.contains(type)) {
            HmiConfig.Widget widget = baseWidget(TYPE_SWITCH, spec.identifier());
            widget.setSpan(3);
            return widget;
        }
        if (ENUM_TYPES.contains(type)) {
            HmiConfig.Widget widget = baseWidget(TYPE_SELECT, spec.identifier());
            widget.setSpan(3);
            return widget;
        }
        if (isNumeric(type)) {
            boolean bounded = spec.min() != null && spec.max() != null;
            HmiConfig.Widget widget = baseWidget(bounded ? TYPE_SLIDER : TYPE_INPUT, spec.identifier());
            widget.setSpan(bounded ? 4 : 3);
            widget.setMin(spec.min());
            widget.setMax(spec.max());
            widget.setStep(spec.integer() ? BigDecimal.ONE : new BigDecimal("0.1"));
            return widget;
        }
        if (TEXT_TYPES.contains(type)) {
            HmiConfig.Widget widget = baseWidget(TYPE_INPUT, spec.identifier());
            widget.setSpan(4);
            return widget;
        }
        notices.add("属性「" + spec.identifier() + "」类型为 " + spec.type() + "，暂不自动生成可写控件。");
        return null;
    }

    private HmiConfig.Widget chartWidget(ThingModelDefinition.PropertySpec spec) {
        HmiConfig.Widget widget = baseWidget(TYPE_CHART, spec.identifier());
        widget.setTitle(spec.identifier() + " 趋势");
        widget.setSpan(12);
        return widget;
    }

    private HmiConfig.Widget buttonWidget(ThingModelDefinition.ServiceSpec service) {
        HmiConfig.Widget widget = baseWidget(TYPE_BUTTON, service.identifier());
        widget.setSpan(3);
        return widget;
    }

    private HmiConfig.Widget eventsWidget() {
        HmiConfig.Widget widget = new HmiConfig.Widget();
        widget.setType(TYPE_EVENTS);
        widget.setTitle("最近事件");
        widget.setSpan(12);
        return widget;
    }

    private HmiConfig.Widget baseWidget(String type, String identifier) {
        HmiConfig.Widget widget = new HmiConfig.Widget();
        widget.setType(type);
        widget.setIdentifier(identifier);
        widget.setTitle(identifier);
        return widget;
    }

    /** 归属校验：不存在或非本人一律 6300，不区分以免探测。 */
    private HmiView getOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.HMI_VIEW_NOT_FOUND);
        }
        HmiView view = hmiViewMapper.selectById(id);
        if (view == null || !view.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.HMI_VIEW_NOT_FOUND);
        }
        return view;
    }

    private String normalizeName(HmiSaveDTO dto) {
        String name = dto == null ? null : dto.getName();
        String trimmed = name == null ? null : name.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new BusinessException(ResultCode.HMI_CONFIG_INVALID, "上位机名称不能为空");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ResultCode.HMI_CONFIG_INVALID,
                    "上位机名称不能超过 " + MAX_NAME_LENGTH + " 字符");
        }
        return trimmed;
    }

    private HmiConfig validateConfig(HmiSaveDTO dto, Device device, Product product) {
        String raw = dto == null ? null : dto.getConfig();
        if (!StringUtils.hasText(raw)) {
            throw invalid("上位机配置不能为空");
        }
        HmiConfig config;
        try {
            config = objectMapper.readValue(raw, HmiConfig.class);
        } catch (Exception e) {
            throw invalid("上位机配置不是合法 JSON");
        }
        if (config == null) {
            throw invalid("上位机配置不能为空");
        }
        if (config.getWidgets() == null) {
            config.setWidgets(new ArrayList<>());
        }
        if (config.getWidgets().isEmpty()) {
            throw invalid("上位机至少需要一个控件");
        }
        int maxWidgets = maxWidgets();
        if (config.getWidgets().size() > maxWidgets) {
            throw invalid("控件数量（" + config.getWidgets().size() + "）超过上限 " + maxWidgets);
        }
        String title = config.getTitle() == null ? null : config.getTitle().trim();
        if (title == null || title.isEmpty()) {
            title = device.getDeviceName() + "上位机";
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw invalid("上位机标题不能超过 " + MAX_TITLE_LENGTH + " 字符");
        }
        config.setTitle(title);

        ThingModelDefinition model = thingModelService.getForProduct(product.getId());
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < config.getWidgets().size(); i++) {
            validateWidget(config.getWidgets().get(i), i, ids, model);
        }
        assignIds(config);
        return config;
    }

    private void validateWidget(HmiConfig.Widget widget, int index, Set<String> ids, ThingModelDefinition model) {
        if (widget == null) {
            throw invalid("控件不能为空");
        }
        String type = typeOf(widget.getType());
        if (!WIDGET_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.HMI_WIDGET_TYPE_UNSUPPORTED,
                    "控件类型非法: " + widget.getType());
        }
        widget.setType(type);
        int span = widget.getSpan() == null ? DEFAULT_SPAN : widget.getSpan();
        if (span < 1 || span > MAX_SPAN) {
            throw invalid("控件跨度必须在 1~" + MAX_SPAN + " 之间");
        }
        widget.setSpan(span);

        if (TYPE_EVENTS.equals(type)) {
            widget.setIdentifier(null);
            widget.setMin(null);
            widget.setMax(null);
            widget.setStep(null);
            normalizeText(widget, index, ids, "最近事件");
            return;
        }

        String identifier = widget.getIdentifier() == null ? null : widget.getIdentifier().trim();
        if (!StringUtils.hasText(identifier)) {
            throw binding("控件「" + safeTitle(widget) + "」缺少绑定标识符");
        }
        widget.setIdentifier(identifier);

        if (TYPE_BUTTON.equals(type)) {
            ThingModelDefinition.ServiceSpec service = model.services().get(identifier);
            if (service == null) {
                throw binding("服务不存在: " + identifier);
            }
            normalizeText(widget, index, ids, identifier);
            return;
        }

        ThingModelDefinition.PropertySpec spec = model.properties().get(identifier);
        if (spec == null) {
            throw binding("属性不存在: " + identifier);
        }
        String propertyType = typeOf(spec.type());
        switch (type) {
            case TYPE_VALUE -> {
                // 任意属性都可只读展示
            }
            case TYPE_CHART -> {
                if (!isNumeric(propertyType)) {
                    throw binding("趋势图只支持数值属性: " + identifier);
                }
            }
            case TYPE_SWITCH -> {
                if (!BOOL_TYPES.contains(propertyType)) {
                    throw binding("开关只支持布尔属性: " + identifier);
                }
                requireWritable(spec);
            }
            case TYPE_SELECT -> {
                if (!ENUM_TYPES.contains(propertyType)) {
                    throw binding("下拉框只支持枚举属性: " + identifier);
                }
                requireWritable(spec);
            }
            case TYPE_SLIDER -> {
                if (!isNumeric(propertyType)) {
                    throw binding("滑块只支持数值属性: " + identifier);
                }
                requireWritable(spec);
                normalizeNumberBounds(widget, spec);
            }
            case TYPE_INPUT -> {
                if (!isNumeric(propertyType) && !TEXT_TYPES.contains(propertyType)) {
                    throw binding("输入框只支持数值或文本属性: " + identifier);
                }
                requireWritable(spec);
                if (isNumeric(propertyType)) {
                    normalizeNumberBounds(widget, spec);
                }
            }
            default -> throw new BusinessException(ResultCode.HMI_WIDGET_TYPE_UNSUPPORTED);
        }
        normalizeText(widget, index, ids, identifier);
    }

    private void requireWritable(ThingModelDefinition.PropertySpec spec) {
        if (!spec.writable()) {
            throw binding("属性不可写: " + spec.identifier());
        }
    }

    private void normalizeNumberBounds(HmiConfig.Widget widget, ThingModelDefinition.PropertySpec spec) {
        if (widget.getMin() == null) {
            widget.setMin(spec.min());
        }
        if (widget.getMax() == null) {
            widget.setMax(spec.max());
        }
        if (widget.getMin() != null && widget.getMax() != null
                && widget.getMin().compareTo(widget.getMax()) > 0) {
            throw invalid("控件「" + safeTitle(widget) + "」的最小值不能大于最大值");
        }
        if (widget.getStep() == null) {
            widget.setStep(spec.integer() ? BigDecimal.ONE : new BigDecimal("0.1"));
        }
        if (widget.getStep().signum() <= 0) {
            throw invalid("控件「" + safeTitle(widget) + "」的步长必须大于 0");
        }
    }

    private void normalizeText(HmiConfig.Widget widget, int index, Set<String> ids, String defaultTitle) {
        String title = widget.getTitle() == null ? null : widget.getTitle().trim();
        if (!StringUtils.hasText(title)) {
            title = defaultTitle;
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw invalid("控件标题不能超过 " + MAX_TITLE_LENGTH + " 字符");
        }
        widget.setTitle(title);

        String unit = widget.getUnit() == null ? null : widget.getUnit().trim();
        if (unit != null && unit.length() > MAX_UNIT_LENGTH) {
            throw invalid("控件单位不能超过 " + MAX_UNIT_LENGTH + " 字符");
        }
        widget.setUnit(StringUtils.hasText(unit) ? unit : null);

        String id = widget.getId() == null ? null : widget.getId().trim();
        if (!StringUtils.hasText(id) || ids.contains(id)) {
            id = "w" + (index + 1);
            while (ids.contains(id)) {
                id = id + "_" + (index + 1);
            }
        }
        ids.add(id);
        widget.setId(id);
    }

    private void assignIds(HmiConfig config) {
        Set<String> ids = new HashSet<>();
        List<HmiConfig.Widget> widgets = config.getWidgets();
        for (int i = 0; i < widgets.size(); i++) {
            HmiConfig.Widget widget = widgets.get(i);
            String id = widget.getId();
            if (!StringUtils.hasText(id) || !ids.add(id)) {
                id = "w" + (i + 1);
                while (!ids.add(id)) {
                    id = id + "_" + (i + 1);
                }
                widget.setId(id);
            }
        }
    }

    private HmiModelVO buildModel(Long productId) {
        ThingModelDefinition definition = thingModelService.getForProduct(productId);
        List<HmiModelVO.Property> properties = definition.properties().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.PropertySpec::identifier))
                .map(spec -> new HmiModelVO.Property(spec.identifier(), spec.type(), spec.accessMode(),
                        spec.min(), spec.max(), spec.integer(), List.copyOf(spec.enumKeys()), spec.textLength()))
                .toList();
        List<HmiModelVO.Service> services = definition.services().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.ServiceSpec::identifier))
                .map(service -> new HmiModelVO.Service(service.identifier(), service.callType(),
                        service.input().values().stream()
                                .sorted(Comparator.comparing(ThingModelDefinition.ParamSpec::identifier))
                                .map(param -> new HmiModelVO.Param(param.identifier(), param.type(), param.min(),
                                        param.max(), param.integer(), List.copyOf(param.enumKeys()),
                                        param.textLength(), param.required()))
                                .toList()))
                .toList();
        return new HmiModelVO(properties, services);
    }

    private HmiConfig parseStrict(String raw) {
        try {
            HmiConfig config = objectMapper.readValue(raw, HmiConfig.class);
            if (config == null) {
                return new HmiConfig();
            }
            if (config.getWidgets() == null) {
                config.setWidgets(new ArrayList<>());
            }
            return config;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.HMI_CONFIG_INVALID, "存量上位机配置无法解析");
        }
    }

    private HmiConfig parseQuietly(String raw) {
        try {
            HmiConfig config = objectMapper.readValue(raw, HmiConfig.class);
            if (config == null) {
                return new HmiConfig();
            }
            if (config.getWidgets() == null) {
                config.setWidgets(new ArrayList<>());
            }
            return config;
        } catch (Exception e) {
            log.warn("上位机配置解析失败，列表按空控件处理: {}", e.getMessage());
            return new HmiConfig();
        }
    }

    private String serialize(HmiConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.HMI_CONFIG_INVALID, "上位机配置序列化失败");
        }
    }

    private HmiDetailVO toDetail(HmiView view, Device device, HmiConfig config) {
        HmiDetailVO vo = new HmiDetailVO();
        vo.setId(view.getId());
        vo.setName(view.getName());
        vo.setDeviceId(view.getDeviceId());
        if (device != null) {
            vo.setDeviceKey(device.getDeviceKey());
            vo.setDeviceName(device.getDeviceName());
        }
        vo.setModel(device == null ? new HmiModelVO(List.of(), List.of()) : buildModel(device.getProductId()));
        vo.setConfig(config);
        vo.setWidgetCount(config.getWidgets() == null ? 0 : config.getWidgets().size());
        vo.setCreatedAt(view.getCreatedAt());
        vo.setUpdatedAt(view.getUpdatedAt());
        return vo;
    }

    private int maxCount() {
        return properties.getMaxCount() > 0 ? properties.getMaxCount() : DEFAULT_MAX_COUNT;
    }

    private int maxWidgets() {
        return properties.getMaxWidgets() > 0 ? properties.getMaxWidgets() : DEFAULT_MAX_WIDGETS;
    }

    private String typeOf(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isNumeric(String type) {
        return NUMERIC_TYPES.contains(typeOf(type));
    }

    private String safeTitle(HmiConfig.Widget widget) {
        return StringUtils.hasText(widget.getTitle()) ? widget.getTitle() : widget.getIdentifier();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResultCode.HMI_CONFIG_INVALID, message);
    }

    private BusinessException binding(String message) {
        return new BusinessException(ResultCode.HMI_BINDING_INVALID, message);
    }
}
