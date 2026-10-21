package com.mqtt.cloud.service;

import com.mqtt.cloud.common.constant.DeviceNodeType;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.config.RealtimeProperties;
import com.mqtt.cloud.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 授权判定（T-25 §8.4）。
 * <p>
 * 三类身份各走一条分支：平台账号、前端只读账号、设备账号。设备分支的归属前缀由产品模板解析，
 * 平台分支同样如此 —— 共享订阅按模板生成后，平台能订阅的正是各启用产品的模板命名空间，
 * 若平台分支仍写死 {@code device/}，自定义模板下平台自身连共享订阅都会被拒（T-25 §2.1）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AclEvaluator {

    public static final String ACTION_PUBLISH = "publish";
    public static final String ACTION_SUBSCRIBE = "subscribe";
    public static final String FRONTEND_USERNAME = "FRONTEND_READONLY";

    private static final String TOPIC_ROOT = "device/";
    private static final String SUBSCRIPTION_WILDCARD = "+";
    private static final String CMD_SEGMENT = "/cmd/";

    private final AccessControlProperties accessControlProperties;
    private final RealtimeProperties realtimeProperties;
    private final DeviceSecretService deviceSecretService;
    private final DeviceAccessGuard deviceAccessGuard;
    private final TopicPrefixResolver topicPrefixResolver;
    private final ProductService productService;

    public boolean allow(String username, String action, String topic) {
        if (!accessControlProperties.isEnforceAuth()) {
            return true;
        }
        if (username == null || action == null || topic == null) {
            return false;
        }
        if (deviceSecretService.isPlatformUsername(username)) {
            return allowPlatform(action, topic);
        }
        if (FRONTEND_USERNAME.equalsIgnoreCase(username)) {
            return realtimeProperties.isDirectFrontendEnabled()
                    && ACTION_SUBSCRIBE.equals(action)
                    && topic.startsWith(TOPIC_ROOT)
                    && topic.endsWith("/data");
        }
        String[] parts = deviceSecretService.parseUsername(username);
        if (parts == null) {
            return false;
        }
        // 与认证回调同源：产品停用/设备禁用后，已连接会话的收发同样被拒。
        // 此前 ACL 只校验主题前缀，形成「禁用只作用于连接、不作用于收发」的缺口。
        DeviceAuthCacheService.AuthMeta meta = deviceAccessGuard.resolve(parts[0], parts[1]);
        if (!deviceAccessGuard.isPermitted(meta)) {
            return false;
        }
        // 归属前缀由产品模板解析：默认模板 device/{deviceKey} 与既有硬编码前缀逐字一致
        return allowDevice(parts[0], parts[1], meta.topicPrefix(), action, topic);
    }

    /**
     * 平台账号（backend 自身）只允许在启用产品的模板命名空间内收发：订阅侧用于承载共享订阅，
     * 发布侧仅限下发命令。命名空间由各启用产品的 {@code topic_prefix} 解析而来，
     * 产品停用后其命名空间自动从允许集合中消失，与共享订阅「只订阅启用产品」保持同源。
     */
    private boolean allowPlatform(String action, String topic) {
        boolean subscribe = ACTION_SUBSCRIBE.equals(action);
        boolean publish = ACTION_PUBLISH.equals(action);
        if (!subscribe && !publish) {
            return false;
        }
        if (publish && !topic.contains(CMD_SEGMENT)) {
            return false;
        }
        for (String prefix : platformTopicPrefixes()) {
            if (prefixMatches(prefix, topic)) {
                return true;
            }
        }
        return false;
    }

    /** 各启用产品的共享订阅前缀，设备位已代入 {@code +}。 */
    private List<String> platformTopicPrefixes() {
        List<Product> products = productService.listEnabled();
        List<String> prefixes = new ArrayList<>(products.size());
        for (Product product : products) {
            try {
                prefixes.add(topicPrefixResolver.resolveForSubscription(
                        product.getTopicPrefix(), product.getProductKey()));
            } catch (BusinessException e) {
                // 模板在写入期已校验，此处仅作兜底：单条脏数据不应让整条授权链路不可用
                log.warn("产品主题前缀模板解析失败，跳过该产品: productKey={}", product.getProductKey(), e);
            }
        }
        return prefixes;
    }

    private boolean allowDevice(String productKey, String deviceKey, String topicPrefix,
                                String action, String topic) {
        String ownPrefix = topicPrefixResolver.resolve(topicPrefix, productKey, deviceKey);
        if (topic.startsWith(ownPrefix)) {
            return allowOwnNamespace(ownPrefix, action, topic);
        }
        // 主题落在自有命名空间之外：可能是网关代子设备收发（T-26 §5.3），交由委托分支判定
        return allowDelegated(productKey, deviceKey, topicPrefix, action, topic);
    }

    /**
     * 自有命名空间的收发语义（T-25 既有规则，T-26 复用同一套）：发布不得进入 {@code cmd/}，
     * 订阅仅限 {@code cmd/}。委托分支亦按同一语义放行，避免代理链路出现第二套例外规则。
     */
    private boolean allowOwnNamespace(String prefix, String action, String topic) {
        if (ACTION_PUBLISH.equals(action)) {
            return !topic.startsWith(prefix + "cmd/");
        }
        return ACTION_SUBSCRIBE.equals(action) && topic.startsWith(prefix + "cmd/");
    }

    /**
     * 网关 / 子网关代理判定（T-26 §5.3，T-47 多级拓扑）：仅在「主题中的目标设备确为当前设备的
     * 直接子设备」时按委托语义放行。
     * <p>
     * 目标设备键由产品模板反解得到（多段模板下段位随占位符走，不写死下标），再以
     * {@code (productKey, targetDeviceKey)} 复用同一份 {@code AuthMeta} 取拓扑：
     * 当前设备须为 {@code GATEWAY} 或已挂载的 {@code SUB}（子网关），目标须为未禁用的
     * {@code SUB}，且其 {@code parentId} 恰为当前设备。三者任一不成立即拒绝 ——
     * 非本人子设备、已解绑、任一侧被禁用都落在拒绝侧。多级链路逐级委托，不做跨级直连。
     * 委托方自身的准入（产品启用、设备启用）已由调用方先行判定，此处不重复。
     */
    private boolean allowDelegated(String productKey, String deviceKey, String topicPrefix,
                                   String action, String topic) {
        TopicPrefixResolver.TargetDevice target =
                topicPrefixResolver.resolveTarget(topicPrefix, productKey, topic);
        if (target == null || target.deviceKey().equals(deviceKey)) {
            return false;
        }
        DeviceAuthCacheService.AuthMeta gatewayMeta = deviceAccessGuard.resolve(productKey, deviceKey);
        if (gatewayMeta == null || gatewayMeta.deviceId() == null
                || (!DeviceNodeType.GATEWAY.equals(gatewayMeta.nodeType())
                    && !DeviceNodeType.SUB.equals(gatewayMeta.nodeType()))) {
            return false;
        }
        DeviceAuthCacheService.AuthMeta targetMeta =
                deviceAccessGuard.resolve(productKey, target.deviceKey());
        if (targetMeta == null || !DeviceNodeType.SUB.equals(targetMeta.nodeType())
                || !deviceAccessGuard.isPermitted(targetMeta)
                || !gatewayMeta.deviceId().equals(targetMeta.parentId())) {
            return false;
        }
        String targetPrefix = topicPrefixResolver.resolve(topicPrefix, productKey, target.deviceKey());
        return allowOwnNamespace(targetPrefix, action, topic);
    }

    /**
     * 前缀与主题按层级逐段比较，前缀中的 {@code +} 匹配主题的任意单层。
     * <p>
     * 订阅侧主题由同一模板解析而来，设备位本身即 {@code +}；发布侧主题是设备实发主题，
     * 设备位为具体设备键。把设备位统一视作通配，两种动作才能共用同一套前缀集合。
     */
    private boolean prefixMatches(String prefix, String topic) {
        String[] required = prefix.split("/");
        String[] actual = topic.split("/");
        if (actual.length < required.length) {
            return false;
        }
        for (int i = 0; i < required.length; i++) {
            if (SUBSCRIPTION_WILDCARD.equals(required[i])) {
                continue;
            }
            if (!required[i].equals(actual[i])) {
                return false;
            }
        }
        return true;
    }
}
