package com.mqtt.cloud.service;

import com.mqtt.cloud.common.constant.OtaStatusValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * {@code device/{key}/ota} 上行进度报文的只读模型（T-22 设计文档 §6.3，T-32 扩校验字段）。
 * <p>
 * 设备侧无错误通道，故解析失败一律返回 {@code null}，由调用方丢弃该条并记 WARN；
 * 这里的 {@code status} 是设备上报的<b>小写</b>字面量（{@code downloading} / {@code flashing} /
 * {@code verifying} / {@code success} / {@code failed}），落库前需由服务层映射为
 * {@link OtaStatusValue} 的记录状态（大写）。
 * <p>
 * 解析规则：
 * <ul>
 *   <li>{@code version}、{@code status} 必填；缺一或 {@code status} 不在允许集合内即判非法。</li>
 *   <li>{@code progress} 可选：缺省时仅 {@code success} 推算为 {@code 100}，其余状态保持 {@code null}（不推进）；
 *       显式给出时必须为 {@code 0~100} 的数字，越界即判非法。</li>
 *   <li>{@code fileMd5}（T-32 新增，对应设备侧字段名 {@code fileMd5}）可选：设备下载完成后计算本地
 *       固件摘要并回传，平台据此与 {@code ota_firmware.md5} 比对（G-11-2）。
 *       仅接受 32 位十六进制并统一转小写；未上报为 {@code null}，表示「设备不支持 / 未回传」，
 *       语义是<b>未校验</b>而非校验通过。</li>
 *   <li>{@code message} 可选，非文本按缺省处理，超长按 {@value #MESSAGE_MAX} 截断。</li>
 *   <li>载荷非 JSON 对象（含非 JSON 文本、数组、标量）即判非法。</li>
 * </ul>
 *
 * @param version  固件版本，与任务记录的目标版本比对后方可采纳
 * @param status   设备上报状态（已归一化为小写）
 * @param progress 进度百分比，可为 {@code null} 表示本次不推进
 * @param fileMd5  设备回传的固件 md5（小写），可为 {@code null} 表示未回传
 * @param message  可读描述，已截断，可为 {@code null}
 */
public record OtaProgressPayload(String version, String status, Integer progress, String fileMd5,
                                 String message) {

    /** 允许的设备上报状态集合；越界即视为非法报文。 */
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            OtaStatusValue.DEVICE_DOWNLOADING,
            OtaStatusValue.DEVICE_FLASHING,
            OtaStatusValue.DEVICE_VERIFYING,
            OtaStatusValue.DEVICE_SUCCESS,
            OtaStatusValue.DEVICE_FAILED);

    private static final String FIELD_VERSION = "version";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PROGRESS = "progress";
    private static final String FIELD_FILE_MD5 = "fileMd5";
    private static final String FIELD_MESSAGE = "message";

    private static final int PROGRESS_MIN = 0;
    private static final int PROGRESS_MAX = 100;
    private static final int MD5_LENGTH = 32;
    static final int MESSAGE_MAX = 255;

    /**
     * 解析上行载荷；任何非法情形（非 JSON 对象、必填缺失、状态未知、进度越界）均返回 {@code null}。
     * <p>
     * {@code fileMd5} 取值非法**不同于**进度非法：进度非法说明设备实现有 bug，整条丢弃；
     * 而 md5 是新增的可选字段，未升级固件的老设备可能上报平台不认识的写法，
     * 此时按「未回传」处理并继续推进流程，避免一条可选字段把老设备的升级卡死。
     * <p>
     * {@code objectMapper} 由调用方注入（与项目其余服务一致，不在本类持有静态实例）。
     */
    public static OtaProgressPayload parse(ObjectMapper objectMapper, String payload) {
        if (objectMapper == null || payload == null || payload.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        String version = text(root, FIELD_VERSION);
        String rawStatus = text(root, FIELD_STATUS);
        if (version == null || rawStatus == null) {
            return null;
        }
        String status = rawStatus.toLowerCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            return null;
        }
        Integer progress = resolveProgress(root.get(FIELD_PROGRESS), status);
        if (progress == null && isProgressPresent(root.get(FIELD_PROGRESS))) {
            // 显式给出进度但越界 / 非数字
            return null;
        }
        String fileMd5 = md5(root.get(FIELD_FILE_MD5));
        String message = text(root, FIELD_MESSAGE);
        if (message != null && message.length() > MESSAGE_MAX) {
            message = message.substring(0, MESSAGE_MAX);
        }
        return new OtaProgressPayload(version, status, progress, fileMd5, message);
    }

    /** {@code success} 恒为 {@code 100}（即使设备漏报或报低了进度）；其余状态缺省不推进。 */
    private static Integer resolveProgress(JsonNode node, String status) {
        if (OtaStatusValue.DEVICE_SUCCESS.equals(status)) {
            return PROGRESS_MAX;
        }
        if (!isProgressPresent(node)) {
            return null;
        }
        if (!node.isNumber()) {
            return null;
        }
        int value = node.asInt();
        return (value < PROGRESS_MIN || value > PROGRESS_MAX) ? null : value;
    }

    /** 区分「未上报」与「上报了非法值」：前者合法（不推进），后者丢弃整条报文。 */
    private static boolean isProgressPresent(JsonNode node) {
        return node != null && !node.isNull();
    }

    /**
     * 取设备回传的 md5：必须是 32 位十六进制文本，否则按「未回传」处理（返回 {@code null}）。
     * <p>
     * 统一转小写：多数设备端库输出大写十六进制，而平台侧 {@code ota_firmware.md5} 存小写，
     * 不归一化会把同一份固件判成不一致。
     */
    private static String md5(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim().toLowerCase();
        if (value.length() != MD5_LENGTH) {
            return null;
        }
        for (int i = 0; i < MD5_LENGTH; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return null;
            }
        }
        return value;
    }

    /** 取非空文本并去除首尾空白；非文本或全空白返回 {@code null}。 */
    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}