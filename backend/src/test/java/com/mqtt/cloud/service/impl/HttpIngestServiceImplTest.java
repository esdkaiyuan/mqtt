package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.HttpIngestProperties;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.service.HttpIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link HttpIngestServiceImpl} 单元测试（T-24 实施计划 P3）。
 * <p>
 * 关注点：
 * <ul>
 *     <li>白名单：{@code data} / {@code heartbeat} / {@code lwt} 通过，其它 {@code 400} + {@code 6247}；</li>
 *     <li><b>等价构造</b>：{@link IngestRecord} 六字段逐字段断言（合成 topic、QoS 映射、receivedAt 区间）；</li>
 *     <li>载荷超限：{@code 413} + {@code 6248} 且不投递；</li>
 *     <li>空载荷合法；</li>
 *     <li>{@code app.ingest.enabled=false}：丢弃 + WARN，不投递、不抛异常。</li>
 * </ul>
 */
class HttpIngestServiceImplTest {

    private IngestPipeline ingestPipeline;
    private IngestProperties ingestProperties;
    private HttpIngestProperties httpIngestProperties;
    private HttpIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        ingestPipeline = mock(IngestPipeline.class);
        ingestProperties = new IngestProperties();
        httpIngestProperties = new HttpIngestProperties();
        service = new HttpIngestServiceImpl(ingestPipeline, ingestProperties, httpIngestProperties);
    }

    /** 捕获唯一一次 {@code submit} 的入参；同时隐式断言「恰好调用一次」。 */
    private IngestRecord captureSubmitted() {
        ArgumentCaptor<IngestRecord> captor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(ingestPipeline, times(1)).submit(captor.capture());
        return captor.getValue();
    }

    @Test
    void ingest_data_should_build_equivalent_record_and_return_accepted() {
        LocalDateTime before = LocalDateTime.now();
        HttpIngestService.IngestAccepted accepted = service.ingest("dev001", "data", "{\"t\":25}");
        LocalDateTime after = LocalDateTime.now();

        IngestRecord record = captureSubmitted();
        assertThat(record.deviceKey()).isEqualTo("dev001");
        assertThat(record.topic()).isEqualTo("device/dev001/data");
        assertThat(record.messageType()).isEqualTo("data");
        assertThat(record.payload()).isEqualTo("{\"t\":25}");
        assertThat(record.qos()).isEqualTo(1);
        assertThat(record.receivedAt()).isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);

        assertThat(accepted.deviceKey()).isEqualTo("dev001");
        assertThat(accepted.messageType()).isEqualTo("data");
        assertThat(accepted.topic()).isEqualTo("device/dev001/data");
        assertThat(accepted.receivedAt()).isNotNull();
        assertThat(accepted.accepted()).isTrue();
    }

    @Test
    void ingest_heartbeat_should_map_qos_zero() {
        service.ingest("dev001", "heartbeat", "pong");

        IngestRecord record = captureSubmitted();
        assertThat(record.messageType()).isEqualTo("heartbeat");
        assertThat(record.topic()).isEqualTo("device/dev001/heartbeat");
        assertThat(record.qos()).isZero();
    }

    @Test
    void ingest_lwt_should_map_qos_one() {
        service.ingest("dev001", "lwt", "offline");

        IngestRecord record = captureSubmitted();
        assertThat(record.messageType()).isEqualTo("lwt");
        assertThat(record.topic()).isEqualTo("device/dev001/lwt");
        assertThat(record.qos()).isEqualTo(1);
    }

    @Test
    void ingest_unsupported_messageType_should_throw_6247_and_not_submit() {
        assertThatThrownBy(() -> service.ingest("dev001", "foo", "x"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6247));

        verifyNoInteractions(ingestPipeline);
    }

    @Test
    void ingest_payload_over_limit_should_throw_6248_and_not_submit() {
        httpIngestProperties.setMaxPayloadBytes(4);

        assertThatThrownBy(() -> service.ingest("dev001", "data", "12345"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6248));

        verifyNoInteractions(ingestPipeline);
    }

    @Test
    void ingest_payload_at_limit_should_pass() {
        httpIngestProperties.setMaxPayloadBytes(5);

        service.ingest("dev001", "data", "12345");

        assertThat(captureSubmitted().payload()).isEqualTo("12345");
    }

    @Test
    void ingest_empty_payload_should_be_valid() {
        service.ingest("dev001", "data", "");

        assertThat(captureSubmitted().payload()).isEmpty();
    }

    @Test
    void ingest_when_pipeline_disabled_should_drop_without_submit_or_throw() {
        ingestProperties.setEnabled(false);

        service.ingest("dev001", "data", "value");

        verifyNoInteractions(ingestPipeline);
    }
}
