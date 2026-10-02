package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.ThingModelRequest;
import com.mqtt.cloud.dto.response.ThingModelResponse;
import com.mqtt.cloud.dto.response.ThingModelValidationResponse;
import com.mqtt.cloud.service.ProductService;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.service.ThingModelValidationError;
import com.mqtt.cloud.service.ThingModelValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 物模型接口单测（T-14 实施计划 P3）。
 * <p>
 * 直接调用控制器方法，聚焦「入参透传 + 校验结果映射 + 导出附件头」；
 * 鉴权（{@code @PreAuthorize}）与 HTTP 状态码由 {@code SecurityConfig} 与全局异常处理器保证。
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerThingModelTest {

    private static final String VALID_TSL = """
            {"schemaVersion":"1.0","properties":[],"events":[],"services":[]}
            """;

    @Mock
    private ProductService productService;

    @Mock
    private ThingModelService thingModelService;

    @InjectMocks
    private ProductController controller;

    private ThingModelRequest request(String thingModel) {
        ThingModelRequest request = new ThingModelRequest();
        request.setThingModel(thingModel);
        return request;
    }

    private ThingModelResponse response(String thingModel, int version) {
        return new ThingModelResponse(thingModel, version, LocalDateTime.of(2026, 10, 2, 12, 0));
    }

    @Test
    void get_should_return_service_response() {
        when(thingModelService.get(1L)).thenReturn(response(VALID_TSL, 1));

        Result<ThingModelResponse> result = controller.getThingModel(1L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getVersion()).isEqualTo(1);
        assertThat(result.getData().getThingModel()).isEqualTo(VALID_TSL);
    }

    @Test
    void save_should_pass_thing_model_json_through() {
        when(thingModelService.save(1L, VALID_TSL)).thenReturn(response(VALID_TSL, 2));

        Result<ThingModelResponse> result = controller.saveThingModel(1L, request(VALID_TSL));

        assertThat(result.getData().getVersion()).isEqualTo(2);
    }

    @Test
    void save_should_propagate_business_exception() {
        when(thingModelService.save(1L, VALID_TSL))
                .thenThrow(new BusinessException(ResultCode.THING_MODEL_TOO_LARGE));

        assertThatThrownBy(() -> controller.saveThingModel(1L, request(VALID_TSL)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.THING_MODEL_TOO_LARGE.getCode());
    }

    @Test
    void clear_should_return_service_response() {
        when(thingModelService.clear(1L)).thenReturn(response(null, 3));

        Result<ThingModelResponse> result = controller.clearThingModel(1L);

        assertThat(result.getData().getThingModel()).isNull();
        assertThat(result.getData().getVersion()).isEqualTo(3);
    }

    @Test
    void validate_should_report_valid_model() {
        when(thingModelService.validate(1L, VALID_TSL)).thenReturn(ThingModelValidationResult.ok());

        Result<ThingModelValidationResponse> result = controller.validateThingModel(1L, request(VALID_TSL));

        assertThat(result.getData().isValid()).isTrue();
        assertThat(result.getData().getErrors()).isEmpty();
    }

    @Test
    void validate_should_map_all_error_paths() {
        ThingModelValidationResult invalid = ThingModelValidationResult.invalid(List.of(
                new ThingModelValidationError("properties[0].identifier", "identifier 不合法"),
                new ThingModelValidationError("events[0].type", "事件 type 非法")));
        when(thingModelService.validate(1L, VALID_TSL)).thenReturn(invalid);

        Result<ThingModelValidationResponse> result = controller.validateThingModel(1L, request(VALID_TSL));

        assertThat(result.getData().isValid()).isFalse();
        assertThat(result.getData().getErrors())
                .extracting(ThingModelValidationResponse.Error::getPath)
                .containsExactly("properties[0].identifier", "events[0].type");
    }

    @Test
    void validate_should_report_parse_failure_as_invalid() {
        when(thingModelService.validate(1L, "{bad"))
                .thenReturn(ThingModelValidationResult.parseFailed("JSON 解析失败"));

        Result<ThingModelValidationResponse> result = controller.validateThingModel(1L, request("{bad"));

        assertThat(result.getData().isValid()).isFalse();
        assertThat(result.getData().getErrors()).hasSize(1);
        assertThat(result.getData().getErrors().get(0).getPath()).isEqualTo("$");
    }

    @Test
    void export_should_return_attachment_with_file_name_and_body() {
        when(thingModelService.export(1L))
                .thenReturn(new ThingModelService.ThingModelExport(
                        "thing-model-esp32-fall-v7.json", VALID_TSL));

        ResponseEntity<byte[]> response = controller.exportThingModel(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("thing-model-esp32-fall-v7.json");
        assertThat(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(VALID_TSL);
    }

    @Test
    void import_should_delegate_to_import_model() {
        when(thingModelService.importModel(1L, VALID_TSL)).thenReturn(response(VALID_TSL, 4));

        Result<ThingModelResponse> result = controller.importThingModel(1L, request(VALID_TSL));

        assertThat(result.getData().getVersion()).isEqualTo(4);
    }
}