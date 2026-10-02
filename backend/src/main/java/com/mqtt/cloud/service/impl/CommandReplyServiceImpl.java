package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.DeviceCommandRecordMapper;
import com.mqtt.cloud.service.CommandReplyService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 命令回执处理实现（T-15 设计文档 §5.3 / §9）。
 * <p>
 * 关联只依赖回执 {@code id}（即 {@code command_id}），容忍设备 {@code method} 不精确；
 * 更新走条件更新（仅 {@code PENDING/SENT} 可迁移），受影响行数为 0 表示未知回执或已是终态，
 * 按「重复 / 未知回执」计数忽略 —— 终态一经写入不可被覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandReplyServiceImpl implements CommandReplyService {

    static final String RESULT_ACKED = "acked";
    static final String RESULT_FAILED = "failed";
    static final String RESULT_IGNORED = "ignored";
    static final String RESULT_INVALID = "invalid";

    private static final String MESSAGE_TYPE_REPLY = "reply";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_DATA = "data";
    private static final int CODE_SUCCESS = 200;
    private static final int ERROR_MESSAGE_MAX = 255;

    private final DeviceCommandRecordMapper commandMapper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Override
    public void handle(List<ResolvedEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (ResolvedEvent event : events) {
            try {
                handleOne(event.record());
            } catch (Exception e) {
                count(RESULT_INVALID);
                log.warn("命令回执处理异常，跳过该条: topic={}", event.record().topic(), e);
            }
        }
    }

    private void handleOne(IngestRecord record) {
        if (!MESSAGE_TYPE_REPLY.equals(record.messageType())) {
            return;
        }
        JsonNode root = parse(record.payload());
        if (root == null || !root.isObject()) {
            count(RESULT_INVALID);
            return;
        }
        String commandId = text(root, FIELD_ID);
        if (commandId == null) {
            count(RESULT_INVALID);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        JsonNode code = root.get(FIELD_CODE);
        if (code != null && code.isNumber() && code.asInt() == CODE_SUCCESS) {
            int updated = commandMapper.markAcked(commandId, jsonText(root.get(FIELD_DATA)), now);
            count(updated > 0 ? RESULT_ACKED : RESULT_IGNORED);
            return;
        }
        int updated = commandMapper.markFailed(commandId, describeFailure(code, root.get(FIELD_DATA)), now);
        count(updated > 0 ? RESULT_FAILED : RESULT_IGNORED);
    }

    /** 失败原因取 {@code code} 与 {@code data} 的可读文本，便于在命令记录里直接排查。 */
    private String describeFailure(JsonNode code, JsonNode data) {
        String codeText = scalarText(code);
        String dataText = scalarText(data);
        StringBuilder builder = new StringBuilder();
        if (codeText != null) {
            builder.append("code=").append(codeText);
        }
        if (dataText != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("data=").append(dataText);
        }
        return truncate(builder.length() == 0 ? "回执失败" : builder.toString());
    }

    /** 标量取文本、对象 / 数组取 JSON 文本，空值返回 {@code null}。 */
    private String scalarText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return (node.isObject() || node.isArray()) ? node.toString() : node.asText();
    }

    /** {@code result} 列为 JSON 类型，必须写入合法 JSON 文本。 */
    private String jsonText(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.toString();
    }

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
        return (value != null && value.isTextual() && !value.asText().isBlank()) ? value.asText() : null;
    }

    private String truncate(String message) {
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX);
    }

    private void count(String result) {
        meterRegistry.counter("command_reply_total", "result", result).increment();
    }
}