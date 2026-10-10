package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.entity.Notification;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内信控制台接口单测（T-27 实施计划 P2）。
 * <p>
 * 聚焦「当前用户透传 + 分页参数下传 + 结果封装」，鉴权与 HTTP 状态码由
 * {@code SecurityConfig} 与全局异常处理器保证。
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long NOTIFICATION_ID = 7L;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void page_should_delegate_with_current_user_and_filters() {
        authenticate();
        when(notificationService.page(eq(USER_ID), eq(true), eq(1), eq(20)))
                .thenReturn(new Page<>(1, 20));

        IPage<Notification> page = controller.page(true, 1, 20).getData();

        assertThat(page).isNotNull();
        verify(notificationService).page(USER_ID, true, 1, 20);
    }

    @Test
    void unreadCount_should_return_current_user_count() {
        authenticate();
        when(notificationService.unreadCount(USER_ID)).thenReturn(3L);

        assertThat(controller.unreadCount().getData()).isEqualTo(3L);
    }

    @Test
    void markRead_should_delegate() {
        authenticate();

        Result<Void> result = controller.markRead(NOTIFICATION_ID);

        assertThat(result.getCode()).isEqualTo(200);
        verify(notificationService).markRead(USER_ID, NOTIFICATION_ID);
    }

    @Test
    void markAllRead_should_delegate() {
        authenticate();

        controller.markAllRead();

        verify(notificationService).markAllRead(USER_ID);
    }

    @Test
    void deleteOne_should_delegate() {
        authenticate();

        controller.deleteOne(NOTIFICATION_ID);

        verify(notificationService).deleteOne(USER_ID, NOTIFICATION_ID);
    }

    @Test
    void clearRead_should_delegate() {
        authenticate();

        controller.clearRead();

        verify(notificationService).clearRead(USER_ID);
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}