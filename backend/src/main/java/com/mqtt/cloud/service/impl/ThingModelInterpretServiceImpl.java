package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.ThingModelProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceEventRecord;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.DeviceEventRecordMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.service.DeviceShadowService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelInterpretService;
import com.mqtt.cloud.service.ThingModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上行数据解析实现（T-14 设计文档 §7.2 六步）。
 * <p>
 * 逐条隔离：单条消息的解析 / 写库异常只记 WARN 与 error 指标，不抛出、不中断本批其余消息、
 * 不进死信（死信只兜「落库失败」，解析产物可由后续上行自然覆盖）。因此本类<b>不开启事务</b>，
 * 让每条 upsert 独立提交，避免一条脏数据回滚整批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThingModelInterpretServiceImpl implements ThingModelInterpretService {

    static final String RESULT_PROPERTY = "property";
    static final String RESULT_EVENT = "event";
    static final String RESULT_UNPARSED = "unparsed";
    static final String RESULT_UNMODELED = "unmodeled";
    static final String RESULT_UNKNOWN_IDENTIFIER = "unknown_identifier";
    static final String RESULT_ERROR = "error";

    private static final String MESSAGE_TYPE_DATA = "data";
    private static final String METHOD_PROPERTY_POST = "thing.event.property.post";
    private static final String METHOD_EVENT_POST = "thing.event.post";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_EVENT_ID = "eventId";
    private static final String FIELD_VALUE = "value";

    private final ThingModelService thingModelService;
    private final DevicePropertyLatestMapper propertyLatestMapper;
    private final DeviceEventRecordMapper eventRecordMapper;
    private final DeviceShadowService deviceShadowService;
    private final ThingModelInterpretMetrics metrics;
    private final ThingModelProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void interpret(List<ResolvedEvent> events) {
        if (!properties.isInterpretEnabled() || events == null || events.isEmpty()) {
            return;
        }
        List<DeviceEventRecord> eventRecords = new ArrayList<>();
        for (ResolvedEvent event : events) {
            try {
                interpretOne(event, eventRecords);
            } catch (Exception e) {
                metrics.count(RESULT_ERROR);
                log.warn("物模型解析异常，跳过该条: topic={}", event.record().topic(), e);
            }
        }
        flushEvents(eventRecords);
    }

    private void interpretOne(ResolvedEvent event, List<DeviceEventRecord> eventRecords) {
        IngestRecord record = event.record();
        // 仅解析业务数据（data），心跳 / 遗嘱不含属性语义
        if (!MESSAGE_TYPE_DATA.equals(record.messageType())) {
            return;
        }
        Device device = event.device();
        if (device == null || device.getId() == null || device.getProductId() == null) {
            metrics.count(RESULT_UNMODELED);
            return;
        }
        ThingModelDefinition definition = thingModelService.getForProduct(device.getProductId());
        if (definition.isEmpty()) {
            metrics.count(RESULT_UNMODELED);
            return;
        }
        JsonNode root = parse(record.payload());
        if (root == null || !root.isObject()) {
            metrics.count(RESULT_UNPARSED);
            return;
        }
        String method = text(root, FIELD_METHOD);
        if (method == null) {
            metrics.count(RESULT_UNPARSED);
            return;
        }
        if (METHOD_PROPERTY_POST.equals(method)) {
            interpretProperties(device, definition, root, record);
        } else if (METHOD_EVENT_POST.equals(method)) {
            interpretEvent(device, definition, root, record, eventRecords);
        }
        // 其它 method（含 T-15 的服务回执）本期不解析
    }

    /** 属性上报：params 为 identifier → 值 平铺映射，逐个命中定义后按类型校验并 upsert。 */
    private void interpretProperties(Device device, ThingModelDefinition definition,
                                     JsonNode root, IngestRecord record) {
        JsonNode params = root.get(FIELD_PARAMS);
        if (params == null || !params.isObject() || params.isEmpty()) {
            return;
        }
        List<String> applied = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : params.properties()) {
            String identifier = entry.getKey();
            ThingModelDefinition.PropertySpec spec = definition.properties().get(identifier);
            if (spec == null) {
                metrics.count(RESULT_UNKNOWN_IDENTIFIER);
                continue;
            }
            String valueText = ThingModelParamValidator.normalize(spec, entry.getValue());
            if (valueText == null) {
                metrics.count(RESULT_UNKNOWN_IDENTIFIER);
                continue;
            }
            propertyLatestMapper.upsertIfNewer(device.getId(), identifier, spec.type(), valueText,
                    record.receivedAt());
            metrics.count(RESULT_PROPERTY);
            applied.add(identifier);
        }
        // T-16：上报收敛。回读权威库合并进影子 reported，异常只记 WARN，不影响本批其余消息。
        applyReported(device, applied);
    }

    /** 上报后的影子收敛；单条合并异常只记 WARN，不冒泡（沿用 T-14 逐条隔离）。 */
    private void applyReported(Device device, List<String> identifiers) {
        if (identifiers.isEmpty()) {
            return;
        }
        try {
            deviceShadowService.applyReported(device.getId(), identifiers);
        } catch (Exception e) {
            log.warn("影子 reported 合并失败，跳过: deviceId={}", device.getId(), e);
        }
    }

    /** 事件上报：params.eventId 命中事件定义后追加记录，event_type 取自物模型。 */
    private void interpretEvent(Device device, ThingModelDefinition definition, JsonNode root,
                                IngestRecord record, List<DeviceEventRecord> eventRecords) {
        JsonNode params = root.get(FIELD_PARAMS);
        if (params == null || !params.isObject()) {
            return;
        }
        String eventId = text(params, FIELD_EVENT_ID);
        if (eventId == null) {
            return;
        }
        ThingModelDefinition.EventSpec spec = definition.events().get(eventId);
        if (spec == null) {
            metrics.count(RESULT_UNKNOWN_IDENTIFIER);
            return;
        }
        JsonNode value = params.get(FIELD_VALUE);
        DeviceEventRecord eventRecord = new DeviceEventRecord();
        eventRecord.setDeviceId(device.getId());
        eventRecord.setIdentifier(eventId);
        eventRecord.setEventType(spec.eventType());
        eventRecord.setOutputData(value == null || value.isNull() ? null : value.toString());
        eventRecord.setReportedAt(record.receivedAt());
        eventRecords.add(eventRecord);
    }

    /** 事件批量写入成功后才计 event 指标，避免「未真正落库却已计数」。 */
    private void flushEvents(List<DeviceEventRecord> eventRecords) {
        if (eventRecords.isEmpty()) {
            return;
        }
        try {
            eventRecordMapper.insertBatch(eventRecords);
            metrics.count(RESULT_EVENT, eventRecords.size());
        } catch (Exception e) {
            metrics.count(RESULT_ERROR);
            log.warn("事件记录批量写入失败，丢弃本批 {} 条事件", eventRecords.size(), e);
        }
    }

    /**
     * 类型与范围校验并文本化的实现见 {@link ThingModelParamValidator}：与下行命令校验共用同一套规则，
     * 避免上行「宽松跳过」与下行「严格拒绝」各自维护一份类型判定而产生漂移。
     */
    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }
}