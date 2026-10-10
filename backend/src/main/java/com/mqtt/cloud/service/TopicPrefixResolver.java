package com.mqtt.cloud.service;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 产品级主题前缀模板解析（T-25 §4.5）。
 * <p>
 * 模板只允许 {@code {productKey}} 与 {@code {deviceKey}} 两个占位符，解析分两处：
 * 授权回调以具体设备键代入得到归属前缀，共享订阅把设备占位符代入 {@code +} 得到订阅过滤器。
 * 之所以做成模板而非固定两段式，是为了在保留默认值 {@code device/{deviceKey}} 语义的前提下
 * 允许产品自定义层级，同时订阅侧只需替换设备占位符即可生成共享订阅。
 * <p>
 * 模板本体不得出现 MQTT 通配符与空白字符：前者会让订阅过滤器越权，后者在主题中无意义。
 */
@Component
public class TopicPrefixResolver {

    /** 与 T-11 建列时的默认值一致，空模板回落至此。 */
    public static final String DEFAULT_TOPIC_PREFIX = "device/{deviceKey}";

    private static final String PLACEHOLDER_PRODUCT_KEY = "{productKey}";
    private static final String PLACEHOLDER_DEVICE_KEY = "{deviceKey}";
    private static final String SUBSCRIPTION_WILDCARD = "+";
    private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of("productKey", "deviceKey");
    private static final String FORBIDDEN_LITERAL_CHARS = "+#";
    private static final int MAX_TEMPLATE_LENGTH = 128;

    /** 反解结果：目标设备键，以及其后剩余的消息类型（不含前导 {@code /}）。 */
    public record TargetDevice(String deviceKey, String messageType) {
    }

    /**
     * 解析为设备归属前缀，结果以单个 {@code /} 收尾，便于与消息类型直接拼接。
     */
    public String resolve(String template, String productKey, String deviceKey) {
        if (!StringUtils.hasText(deviceKey)) {
            throw invalid("设备标识为空，无法解析主题前缀模板");
        }
        return normalize(expand(template, productKey, deviceKey));
    }

    /**
     * 解析为共享订阅前缀，设备占位符代入 {@code +}，产品占位符代入产品键。
     */
    public String resolveForSubscription(String template, String productKey) {
        return normalize(expand(template, productKey, SUBSCRIPTION_WILDCARD));
    }

    /**
     * 校验模板本体并返回去空白后的可用模板，空模板回落 {@link #DEFAULT_TOPIC_PREFIX}。
     * <p>
     * 产品保存时设备键尚未确定，无法做完整解析，只能在此拦下非法占位符与非法字符，
     * 使模板注入在写入期即被拒，而不是等到设备认证或订阅时才暴露。
     */
    public String validateTemplate(String template) {
        String effective = StringUtils.hasText(template) ? template.trim() : DEFAULT_TOPIC_PREFIX;
        validate(effective);
        return effective;
    }

    /**
     * 从主题中反解目标设备键与消息类型，供网关代理判定使用（T-26 §5.3）。
     * <p>
     * 设备键在主题中的段位由模板里 {@code {deviceKey}} 占位符的位置决定，
     * 因此 {@code device/{deviceKey}} 与 {@code iot/{productKey}/{deviceKey}} 两种形状
     * 都能取到正确段位，不依赖固定下标；占位符之前的字面段与产品段必须逐段相符。
     * 主题不在模板形状内、或占位符之后已无消息类型时返回 {@code null}，由调用方按拒绝处理。
     */
    public TargetDevice resolveTarget(String template, String productKey, String topic) {
        if (!StringUtils.hasText(topic)) {
            return null;
        }
        if (!StringUtils.hasText(productKey)) {
            throw invalid("产品标识为空，无法反解主题目标设备");
        }
        String[] templateSegments = validateTemplate(template).split("/");
        String[] topicSegments = topic.split("/");
        int deviceIndex = -1;
        for (int i = 0; i < templateSegments.length; i++) {
            if (PLACEHOLDER_DEVICE_KEY.equals(templateSegments[i])) {
                deviceIndex = i;
                break;
            }
        }
        if (deviceIndex < 0 || topicSegments.length <= deviceIndex + 1) {
            return null;
        }
        for (int i = 0; i < deviceIndex; i++) {
            String expected = templateSegments[i].replace(PLACEHOLDER_PRODUCT_KEY, productKey);
            if (!expected.equals(topicSegments[i])) {
                return null;
            }
        }
        String deviceKey = topicSegments[deviceIndex];
        if (!StringUtils.hasText(deviceKey)) {
            return null;
        }
        StringBuilder messageType = new StringBuilder();
        for (int i = deviceIndex + 1; i < topicSegments.length; i++) {
            if (messageType.length() > 0) {
                messageType.append('/');
            }
            messageType.append(topicSegments[i]);
        }
        return new TargetDevice(deviceKey, messageType.toString());
    }

    private String expand(String template, String productKey, String deviceKey) {
        String effective = validateTemplate(template);
        if (!StringUtils.hasText(productKey)) {
            throw invalid("产品标识为空，无法解析主题前缀模板: " + effective);
        }
        return effective.replace(PLACEHOLDER_PRODUCT_KEY, productKey)
                .replace(PLACEHOLDER_DEVICE_KEY, deviceKey);
    }

    private void validate(String template) {
        if (template.length() > MAX_TEMPLATE_LENGTH) {
            throw invalid("主题前缀模板长度超出上限 " + MAX_TEMPLATE_LENGTH + ": " + template);
        }
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '{') {
                int close = template.indexOf('}', index);
                if (close < 0) {
                    throw invalid("主题前缀模板存在未闭合的占位符: " + template);
                }
                String name = template.substring(index + 1, close);
                if (!ALLOWED_PLACEHOLDERS.contains(name)) {
                    throw invalid("主题前缀模板含未知占位符: {" + name + "}");
                }
                index = close + 1;
                continue;
            }
            if (current == '}') {
                throw invalid("主题前缀模板存在多余的右花括号: " + template);
            }
            if (FORBIDDEN_LITERAL_CHARS.indexOf(current) >= 0
                    || Character.isWhitespace(current)
                    || Character.isISOControl(current)) {
                throw invalid("主题前缀模板含非法字符: " + current);
            }
            index++;
        }
    }

    private String normalize(String prefix) {
        String trimmed = prefix;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            throw invalid("主题前缀模板解析结果为空");
        }
        return trimmed + "/";
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResultCode.TOPIC_PREFIX_TEMPLATE_INVALID, message);
    }
}
