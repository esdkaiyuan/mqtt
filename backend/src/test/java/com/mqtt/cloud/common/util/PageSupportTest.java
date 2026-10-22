package com.mqtt.cloud.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageSupportTest {

    @Test
    void 页码缺失或非法时回退到第一页() {
        assertThat(PageSupport.page(null)).isEqualTo(1);
        assertThat(PageSupport.page(0)).isEqualTo(1);
        assertThat(PageSupport.page(-3)).isEqualTo(1);
        assertThat(PageSupport.page(2)).isEqualTo(2);
    }

    @Test
    void 每页数量缺失时使用默认值() {
        assertThat(PageSupport.size(null, 10)).isEqualTo(10);
        assertThat(PageSupport.size(0, 20)).isEqualTo(20);
        assertThat(PageSupport.size(-1, 20)).isEqualTo(20);
    }

    @Test
    void 每页数量超限时收敛到上限() {
        assertThat(PageSupport.size(50, 20)).isEqualTo(50);
        assertThat(PageSupport.size(101, 20)).isEqualTo(100);
        assertThat(PageSupport.size(10_000, 20)).isEqualTo(100);
    }
}
