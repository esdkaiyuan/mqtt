package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.response.OtaFirmwareVO;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.OtaFirmwareService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OTA 固件包控制台接口单测（T-22 实施计划 P6）。
 * <p>
 * 聚焦「当前用户透传 + 参数下传 + 结果封装 + 下载响应头」；鉴权注解与 HTTP 状态码由
 * {@code SecurityConfig} 与全局异常处理器保证，不在本层断言。
 */
@ExtendWith(MockitoExtension.class)
class OtaFirmwareControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 3L;
    private static final Long FIRMWARE_ID = 1L;
    private static final String VERSION = "1.0.0";

    @Mock
    private OtaFirmwareService otaFirmwareService;

    @InjectMocks
    private OtaFirmwareController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upload_should_delegate_with_current_user() {
        authenticate();
        MockMultipartFile file = new MockMultipartFile("file", "fw.bin",
                "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8));
        OtaFirmwareVO vo = new OtaFirmwareVO();
        vo.setId(FIRMWARE_ID);
        when(otaFirmwareService.upload(USER_ID, PRODUCT_ID, VERSION, "首个版本", file)).thenReturn(vo);

        Result<OtaFirmwareVO> result = controller.upload(PRODUCT_ID, VERSION, "首个版本", file);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(vo);
    }

    @Test
    void list_should_delegate_with_product_filter() {
        authenticate();
        OtaFirmwareVO vo = new OtaFirmwareVO();
        when(otaFirmwareService.list(USER_ID, PRODUCT_ID)).thenReturn(List.of(vo));

        assertThat(controller.list(PRODUCT_ID).getData()).containsExactly(vo);
    }

    @Test
    void detail_should_delegate() {
        authenticate();
        OtaFirmwareVO vo = new OtaFirmwareVO();
        when(otaFirmwareService.detail(USER_ID, FIRMWARE_ID)).thenReturn(vo);

        assertThat(controller.detail(FIRMWARE_ID).getData()).isSameAs(vo);
    }

    @Test
    void download_should_stream_attachment_headers() {
        authenticate();
        when(otaFirmwareService.download(USER_ID, FIRMWARE_ID))
                .thenReturn(new OtaFirmwareService.Download("fw.bin", "D:/tmp/ota/3/1.0.0/fw.bin", 1024L));

        ResponseEntity<Resource> response = controller.download(FIRMWARE_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(1024L);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("attachment").contains("fw.bin");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void remove_should_delegate() {
        authenticate();

        controller.remove(FIRMWARE_ID);

        verify(otaFirmwareService).remove(USER_ID, FIRMWARE_ID);
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user-" + USER_ID, "OPERATOR");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}