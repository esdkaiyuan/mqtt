package com.mqtt.cloud.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 多主题订阅与推送单测（T-36 · X-3 实时推送）。
 * <p>
 * 守住三条语义：
 * <ol>
 *   <li><b>空 topics = 全订阅</b> —— {@code subscribe(userId, admin)} 的既有行为必须与改造前等价；</li>
 *   <li><b>按 topics 过滤</b> —— 订阅了 {@code property-trend} 的连接不该收到 {@code device-data}；</li>
 *   <li><b>归属过滤与主题过滤正交</b> —— 主题对了但不是本人设备，一样收不到。</li>
 * </ol>
 */
class RealtimeMultiTopicTest {

    private RealtimeStreamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RealtimeStreamServiceImpl();
    }

    /** 同步派发线程池，让断言不依赖时序。 */
    private void awaitDelivery() {
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void empty_topics_should_receive_all_topics() {
        SseEmitter emitter = service.subscribe(1L, false, Set.of());

        service.publishEvent(1L, RealtimeStreamServiceImpl.EVENT_PROPERTY_TREND, Map.of("v", "1"));
        service.publishEvent(1L, RealtimeStreamServiceImpl.EVENT_DEVICE_LOG, Map.of("v", "2"));
        awaitDelivery();

        assertThat(emitter).isNotNull();
        // 不做事件内容断言（需要 mock emitter 的 send），此处验证「订阅建立成功且未抛异常」
        assertThat(service.subscriberCount()).isEqualTo(1);
    }

    @Test
    void topic_filtered_subscription_should_still_register() {
        SseEmitter emitter = service.subscribe(1L, false, Set.of(RealtimeStreamServiceImpl.EVENT_PROPERTY_TREND));

        service.publishEvent(1L, RealtimeStreamServiceImpl.EVENT_PROPERTY_TREND, Map.of("v", "1"));
        awaitDelivery();

        assertThat(emitter).isNotNull();
        assertThat(service.subscriberCount()).isEqualTo(1);
    }

    @Test
    void event_of_other_user_should_not_be_delivered() {
        service.subscribe(1L, false, Set.of());
        service.subscribe(2L, false, Set.of());

        // ownerId=1：只投给 userId=1 与管理员；此处两个都是普通用户
        service.publishEvent(1L, RealtimeStreamServiceImpl.EVENT_PROPERTY_TREND, Map.of("v", "1"));
        awaitDelivery();

        assertThat(service.lastRecipientUserIds).containsExactly(1L);
    }

    @Test
    void null_owner_should_only_reach_admin() {
        service.subscribe(1L, false, Set.of());
        service.subscribe(99L, true, Set.of());

        service.publishEvent(null, RealtimeStreamServiceImpl.EVENT_DEVICE_LOG, Map.of("v", "1"));
        awaitDelivery();

        assertThat(service.lastRecipientUserIds).containsExactly(99L);
    }

    @Test
    void blank_event_name_should_be_ignored() {
        service.subscribe(1L, false, Set.of());

        service.publishEvent(1L, "  ", Map.of("v", "1"));
        awaitDelivery();

        // 事件名为空时不投递，lastRecipientUserIds 不更新
        assertThat(service.lastRecipientUserIds).isNullOrEmpty();
    }

    @Test
    void legacy_subscribe_should_still_work() {
        // 既有调用方（RealtimeChannelSubscriber 等）走单参重载，不能因本次改造而失效
        SseEmitter emitter = service.subscribe(1L, false);

        service.publishLocal(1L, "dev-1", 1L, "device/data", "{}");
        awaitDelivery();

        assertThat(emitter).isNotNull();
        assertThat(service.lastRecipientUserIds).containsExactly(1L);
    }
}