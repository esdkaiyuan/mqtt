package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.NotificationDelivery;
import com.mqtt.cloud.mapper.NotificationDeliveryMapper;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * 投递记录服务单测（T-27 实施计划 P3）。
 * <p>
 * 覆盖退避顺延与达上限停止两条重试锚点语义，以及落记录时成功不排重试、失败排一次重试。
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 10, 0);

    @Mock
    private NotificationDeliveryMapper mapper;

    private NotificationProperties properties;
    private NotificationDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        service = new NotificationDeliveryServiceImpl(properties);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void nextRetryAt_should_back_off_exponentially() {
        assertThat(NotificationDeliveryServiceImpl.nextRetryAt(1, 3, 60, NOW))
                .isEqualTo(NOW.plusSeconds(60));
        assertThat(NotificationDeliveryServiceImpl.nextRetryAt(2, 3, 60, NOW))
                .isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void nextRetryAt_should_stop_at_max_attempts() {
        assertThat(NotificationDeliveryServiceImpl.nextRetryAt(3, 3, 60, NOW)).isNull();
        assertThat(NotificationDeliveryServiceImpl.nextRetryAt(4, 3, 60, NOW)).isNull();
    }

    @Test
    void record_should_schedule_retry_on_failure() {
        NotificationDelivery delivery = service.record(message(), "EMAIL", 21L,
                ChannelSendResult.failure("ops@example.com", "smtp down"));

        assertThat(delivery.getStatus()).isEqualTo(NotificationConstants.DELIVERY_FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNotNull();
        assertThat(delivery.getErrorMessage()).isEqualTo("smtp down");
        verify(mapper).insert(any(NotificationDelivery.class));
    }

    @Test
    void record_should_not_schedule_retry_on_success() {
        NotificationDelivery delivery = service.record(message(), "INAPP", null,
                ChannelSendResult.success(null));

        assertThat(delivery.getStatus()).isEqualTo(NotificationConstants.DELIVERY_SUCCESS);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getErrorMessage()).isNull();
    }

    private NotificationMessage message() {
        NotificationMessage message = new NotificationMessage();
        message.setUserId(USER_ID);
        message.setEventType("alert.triggered");
        message.setTitle("温度过高");
        return message;
    }
}