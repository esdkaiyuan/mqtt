package com.mqtt.cloud.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CsvReader} 单元测试（T-53）。
 */
class CsvReaderTest {

    @Test
    void parse_should_stripBom_and_supportCrlf() {
        String text = "﻿deviceKey,deviceName\r\ndev-1,传感器\r\n";
        List<List<String>> rows = CsvReader.parse(text.getBytes(StandardCharsets.UTF_8));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("deviceKey", "deviceName");
        assertThat(rows.get(1)).containsExactly("dev-1", "传感器");
    }

    @Test
    void parse_should_handleQuotedCommaQuoteAndNewline() {
        String text = "a,b,c\r\n"
                + "\"x,1\",\"he said \"\"hi\"\"\",\"line1\nline2\"\r\n";
        List<List<String>> rows = CsvReader.parse(text);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("x,1", "he said \"hi\"", "line1\nline2");
    }

    @Test
    void parse_should_acceptLastRowWithoutTrailingNewline() {
        List<List<String>> rows = CsvReader.parse("a,b\n1,2");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("1", "2");
    }

    @Test
    void parse_should_rejectUnclosedQuote() {
        assertThatThrownBy(() -> CsvReader.parse("a,\"b\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("引号未闭合");
    }

    @Test
    void parse_empty_should_returnEmptyList() {
        assertThat(CsvReader.parse("")).isEmpty();
        assertThat(CsvReader.parse((byte[]) null)).isEmpty();
    }
}