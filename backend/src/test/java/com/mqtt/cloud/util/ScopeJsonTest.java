package com.mqtt.cloud.util;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 作用域 ID 列表 JSON 工具单测（T-28 实施计划 P2）。
 * <p>
 * 重点覆盖读路径的降级语义：空值 / 非法文本一律返回空列表且不抛异常 —— 库中脏数据不得击穿评估链路；
 * 以及写路径的往返一致性。
 */
class ScopeJsonTest {

    private final ScopeJson scopeJson = new ScopeJson(new ObjectMapper());

    @Test
    void toIdList_should_return_empty_for_null_blank_or_empty_array() {
        assertThat(scopeJson.toIdList(null)).isEmpty();
        assertThat(scopeJson.toIdList("")).isEmpty();
        assertThat(scopeJson.toIdList("   ")).isEmpty();
        assertThat(scopeJson.toIdList("[]")).isEmpty();
    }

    @Test
    void toIdList_should_parse_json_array_in_order() {
        assertThat(scopeJson.toIdList("[1,2,3]")).containsExactly(1L, 2L, 3L);
    }

    @Test
    void toIdList_should_drop_duplicates_and_null_items() {
        assertThat(scopeJson.toIdList("[1,1,2,null,2]")).containsExactly(1L, 2L);
    }

    @Test
    void toIdList_should_degrade_to_empty_on_malformed_text() {
        assertThat(scopeJson.toIdList("not-json")).isEmpty();
        assertThat(scopeJson.toIdList("{")).isEmpty();
    }

    @Test
    void toIdList_should_degrade_to_empty_on_non_numeric_item() {
        assertThat(scopeJson.toIdList("[1,\"abc\"]")).isEmpty();
    }

    @Test
    void write_should_return_null_for_null_or_empty() {
        assertThat(scopeJson.write(null)).isNull();
        assertThat(scopeJson.write(List.of())).isNull();
    }

    @Test
    void write_should_serialize_distinct_ids_and_round_trip() {
        String json = scopeJson.write(Arrays.asList(3L, 1L, 3L, null));

        assertThat(json).isEqualTo("[3,1]");
        assertThat(scopeJson.toIdList(json)).containsExactly(3L, 1L);
    }
}