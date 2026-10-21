package com.mqtt.cloud.common;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * CSV 导出编码器（T-36 · X-2）。
 * <p>
 * 三个容易被忽略但会直接坑到用户的细节，这里一并处理：
 * <ol>
 *   <li><b>UTF-8 BOM</b> —— 不加 BOM 时 Excel 打开中文 CSV 会乱码，而 BOM 会让部分解析器看到多余字符；
 *       故编码结果自带 BOM 前缀，文件名也走 {@code ContentDisposition} 的 UTF-8 编码。</li>
 *   <li><b>公式注入防护</b> —— 单元格以 {@code = + - @ } 开头时 Excel 会当公式执行
 *       （{@code =HYPERLINK("http://evil","点我")} 是经典的凭据钓鱼手法）。
 *       这类值前置一个单引号强制按文本处理。设备上报的 {@code payload} 完全不可控，
 *       所以这不是过度设计。</li>
 *   <li><b>换行与分隔符转义</b> —— 载荷里含逗号、换行、双引号时必须按 RFC 4180 加引号并把内部引号翻倍。</li>
 * </ol>
 * 纯函数、无状态，便于直接单测。
 */
public final class CsvWriter {

    /** UTF-8 BOM：让 Excel 正确识别编码。 */
    public static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvWriter() {
    }

    /**
     * 把行集合编码为带 BOM 的 CSV 字节数组。
     *
     * @param headers 表头列名
     * @param rows    数据行（每行是已格式化的字符串，长度需与表头一致）
     * @return UTF-8 编码的 CSV 内容（含 BOM）
     */
    public static byte[] encode(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        for (List<String> row : rows) {
            appendRow(sb, row);
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    /**
     * 把对象集合编码为 CSV。
     *
     * @param headers   表头列名
     * @param items     数据集合
     * @param extractor 每行取值器，返回值顺序需与表头一致
     */
    public static <T> byte[] encode(List<String> headers, List<T> items, Function<T, List<String>> extractor) {
        List<List<String>> rows = items.stream().map(extractor).toList();
        return encode(headers, rows);
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }

    /**
     * 转义单个单元格。
     * <p>
     * 顺序要点：先判公式注入（只对**首字符**判定）→ 再判是否需加引号 → 最后翻倍内部引号。
     * 判公式注入必须在加引号之前，否则 {@code "=x"} 加了引号就逃不掉了。
     */
    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value;
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            text = "'" + text;
        }
        boolean needQuote = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        if (!needQuote) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /** 时间格式化（导出统一口径，避免各处 `toString()` 长度不一）。 */
    public static String time(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMAT);
    }
}