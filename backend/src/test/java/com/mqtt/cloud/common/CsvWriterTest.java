package com.mqtt.cloud.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 导出编码单测（T-36 · X-2）。
 * <p>
 * 重点覆盖三个「不处理就会坑到用户」的点：Excel 中文乱码、公式注入、RFC 4180 转义。
 */
class CsvWriterTest {

    private static String decode(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    @Test
    void should_prefix_utf8_bom_for_excel() {
        byte[] content = CsvWriter.encode(List.of("名称"), List.of(List.of("传感器")));

        // Excel 不认 UTF-8 的裸内容，没有 BOM 打开就是乱码
        assertThat(content).startsWith(CsvWriter.BOM);
        assertThat(decode(content)).endsWith("传感器\r\n");
    }

    @Test
    void should_escape_commas_quotes_and_newlines() {
        byte[] content = CsvWriter.encode(
                List.of("载荷"),
                List.of(List.of("a,b")));
        assertThat(decode(content)).contains("\"a,b\"");

        byte[] quoted = CsvWriter.encode(List.of("x"), List.of(List.of("say \"hi\"")));
        assertThat(decode(quoted)).contains("\"say \"\"hi\"\"\"");

        byte[] newline = CsvWriter.encode(List.of("x"), List.of(List.of("line1\nline2")));
        assertThat(decode(newline)).contains("\"line1\nline2\"");
    }

    @Test
    void should_neutralize_formula_injection() {
        // 设备上报的 payload 完全可控，=HYPERLINK 是经典的凭据钓鱼手法
        byte[] content = CsvWriter.encode(
                List.of("载荷"),
                List.of(List.of("=HYPERLINK(\"http://evil\",\"点我\")")));
        assertThat(decode(content)).contains("'=HYPERLINK");
        assertThat(decode(content)).doesNotContain("\n=HYPERLINK");
    }

    @Test
    void should_neutralize_leading_plus_minus_at_and_tab() {
        for (String payload : List.of("+1+1", "-2+3", "@SUM(A1)", "\tX")) {
            byte[] content = CsvWriter.encode(List.of("c"), List.of(List.of(payload)));
            assertThat(decode(content))
                    .as("以 %s 开头的值必须被前置单引号", payload)
                    .contains("'" + payload);
        }
    }

    @Test
    void should_render_null_as_empty_cell() {
        byte[] content = CsvWriter.encode(List.of("a", "b"),
                List.of(java.util.Arrays.asList("x", null)));

        assertThat(decode(content)).contains("x,\r\n");
    }

    @Test
    void should_write_headers_and_rows_in_order() {
        byte[] content = CsvWriter.encode(
                List.of("时间", "值"),
                List.of(List.of("2026-01-01 00:00:00", "1"), List.of("2026-01-01 00:01:00", "2")));

        String text = decode(content);
        int headerAt = text.indexOf("时间,值");
        int row1At = text.indexOf("2026-01-01 00:00:00,1");
        int row2At = text.indexOf("2026-01-01 00:01:00,2");
        assertThat(headerAt).isLessThan(row1At);
        assertThat(row1At).isLessThan(row2At);
    }

    @Test
    void should_format_time_consistently() {
        assertThat(CsvWriter.time(null)).isEmpty();
        assertThat(CsvWriter.time(java.time.LocalDateTime.of(2026, 10, 11, 16, 35, 15)))
                .isEqualTo("2026-10-11 16:35:15");
    }
}