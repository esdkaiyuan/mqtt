package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.dto.request.NotificationChannelConfigRequest;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.mapper.NotificationChannelConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知渠道配置服务单测（T-27 实施计划 P3）。
 * <p>
 * 覆盖设计 §7 校验矩阵（类型 / 配置 / 事件 / 重名 / 数量上限）、响应脱敏与
 * 「敏感字段留空保留原值」五类语义。
 */
@ExtendWith(MockitoExtension.class)
class NotificationChannelConfigServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long CHANNEL_ID = 21L;

    @Mock
    private NotificationChannelConfigMapper mapper;

    private NotificationProperties properties;
    private ObjectMapper objectMapper;
    private NotificationChannelConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        objectMapper = new ObjectMapper();
        service = new NotificationChannelConfigServiceImpl(properties, objectMapper);
        // LambdaQueryWrapper / LambdaUpdateWrapper 需要实体的 TableInfo；纯单测无 MyBatis 上下文
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NotificationChannelConfig.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void create_should_reject_unsupported_channel_type() {
        NotificationChannelConfigRequest request = emailRequest();
        request.setChannelType("WEBHOOK");

        assertCode(() -> service.create(USER_ID, request), ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED);
    }

    @Test
    void create_should_reject_email_without_host() {
        when(mapper.selectCount(any())).thenReturn(0L);
        NotificationChannelConfigRequest request = emailRequest();
        request.setConfig(Map.of("port", 587, "from", "ops@example.com"));

        assertCode(() -> service.create(USER_ID, request), ResultCode.NOTIFICATION_CHANNEL_INVALID);
    }

    @Test
    void create_should_reject_email_port_out_of_range() {
        when(mapper.selectCount(any())).thenReturn(0L);
        NotificationChannelConfigRequest request = emailRequest();
        request.setConfig(Map.of("host", "smtp.example.com", "port", 70000, "from", "ops@example.com"));

        assertCode(() -> service.create(USER_ID, request), ResultCode.NOTIFICATION_CHANNEL_INVALID);
    }

    @Test
    void create_should_reject_sms_method_outside_post_put() {
        when(mapper.selectCount(any())).thenReturn(0L);
        NotificationChannelConfigRequest request = new NotificationChannelConfigRequest();
        request.setChannelType("SMS");
        request.setName("值班短信");
        request.setEvents(List.of("alert.triggered"));
        request.setConfig(Map.of("url", "https://sms.example.com/send", "method", "GET"));

        assertCode(() -> service.create(USER_ID, request), ResultCode.NOTIFICATION_CHANNEL_INVALID);
    }

    @Test
    void create_should_reject_unknown_event() {
        when(mapper.selectCount(any())).thenReturn(0L);
        NotificationChannelConfigRequest request = emailRequest();
        request.setEvents(List.of("alert.exploded"));

        assertCode(() -> service.create(USER_ID, request), ResultCode.NOTIFICATION_CHANNEL_INVALID);
    }

    @Test
    void create_should_reject_duplicate_name() {
        // 第一次 count 是渠道数，第二次是重名判定
        when(mapper.selectCount(any())).thenReturn(0L, 1L);

        assertCode(() -> service.create(USER_ID, emailRequest()), ResultCode.NOTIFICATION_CHANNEL_INVALID);
    }

    @Test
    void create_should_reject_when_channel_count_reaches_limit() {
        when(mapper.selectCount(any())).thenReturn((long) properties.getMaxChannelsPerUser());

        assertCode(() -> service.create(USER_ID, emailRequest()), ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED);
    }

    @Test
    void create_should_mask_sensitive_fields_in_response() {
        when(mapper.selectCount(any())).thenReturn(0L);
        NotificationChannelConfigRequest request = emailRequest();
        request.setConfig(Map.of("host", "smtp.example.com", "port", 587,
                "from", "ops@example.com", "password", "s3cret"));

        NotificationChannelConfig created = service.create(USER_ID, request);

        assertThat(created.getConfig()).contains(NotificationConstants.MASK).doesNotContain("s3cret");
        verify(mapper).insert(any(NotificationChannelConfig.class));
    }

    @Test
    void update_should_keep_original_sensitive_value_when_blank() {
        NotificationChannelConfig existing = new NotificationChannelConfig();
        existing.setId(CHANNEL_ID);
        existing.setUserId(USER_ID);
        existing.setChannelType(NotificationConstants.CHANNEL_EMAIL);
        existing.setName("值班邮件");
        existing.setEvents("[\"alert.triggered\"]");
        existing.setConfig(objectMapper.writeValueAsString(Map.of("host", "smtp.example.com", "port", 587,
                "from", "ops@example.com", "password", "s3cret")));
        when(mapper.selectById(CHANNEL_ID)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);

        NotificationChannelConfigRequest request = emailRequest();
        request.setConfig(Map.of("host", "smtp.example.com", "port", 587,
                "from", "ops@example.com", "password", ""));

        service.update(USER_ID, CHANNEL_ID, request);

        ArgumentCaptor<LambdaUpdateWrapper<NotificationChannelConfig>> captor = updateCaptor();
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .anyMatch(value -> value != null && value.toString().contains("s3cret"));
    }

    @Test
    void listByUser_should_mask_sensitive_fields() {
        NotificationChannelConfig stored = new NotificationChannelConfig();
        stored.setId(CHANNEL_ID);
        stored.setUserId(USER_ID);
        stored.setChannelType(NotificationConstants.CHANNEL_EMAIL);
        stored.setConfig(objectMapper.writeValueAsString(Map.of("host", "smtp.example.com", "port", 587,
                "from", "ops@example.com", "password", "s3cret")));
        when(mapper.selectList(any())).thenReturn(List.of(stored));

        List<NotificationChannelConfig> result = service.listByUser(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfig()).contains(NotificationConstants.MASK).doesNotContain("s3cret");
    }

    private NotificationChannelConfigRequest emailRequest() {
        NotificationChannelConfigRequest request = new NotificationChannelConfigRequest();
        request.setChannelType(NotificationConstants.CHANNEL_EMAIL);
        request.setName("值班邮件");
        request.setEvents(List.of("alert.triggered"));
        request.setConfig(Map.of("host", "smtp.example.com", "port", 587, "from", "ops@example.com"));
        return request;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaUpdateWrapper<NotificationChannelConfig>> updateCaptor() {
        ArgumentCaptor<LambdaUpdateWrapper<NotificationChannelConfig>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        return captor;
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }
}