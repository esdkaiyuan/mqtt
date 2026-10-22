package com.mqtt.cloud.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyScopeServiceTest {

    private final ApiKeyScopeService service = new ApiKeyScopeService(new ObjectMapper());

    @Test
    void 空权限保持全部权限兼容语义() {
        assertThat(service.allows(null, ApiKeyScopeService.DEVICE_WRITE)).isTrue();
        assertThat(service.allows("", ApiKeyScopeService.DEVICE_READ)).isTrue();
        assertThat(service.allows("   ", ApiKeyScopeService.STATS_READ)).isTrue();
    }

    @Test
    void 读写与统计scope分别生效() {
        assertThat(service.allows("[\"device:read\"]", ApiKeyScopeService.DEVICE_READ)).isTrue();
        assertThat(service.allows("[\"device:read\"]", ApiKeyScopeService.DEVICE_WRITE)).isFalse();
        assertThat(service.allows("[\"device:write\"]", ApiKeyScopeService.DEVICE_WRITE)).isTrue();
        assertThat(service.allows("[\"stats:read\"]", ApiKeyScopeService.STATS_READ)).isTrue();
        assertThat(service.allows("[\"device:read\"]", ApiKeyScopeService.STATS_READ)).isFalse();
    }

    @Test
    void 兼容旧版data_read别名与通配符() {
        assertThat(service.allows("[\"data:read\"]", ApiKeyScopeService.DEVICE_READ)).isTrue();
        assertThat(service.allows("[\"data:write\"]", ApiKeyScopeService.DEVICE_WRITE)).isTrue();
        assertThat(service.allows("[\"*\"]", ApiKeyScopeService.STATS_READ)).isTrue();
    }

    @Test
    void 非法或空数组默认拒绝() {
        assertThat(service.allows("not-json", ApiKeyScopeService.DEVICE_READ)).isFalse();
        assertThat(service.allows("[]", ApiKeyScopeService.DEVICE_READ)).isFalse();
        assertThat(service.allows("{}", ApiKeyScopeService.DEVICE_READ)).isFalse();
    }

    @Test
    void 创建密钥时拒绝未知或非法scope() {
        assertThatCode(() -> service.requireValid("[\"device:read\",\"device:write\",\"stats:read\"]"))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.requireValid("")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireValid("[\"unknown:read\"]"))
                .isInstanceOf(com.mqtt.cloud.common.exception.BusinessException.class);
        assertThatThrownBy(() -> service.requireValid("not-json"))
                .isInstanceOf(com.mqtt.cloud.common.exception.BusinessException.class);
    }

    @Test
    void 按方法与路径推导scope() {
        assertThat(service.requiredScope("GET", "/external/v1/devices"))
                .isEqualTo(ApiKeyScopeService.DEVICE_READ);
        assertThat(service.requiredScope("GET", "/external/v1/stats"))
                .isEqualTo(ApiKeyScopeService.STATS_READ);
        assertThat(service.requiredScope("POST", "/external/v1/devices/a/command"))
                .isEqualTo(ApiKeyScopeService.DEVICE_WRITE);
        assertThat(service.requiredScope("PUT", "/external/v1/devices/a/shadow/desired"))
                .isEqualTo(ApiKeyScopeService.DEVICE_WRITE);
    }
}
