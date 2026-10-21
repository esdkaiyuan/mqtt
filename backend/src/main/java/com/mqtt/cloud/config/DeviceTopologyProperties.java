package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 子设备与网关代理接入配置（T-26）。
 * <p>拓扑深度与单网关子设备数量是挂载校验的边界输入；
 * {@code enabled} 为现场回滚开关，关闭后拓扑端点返回 404、
 * 授权委托分支不生效，既有直连设备链路不受影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.device-topology")
public class DeviceTopologyProperties {

    /** 是否启用子设备拓扑；false 时拓扑端点返回 404，授权委托分支不生效 */
    private boolean enabled = true;

    /** 拓扑深度上限；根网关为第 1 层，2 表示两层（网关 + 子设备），低于 2 不接受任何挂载 */
    private int maxDepth = 5;

    /** 单网关子设备数量上限 */
    private int maxSubDevicesPerGateway = 200;
}
