package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 点位保存请求（T-31）
 * <p>
 * 单条点位配置的写入请求，批量保存时以列表形式提交。
 * <p>
 * {@code pointKey} 一旦保存<b>不建议再改</b>：它是到物模型属性 identifier 的映射键，
 * 改名会导致历史数据里的旧标识与新配置对不上，历史查询出现断层。地址与换算参数可以随时调。
 */
@Data
public class PointTableUpsertRequest {

    /** 点位ID；为空表示新增，非空表示更新该点位。 */
    private Long id;

    /**
     * 点位标识，映射到物模型属性/事件 identifier。
     * <p>只允许字母数字、下划线、中划线与点号——它会拼进物模型匹配路径，
     * 放开特殊字符会让下游的标识解析产生歧义。</p>
     */
    @NotBlank(message = "点位标识不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,128}$", message = "点位标识仅允许字母、数字、下划线、中划线与点号")
    private String pointKey;

    /** 点位类型；为空按 {@code HOLDING_REGISTER} 处理。 */
    @Pattern(regexp = "^(HOLDING_REGISTER|INPUT_REGISTER|COIL|DISCRETE_INPUT|TAG|NODE)$",
            message = "点位类型非法")
    private String pointType;

    /** 地址：寄存器号（数字）或 tag/节点名。寄存器类点位必填。 */
    private String address;

    /** 数据类型 int/float/double/bool/string；为空按 {@code float} 处理（工业量测最常用）。 */
    @Pattern(regexp = "^(int|float|double|bool|string)$", message = "数据类型非法")
    private String dataType;

    /** 线性缩放系数；为空按 {@code 1} 处理。 */
    private BigDecimal scale;

    /** 线性偏移；为空按 {@code 0} 处理。 */
    private BigDecimal offset;

    /** 单位（仅作展示与文档标注，不参与换算）。 */
    private String unit;

    /** 执行方 EDGE / PLATFORM；为空按 {@code EDGE} 处理。 */
    @Pattern(regexp = "^(EDGE|PLATFORM)$", message = "执行方非法")
    private String executor;

    /** 采集周期（毫秒）；为空按 60000。运行期仍会被钳制到配置的最小周期。 */
    @Positive(message = "采集周期必须为正数")
    private Integer readIntervalMs;

    /** 是否启用；为空默认启用。 */
    private Integer enabled;

    /** 无参构造（Jackson / Bean Validation 需要）。 */
    public PointTableUpsertRequest() {
    }
}
