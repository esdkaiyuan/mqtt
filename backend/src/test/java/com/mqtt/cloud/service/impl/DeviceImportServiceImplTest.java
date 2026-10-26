package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DeviceImportProperties;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.dto.response.DeviceImportResultDTO;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link DeviceImportServiceImpl} 单元测试（T-53）。
 */
class DeviceImportServiceImplTest {

    private DeviceService deviceService;
    private ProductService productService;
    private DeviceImportServiceImpl service;

    @BeforeEach
    void setUp() {
        deviceService = mock(DeviceService.class);
        productService = mock(ProductService.class);
        service = new DeviceImportServiceImpl(deviceService, productService, new DeviceImportProperties());
    }

    @Test
    void import_should_createDevice_withDefaultsAndCredential() {
        Product product = product(7L, "p1");
        given(productService.getByProductKey("p1")).willReturn(product);
        given(deviceService.createDevice(anyLong(), any())).willAnswer(invocation -> {
            CreateDeviceDTO dto = invocation.getArgument(1);
            DeviceCreatedDTO created = new DeviceCreatedDTO();
            created.setId(1L);
            created.setDeviceKey(dto.getDeviceKey());
            created.setProductKey("p1");
            created.setUsername("p1." + dto.getDeviceKey());
            created.setDeviceSecret("secret-1");
            return created;
        });

        byte[] csv = ("deviceKey,deviceName,productKey,deviceType,topic,description\n"
                + "dev-1,设备一,p1,,,\n").getBytes(StandardCharsets.UTF_8);
        DeviceImportResultDTO result = service.importCsv(9L, csv, "devices.csv");

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getSucceededCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getCredentials()).hasSize(1);
        assertThat(result.getCredentials().get(0).getUsername()).isEqualTo("p1.dev-1");
        assertThat(result.getCredentials().get(0).getSecret()).isEqualTo("secret-1");

        ArgumentCaptor<CreateDeviceDTO> captor = ArgumentCaptor.forClass(CreateDeviceDTO.class);
        verify(deviceService).createDevice(eq(9L), captor.capture());
        CreateDeviceDTO dto = captor.getValue();
        assertThat(dto.getProductId()).isEqualTo(7L);
        assertThat(dto.getDeviceName()).isEqualTo("设备一");
        assertThat(dto.getDeviceType()).isEqualTo("sensor");
        assertThat(dto.getTopic()).isEqualTo("device/dev-1/data");
    }

    @Test
    void import_should_failRow_whenProductMissing() {
        given(productService.getByProductKey("missing")).willReturn(null);

        byte[] csv = ("deviceKey,deviceName,productKey\n"
                + "dev-1,设备一,missing\n").getBytes(StandardCharsets.UTF_8);
        DeviceImportResultDTO result = service.importCsv(9L, csv, "devices.csv");

        assertThat(result.getSucceededCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailedItems().get(0).getRowNumber()).isEqualTo(2);
        assertThat(result.getFailedItems().get(0).getReason()).contains("产品不存在");
    }

    @Test
    void import_should_isolateRowFailure_andKeepOtherRows() {
        given(productService.getByProductKey("p1")).willReturn(product(7L, "p1"));
        given(deviceService.createDevice(anyLong(), any())).willAnswer(invocation -> {
            CreateDeviceDTO dto = invocation.getArgument(1);
            if ("dup".equals(dto.getDeviceKey())) {
                throw new BusinessException(com.mqtt.cloud.common.ResultCode.DEVICE_KEY_EXISTS);
            }
            DeviceCreatedDTO created = new DeviceCreatedDTO();
            created.setDeviceKey(dto.getDeviceKey());
            created.setUsername("p1." + dto.getDeviceKey());
            created.setDeviceSecret("s");
            return created;
        });

        byte[] csv = ("deviceKey,deviceName,productKey\n"
                + "dup,重复,p1\n"
                + "ok,正常,p1\n").getBytes(StandardCharsets.UTF_8);
        DeviceImportResultDTO result = service.importCsv(9L, csv, "devices.csv");

        assertThat(result.getSucceededCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getCredentials()).extracting(DeviceImportResultDTO.Credential::getDeviceKey)
                .containsExactly("ok");
    }

    @Test
    void import_should_rejectMissingRequiredHeader() {
        byte[] csv = "deviceKey,deviceName\ndev-1,设备一\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.importCsv(9L, csv, "devices.csv"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6316))
                .hasMessageContaining("productkey");
    }

    @Test
    void import_should_rejectFileOverSizeLimit() {
        DeviceImportProperties properties = new DeviceImportProperties();
        properties.setMaxFileBytes(4);
        DeviceImportServiceImpl small = new DeviceImportServiceImpl(deviceService, productService, properties);

        assertThatThrownBy(() -> small.importCsv(9L, "12345".getBytes(StandardCharsets.UTF_8), "x.csv"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(6316));
    }

    @Test
    void template_should_containAllColumns() {
        String content = new String(service.template(), StandardCharsets.UTF_8);

        assertThat(content).contains("deviceKey", "deviceName", "productKey",
                "deviceType", "topic", "description", "firmwareVersion");
    }

    private Product product(Long id, String productKey) {
        Product product = new Product();
        product.setId(id);
        product.setProductKey(productKey);
        product.setProductName(productKey);
        return product;
    }
}