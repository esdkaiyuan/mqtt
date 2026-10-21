package com.mqtt.cloud.dto.response;

/**
 * 产品驱动配置（T-31 P4）。
 * <p>
 * {@code Product.driverConfig} 带 {@code @JsonIgnore}，避免产品列表把现场内网地址暴露给
 * 所有登录用户；编辑驱动配置时由 ADMIN 通过独立端点读取。
 */
public record ProductDriverConfigVO(String protocolType, String driverId, String driverConfig) {
}
