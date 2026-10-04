package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.exception.GlobalExceptionHandler;
import com.mqtt.cloud.service.HttpIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link HttpIngestController} 契约测试（T-24 设计文档 §10.1 / §13）。
 * <p>
 * 采用 Stand-alone {@link MockMvc}（不加载 Spring 上下文，规避 SecurityConfig 过滤器链），
 * 并显式装配 {@link GlobalExceptionHandler}，以覆盖「业务异常 → HTTP 状态码」的真实转换。
 */
@ExtendWith(MockitoExtension.class)
class HttpIngestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HttpIngestService httpIngestService;

    @BeforeEach
    void setUp() {
        HttpIngestController controller = new HttpIngestController(httpIngestService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("data 上报：200，返回体含 deviceKey/messageType/topic/accepted/receivedAt")
    void ingest_data_returnsAccepted() throws Exception {
        LocalDateTime receivedAt = LocalDateTime.now();
        given(httpIngestService.ingest("dev001", "data", "{\"a\":1}"))
                .willReturn(new HttpIngestService.IngestAccepted(
                        "dev001", "data", "device/dev001/data", receivedAt, true));

        mockMvc.perform(post("/ingest/dev001/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"a\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceKey").value("dev001"))
                .andExpect(jsonPath("$.data.messageType").value("data"))
                .andExpect(jsonPath("$.data.topic").value("device/dev001/data"))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.receivedAt").exists());

        verify(httpIngestService).ingest("dev001", "data", "{\"a\":1}");
    }

    @Test
    @DisplayName("空 body：200，并把 null 透传为空串")
    void ingest_emptyBody_passesEmptyString() throws Exception {
        given(httpIngestService.ingest("dev001", "heartbeat", ""))
                .willReturn(new HttpIngestService.IngestAccepted(
                        "dev001", "heartbeat", "device/dev001/heartbeat", LocalDateTime.now(), true));

        mockMvc.perform(post("/ingest/dev001/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.messageType").value("heartbeat"));

        verify(httpIngestService).ingest("dev001", "heartbeat", "");
    }

    @Test
    @DisplayName("非白名单 messageType：400 + code=6247")
    void ingest_unsupportedType_maps400And6247() throws Exception {
        given(httpIngestService.ingest("dev001", "foo", "x"))
                .willThrow(new BusinessException(ResultCode.INGEST_MESSAGE_TYPE_UNSUPPORTED));

        mockMvc.perform(post("/ingest/dev001/foo")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6247));
    }

    @Test
    @DisplayName("载荷超限：413 + code=6248")
    void ingest_payloadTooLarge_maps413And6248() throws Exception {
        given(httpIngestService.ingest("dev001", "data", "big"))
                .willThrow(new BusinessException(ResultCode.INGEST_PAYLOAD_TOO_LARGE));

        mockMvc.perform(post("/ingest/dev001/data")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("big"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(6248));
    }
}
