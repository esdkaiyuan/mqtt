package com.mqtt.cloud.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码枚举用例。
 * <p>校验 T-26 拓扑相关七码自 {@code 6254} 起、T-27 通知相关七码自 {@code 6261} 起、
 * T-28 作用域相关两码自 {@code 6268} 起连续存在，且 HTTP 状态与设计文档 §10.2 / §9.2 一致。
 * 同时兜住「枚举内错误码重复」这一隐性回归：重复码会让按码查找取到错误条目，
 * 而调用方仅凭日志无从察觉。
 */
class ResultCodeEnumTest {

    private static final Map<Integer, ResultCode> BY_CODE = Arrays.stream(ResultCode.values())
            .collect(Collectors.toMap(ResultCode::getCode, Function.identity(), (first, second) -> first));

    @Test
    void topology_codes_must_exist_with_designed_http_status() {
        assertCode(6254, ResultCode.DEVICE_TOPOLOGY_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6255, ResultCode.DEVICE_PARENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertCode(6256, ResultCode.DEVICE_PARENT_KIND_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6257, ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED, HttpStatus.BAD_REQUEST);
        assertCode(6258, ResultCode.DEVICE_PRODUCT_MISMATCH, HttpStatus.BAD_REQUEST);
        assertCode(6259, ResultCode.DEVICE_HAS_SUBDEVICES, HttpStatus.CONFLICT);
        assertCode(6260, ResultCode.DEVICE_SUBDEVICE_LIMIT_EXCEEDED, HttpStatus.BAD_REQUEST);
    }

    @Test
    void notification_codes_must_exist_with_designed_http_status() {
        assertCode(6261, ResultCode.NOTIFICATION_CHANNEL_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertCode(6262, ResultCode.NOTIFICATION_CHANNEL_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6263, ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        assertCode(6264, ResultCode.NOTIFICATION_SEND_FAILED, HttpStatus.BAD_REQUEST);
        assertCode(6265, ResultCode.ESCALATION_POLICY_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertCode(6266, ResultCode.ESCALATION_POLICY_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6267, ResultCode.ALERT_ASSIGNEE_INVALID, HttpStatus.BAD_REQUEST);
    }

    @Test
    void scope_codes_must_exist_with_designed_http_status() {
        assertCode(6268, ResultCode.ALERT_RULE_SCOPE_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6269, ResultCode.RULE_SCOPE_INVALID, HttpStatus.BAD_REQUEST);
    }

    @Test
    void point_table_codes_must_exist_with_designed_http_status() {
        // T-31 点位表。除「点位不存在」外的全部为 BAD_REQUEST：
        // 点表配置属管理面输入错误，返回 400 才能让前端与调用方区分「配置写错」与「服务异常」
        assertCode(6280, ResultCode.POINT_KEY_DUPLICATED, HttpStatus.BAD_REQUEST);
        assertCode(6281, ResultCode.POINT_EXECUTOR_MIXED, HttpStatus.BAD_REQUEST);
        assertCode(6282, ResultCode.POINT_EXECUTOR_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        assertCode(6283, ResultCode.POINT_TYPE_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        assertCode(6284, ResultCode.POINT_ADDRESS_REQUIRED, HttpStatus.BAD_REQUEST);
        assertCode(6285, ResultCode.POINT_ADDRESS_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6286, ResultCode.POINT_INTERVAL_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6287, ResultCode.POINT_SCALE_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6288, ResultCode.POINT_DATA_TYPE_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6289, ResultCode.POINT_BATCH_EMPTY, HttpStatus.BAD_REQUEST);
    }

    @Test
    void debug_and_hmi_codes_must_exist_with_designed_http_status() {
        // T-37 调试中心：类型 / 载荷 / 代码目标均为管理面输入错误，统一 400
        assertCode(6297, ResultCode.DEBUG_MESSAGE_TYPE_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        assertCode(6298, ResultCode.DEBUG_PAYLOAD_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6299, ResultCode.DEBUG_CODE_TARGET_UNSUPPORTED, HttpStatus.BAD_REQUEST);
        // T-38 上位机：不存在 404（不区分越权，避免探测），其余配置类错误 400
        assertCode(6300, ResultCode.HMI_VIEW_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertCode(6301, ResultCode.HMI_VIEW_LIMIT_EXCEEDED, HttpStatus.BAD_REQUEST);
        assertCode(6302, ResultCode.HMI_CONFIG_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6303, ResultCode.HMI_BINDING_INVALID, HttpStatus.BAD_REQUEST);
        assertCode(6304, ResultCode.HMI_WIDGET_TYPE_UNSUPPORTED, HttpStatus.BAD_REQUEST);
    }

    @Test
    void codes_must_be_unique() {
        Map<Integer, Long> counts = Arrays.stream(ResultCode.values())
                .collect(Collectors.groupingBy(ResultCode::getCode, Collectors.counting()));

        counts.forEach((code, count) -> assertThat(count)
                .as("错误码 %d 重复定义，按码查找会取到错误条目", code)
                .isEqualTo(1L));
    }

    private void assertCode(int code, ResultCode expected, HttpStatus httpStatus) {
        ResultCode actual = BY_CODE.get(code);
        assertThat(actual).as("错误码 %d 未定义", code).isNotNull();
        assertThat(actual).as("错误码 %d 的枚举项", code).isEqualTo(expected);
        assertThat(actual.getHttpStatus()).as("错误码 %d 的 HTTP 状态", code).isEqualTo(httpStatus);
    }
}
