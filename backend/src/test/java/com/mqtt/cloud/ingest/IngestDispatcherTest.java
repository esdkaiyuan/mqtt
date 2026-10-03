package com.mqtt.cloud.ingest;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.WebhookConfig;
import com.mqtt.cloud.service.CommandReplyService;
import com.mqtt.cloud.service.OtaProgressService;
import com.mqtt.cloud.service.RealtimeBroadcaster;
import com.mqtt.cloud.service.ShadowDeliveryService;
import com.mqtt.cloud.service.ThingModelInterpretService;
import com.mqtt.cloud.service.WebhookConfigService;
import com.mqtt.cloud.service.WebhookDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 落库后下游分发单测（T-22 实施计划 P6，扩展自既有分发链路）。
 * <p>
 * 覆盖六路 fan-out 的调用与隔离：Webhook、实时推送、物模型解析、命令回执、影子补发，
 * 以及 T-22 新增的 OTA 进度回传（第六路）；并校验 {@code ota} → {@code device.ota} 事件类型映射。
 * 任一路异常都不得冒泡（否则 worker 整批重试导致消息重复落库）。
 */
class IngestDispatcherTest {

    private static final Long USER_ID = 10L;
    private static final Long DEVICE_ID = 101L;
    private static final Long OTHER_DEVICE_ID = 202L;
    private static final String DEVICE_KEY = "dev-1";

    private WebhookDispatcher webhookDispatcher;
    private WebhookConfigService webhookConfigService;
    private RealtimeBroadcaster realtimeBroadcaster;
    private ThingModelInterpretService thingModelInterpretService;
    private CommandReplyService commandReplyService;
    private ShadowDeliveryService shadowDeliveryService;
    private OtaProgressService otaProgressService;
    private IngestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WebhookConfig.class);
        webhookDispatcher = mock(WebhookDispatcher.class);
        webhookConfigService = mock(WebhookConfigService.class);
        realtimeBroadcaster = mock(RealtimeBroadcaster.class);
        thingModelInterpretService = mock(ThingModelInterpretService.class);
        commandReplyService = mock(CommandReplyService.class);
        shadowDeliveryService = mock(ShadowDeliveryService.class);
        otaProgressService = mock(OtaProgressService.class);
        dispatcher = new IngestDispatcher(webhookDispatcher, webhookConfigService, realtimeBroadcaster,
                thingModelInterpretService, commandReplyService, shadowDeliveryService, otaProgressService);
    }

    @Test
    void dispatch_should_noop_on_empty_events() {
        dispatcher.dispatch(List.of());

        verifyNoInteractions(webhookDispatcher, webhookConfigService, realtimeBroadcaster,
                thingModelInterpretService, commandReplyService, shadowDeliveryService, otaProgressService);
    }

    @Test
    void dispatch_should_fan_out_all_six_routes() {
        when(webhookConfigService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        List<ResolvedEvent> events = List.of(event(device(DEVICE_ID, USER_ID), "data", "{}"));

        dispatcher.dispatch(events);

        verify(webhookDispatcher).dispatch(anyList(), anyString(), any(Device.class), anyString());
        verify(realtimeBroadcaster).broadcast(any(Device.class), anyString(), anyString());
        verify(thingModelInterpretService).interpret(events);
        verify(commandReplyService).handle(events);
        verify(shadowDeliveryService).onIngest(events);
        verify(otaProgressService).handle(events);
    }

    @Test
    void dispatch_should_map_event_types_including_ota() {
        when(webhookConfigService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        List<ResolvedEvent> events = List.of(
                event(device(DEVICE_ID, USER_ID), "heartbeat", "{}"),
                event(device(DEVICE_ID, USER_ID), "lwt", "{}"),
                event(device(DEVICE_ID, USER_ID), "ota", "{\"status\":\"success\"}"),
                event(device(DEVICE_ID, USER_ID), "data", "{}"));

        dispatcher.dispatch(events);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(webhookDispatcher, times(4)).dispatch(anyList(), captor.capture(), any(Device.class), anyString());
        assertThat(captor.getAllValues())
                .containsExactly("device.heartbeat", "device.lwt", "device.ota", "device.data");
    }

    @Test
    void dispatch_should_match_device_level_and_exact_webhooks() {
        WebhookConfig deviceLevel = webhook(null);
        WebhookConfig exact = webhook(DEVICE_ID);
        WebhookConfig otherDevice = webhook(OTHER_DEVICE_ID);
        when(webhookConfigService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(deviceLevel, exact, otherDevice));

        dispatcher.dispatch(List.of(event(device(DEVICE_ID, USER_ID), "data", "{}")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WebhookConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(webhookDispatcher).dispatch(captor.capture(), anyString(), any(Device.class), anyString());
        assertThat(captor.getValue()).containsExactly(deviceLevel, exact);
    }

    @Test
    void dispatch_should_isolate_webhook_failure() {
        when(webhookConfigService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        List<ResolvedEvent> events = List.of(event(device(DEVICE_ID, USER_ID), "data", "{}"));
        org.mockito.Mockito.doThrow(new RuntimeException("webhook down"))
                .when(webhookDispatcher).dispatch(anyList(), anyString(), any(Device.class), anyString());

        assertThatCode(() -> dispatcher.dispatch(events)).doesNotThrowAnyException();
        verify(realtimeBroadcaster).broadcast(any(Device.class), anyString(), anyString());
        verify(otaProgressService).handle(events);
    }

    @Test
    void dispatch_should_isolate_ota_progress_failure() {
        when(webhookConfigService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        List<ResolvedEvent> events = List.of(event(device(DEVICE_ID, USER_ID), "ota", "{}"));
        org.mockito.Mockito.doThrow(new RuntimeException("ota down"))
                .when(otaProgressService).handle(events);

        assertThatCode(() -> dispatcher.dispatch(events)).doesNotThrowAnyException();
        verify(thingModelInterpretService).interpret(events);
    }

    @Test
    void dispatch_should_isolate_thing_model_failure() {
        when(webhookConfigService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        List<ResolvedEvent> events = List.of(event(device(DEVICE_ID, USER_ID), "data", "{}"));
        org.mockito.Mockito.doThrow(new RuntimeException("interpret down"))
                .when(thingModelInterpretService).interpret(events);

        assertThatCode(() -> dispatcher.dispatch(events)).doesNotThrowAnyException();
        verify(commandReplyService).handle(events);
        verify(shadowDeliveryService).onIngest(events);
        verify(otaProgressService).handle(events);
    }

    @Test
    void dispatch_should_skip_webhook_lookup_when_owner_missing() {
        dispatcher.dispatch(List.of(event(device(DEVICE_ID, null), "data", "{}")));

        verify(webhookConfigService, never()).list(any(LambdaQueryWrapper.class));
        verify(realtimeBroadcaster).broadcast(any(Device.class), anyString(), anyString());
    }

    private Device device(Long id, Long ownerId) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey(DEVICE_KEY);
        device.setOwnerId(ownerId);
        return device;
    }

    private WebhookConfig webhook(Long deviceId) {
        WebhookConfig webhook = new WebhookConfig();
        webhook.setId(1L);
        webhook.setUserId(USER_ID);
        webhook.setDeviceId(deviceId);
        webhook.setEvents("[\"device.data\"]");
        return webhook;
    }

    private ResolvedEvent event(Device device, String messageType, String payload) {
        IngestRecord record = new IngestRecord(device.getDeviceKey(),
                "device/" + device.getDeviceKey() + "/" + messageType, messageType, payload, 0, LocalDateTime.now());
        return new ResolvedEvent(device, record);
    }
}