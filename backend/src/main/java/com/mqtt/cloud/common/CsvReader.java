package com.mqtt.cloud.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 解析器（T-53）。
 * <p>
 * 与 {@link CsvWriter} 配对：导出端负责 BOM / 公式注入防护 / RFC 4180 转义，
 * 导入端必须能把同样的格式解析回来。实现为逐字符状态机，支持：
 * <ul>
 *   <li>UTF-8 BOM 前缀（Excel 另存为 CSV 时通常带 BOM）；</li>
 *   <li>引号包裹的字段，内含逗号、换行与双引号（{@code ""} 转义）；</li>
 *   <li>CRLF / LF 两种换行。</li>
 * </ul>
 * 纯函数、无状态；格式非法（如引号未闭合）抛 {@link IllegalArgumentException}，
 * 由调用方转换为业务错误码。
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<List<String>> parse(byte[] content) {
        if (content == null || content.length == 0) {
            return List.of();
        }
        int offset = 0;
        // UTF-8 BOM：EF BB BF
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        return parse(new String(content, offset, content.length - offset, StandardCharsets.UTF_8));
    }

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    if (field.length() > 0) {
                        // 非字段起始位置的引号按普通字符处理，兼容不规范但常见的导出文件
                        field.append('"');
                    } else {
                        inQuotes = true;
                    }
                }
                case ',' -> {
                    row.add(field.toString());
                    field.setLength(0);
                }
                case '\r' -> {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    endRow(rows, row, field);
                }
                case '\n' -> endRow(rows, row, field);
                default -> field.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("CSV 引号未闭合");
        }
        // 最后一行没有换行符时也要收尾
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder field) {
        row.add(field.toString());
        field.setLength(0);
        // 必须存副本：row 会被复用，直接 add(row) 后 clear() 会把已存的行清空
        rows.add(new ArrayList<>(row));
        row.clear();
    }
}