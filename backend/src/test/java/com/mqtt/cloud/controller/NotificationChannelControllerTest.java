package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.dto.request.NotificationChannelConfigRequest;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.NotificationChannelConfigService;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知渠道配置接口单测（T-27 实施计划 P3）。
 * <p>
 * 覆盖：回滚开关关闭时端点整体返回 404；CRUD 的当前用户透传；测试发送的成功 / 跳过 /
 * 失败 / 类型不可用四条分支映射到设计业务码。
 */
@ExtendWith(MockitoExtension.class)
class NotificationChannelControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long CHANNEL_ID = 5L;
    private static final String EMAIL = "EMAIL";

    @Mock
    private NotificationChannelConfigService channelService;

    private NotificationChannel emailChannel;
    private NotificationProperties properties;
    private NotificationChannelController controller;

    @BeforeEach
    void setUp() {
        emailChannel = mock(NotificationChannel.class);
        when(emailChannel.channelType()).thenReturn(EMAIL);
        properties = new NotificationProperties();
        controller = new NotificationChannelController(channelService, properties, List.of(emailChannel));
        authenticate();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void endpoints_should_return_404_when_disabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> controller.list())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));
        verify(channelService, never()).listByUser(any());
    }

    @Test
    void list_should_delegate_with_current_user() {
        NotificationChannelConfig config = config();
        when(channelService.listByUser(USER_ID)).thenReturn(List.of(config));

        assertThat(controller.list().getData()).containsExactly(config);
        verify(channelService).listByUser(USER_ID);
    }

    @Test
    void create_should_delegate_with_current_user() {
        NotificationChannelConfigRequest request = new NotificationChannelConfigRequest();
        NotificationChannelConfig created = config();
        when(channelService.create(eq(USER_ID), eq(request))).thenReturn(created);

        assertThat(controller.create(request).getData()).isEqualTo(created);
        verify(channelService).create(USER_ID, request);
    }

    @Test
    void detail_should_delegate_with_current_user() {
        NotificationChannelConfig config = config();
        when(channelService.getOwned(USER_ID, CHANNEL_ID)).thenReturn(config);

        assertThat(controller.detail(CHANNEL_ID).getData()).isEqualTo(config);
        verify(channelService).getOwned(USER_ID, CHANNEL_ID);
    }

    @Test
    void update_should_delegate_with_current_user() {
        NotificationChannelConfigRequest request = new NotificationChannelConfigRequest();
        NotificationChannelConfig updated = config();
        when(channelService.update(eq(USER_ID), eq(CHANNEL_ID), eq(request))).thenReturn(updated);

        assertThat(controller.update(CHANNEL_ID, request).getData()).isEqualTo(updated);
        verify(channelService).update(USER_ID, CHANNEL_ID, request);
    }

    @Test
    void delete_should_delegate_with_current_user() {
        controller.delete(CHANNEL_ID);

        verify(channelService).delete(USER_ID, CHANNEL_ID);
    }

    @Test
    void test_should_succeed_when_channel_returns_success() {
        when(channelService.getOwned(USER_ID, CHANNEL_ID)).thenReturn(config());
        when(emailChannel.send(any())).thenReturn(ChannelSendResult.success("ops@example.com"));

        assertThat(controller.test(CHANNEL_ID).getData()).isEqualTo("发送成功");
    }

    @Test
    void test_should_fail_when_channel_skipped() {
        when(channelService.getOwned(USER_ID, CHANNEL_ID)).thenReturn(config());
        when(emailChannel.send(any())).thenReturn(ChannelSendResult.skipped());

        assertThatThrownBy(() -> controller.test(CHANNEL_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.NOTIFICATION_SEND_FAILED.getCode()));
    }

    @Test
    void test_should_fail_when_channel_returns_failure() {
        when(channelService.getOwned(USER_ID, CHANNEL_ID)).thenReturn(config());
        when(emailChannel.send(any())).thenReturn(ChannelSendResult.failure("ops@example.com", "smtp down"));

        assertThatThrownBy(() -> controller.test(CHANNEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("smtp down")
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.NOTIFICATION_SEND_FAILED.getCode()));
    }

    @Test
    void test_should_fail_when_channel_type_unsupported() {
        NotificationChannelConfig unsupported = config();
        unsupported.setChannelType("UNKNOWN");
        when(channelService.getOwned(USER_ID, CHANNEL_ID)).thenReturn(unsupported);

        assertThatThrownBy(() -> controller.test(CHANNEL_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED.getCode()));
    }

    private NotificationChannelConfig config() {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setId(CHANNEL_ID);
        config.setUserId(USER_ID);
        config.setChannelType(EMAIL);
        config.setName("运维邮件");
        return config;
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}