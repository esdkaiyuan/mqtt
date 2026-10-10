package com.mqtt.cloud.common.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 点位常量用例（T-31 P1）。
 * <p>
 * 覆盖 {@code executor} 与 {@code pointType} 的归一化口径。
 * <p>
 * <b>为什么归一化规则必须被测试钉死</b>：{@code executor} 是路线 A / B 的唯一分支依据，
 * 一旦「空值 / 小写 / 带空格」的处理发生变化，取点位就会落到错误的一侧——
 * 而这类故障的表现是「平台采不到值」或「边缘采到的值平台看不到」，不报错、不告警，
 * 排查成本极高。口径的每次变更都应先改此测试并说明理由。
 */
class PointValueConstantsTest {

    // ---------- executor ----------

    @Test
    void executor_should_normalize_null_to_edge() {
        // 与 V18 建表的 DEFAULT 'EDGE' 对齐：存量空值必须归到边缘执行而非报错
        assertThat(PointExecutorValue.normalize(null)).isEqualTo(PointExecutorValue.EDGE);
        assertThat(PointExecutorValue.normalize("")).isEqualTo(PointExecutorValue.EDGE);
        assertThat(PointExecutorValue.normalize("   ")).isEqualTo(PointExecutorValue.EDGE);
    }

    @Test
    void executor_should_normalize_case_and_trim() {
        assertThat(PointExecutorValue.normalize("platform")).isEqualTo(PointExecutorValue.PLATFORM);
        assertThat(PointExecutorValue.normalize("  PLATFORM  ")).isEqualTo(PointExecutorValue.PLATFORM);
        assertThat(PointExecutorValue.normalize("edge")).isEqualTo(PointExecutorValue.EDGE);
    }

    @Test
    void executor_should_keep_unknown_value_as_is_for_error_reporting() {
        // 非法值原样返回（而非抛异常或静默改成 EDGE）：让服务层的
        // POINT_EXECUTOR_UNSUPPORTED 能报出用户实际填的值，便于定位是哪里配错了
        assertThat(PointExecutorValue.normalize("GATEWAY")).isEqualTo("GATEWAY");
    }

    @Test
    void isPlatform_should_only_true_for_platform() {
        assertThat(PointExecutorValue.isPlatform("PLATFORM")).isTrue();
        assertThat(PointExecutorValue.isPlatform("platform")).isTrue();
        assertThat(PointExecutorValue.isPlatform("EDGE")).isFalse();
        assertThat(PointExecutorValue.isPlatform(null)).isFalse();
        assertThat(PointExecutorValue.isPlatform("GATEWAY")).isFalse();
    }

    @Test
    void executor_isSupported_should_accept_blank_as_edge() {
        assertThat(PointExecutorValue.isSupported(null)).isTrue();
        assertThat(PointExecutorValue.isSupported("")).isTrue();
        assertThat(PointExecutorValue.isSupported("EDGE")).isTrue();
        assertThat(PointExecutorValue.isSupported("PLATFORM")).isTrue();
        assertThat(PointExecutorValue.isSupported("GATEWAY")).isFalse();
    }

    // ---------- pointType ----------

    @Test
    void pointType_should_normalize_null_to_holding_register() {
        assertThat(PointTypeValue.normalize(null)).isEqualTo(PointTypeValue.HOLDING_REGISTER);
        assertThat(PointTypeValue.normalize("holding_register")).isEqualTo(PointTypeValue.HOLDING_REGISTER);
    }

    @Test
    void pointType_should_support_all_documented_types() {
        for (String type : new String[]{"HOLDING_REGISTER", "INPUT_REGISTER", "COIL",
                "DISCRETE_INPUT", "TAG", "NODE"}) {
            assertThat(PointTypeValue.isSupported(type)).as(type).isTrue();
        }
        assertThat(PointTypeValue.isSupported("REGISTER")).isFalse();
    }

    @Test
    void pointType_should_mark_bit_types_as_single() {
        // 线圈 / 离散输入是单点布尔量，采集时不做寄存器区间合并
        assertThat(PointTypeValue.isSingleBit("COIL")).isTrue();
        assertThat(PointTypeValue.isSingleBit("DISCRETE_INPUT")).isTrue();
        assertThat(PointTypeValue.isSingleBit("HOLDING_REGISTER")).isFalse();
        assertThat(PointTypeValue.isSingleBit(null)).isFalse();
    }

    @Test
    void pointType_should_normalize_unknown_before_single_bit_check() {
        assertThat(PointTypeValue.isSingleBit("coil")).isTrue();
        assertThat(PointTypeValue.isSingleBit(" COIL ")).isTrue();
    }
}
