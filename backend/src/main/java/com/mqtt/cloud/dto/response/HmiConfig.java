package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 上位机控件配置（T-38）。
 * <p>
 * 12 列栅格 + 顺序流：控件按数组顺序排布，{@code span} 表示占几列，不做绝对定位。
 * 所有字段在保存前由服务层按物模型核对，非法配置不落库。
 */
@Data
public class HmiConfig {

    /** 页面标题，缺省用设备名 + 「上位机」 */
    private String title;

    private List<Widget> widgets = new ArrayList<>();

    @Data
    public static class Widget {

        /** 稳定标识，缺省由服务端生成 */
        private String id;

        /** value / switch / slider / input / select / button / chart / events */
        private String type;

        private String title;

        /** 属性或服务标识符；events 类型为空 */
        private String identifier;

        /** 占列数 1~12，缺省 4 */
        private Integer span;

        /** 单位（可选，仅展示） */
        private String unit;

        /** 数值控件下界（缺省取物模型） */
        private BigDecimal min;

        /** 数值控件上界（缺省取物模型） */
        private BigDecimal max;

        /** 数值控件步长（缺省整数 1、小数 0.1） */
        private BigDecimal step;
    }
}
