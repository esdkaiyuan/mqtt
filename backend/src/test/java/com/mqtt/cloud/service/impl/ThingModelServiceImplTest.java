package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.ThingModelProperties;
import com.mqtt.cloud.dto.response.ThingModelResponse;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.ProductMapper;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 物模型读写服务单测（T-14 实施计划 P2）。
 * <p>
 * 覆盖版本自增 / 大小与校验拒绝 / 缓存写入与失效 / 清空幂等 / 导出前置条件。
 */
@ExtendWith(MockitoExtension.class)
class ThingModelServiceImplTest {

    private static final String VALID_TSL = """
            {"schemaVersion":"1.0","properties":[
              {"identifier":"temperature","name":"环境温度",
               "dataType":{"type":"float","min":-40,"max":125,"unit":"℃"},"accessMode":"r"}],
             "events":[],"services":[]}
            """;

    private static final String INVALID_TSL = """
            {"schemaVersion":"2.0","properties":[],"events":[],"services":[]}
            """;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ThingModelCache cache;

    private ThingModelProperties properties;

    private ThingModelServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new ThingModelProperties();
        service = new ThingModelServiceImpl(productMapper, cache, properties);
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setProductKey("esp32-fall");
        return product;
    }

    // ---------- save ----------

    @Test
    void save_should_increment_version_and_refresh_timestamp() {
        Product existing = product(1L);
        existing.setThingModelVersion(4);
        when(productMapper.selectById(1L)).thenReturn(existing);

        ThingModelResponse response = service.save(1L, VALID_TSL);

        assertThat(response.getThingModel()).isEqualTo(VALID_TSL);
        assertThat(response.getVersion()).isEqualTo(5);
        assertThat(response.getUpdatedAt()).isNotNull();
        assertThat(existing.getThingModelUpdatedAt()).isEqualTo(response.getUpdatedAt());
        verify(productMapper).updateById(existing);
    }

    @Test
    void save_should_treat_null_version_as_zero() {
        Product existing = product(1L);
        existing.setThingModelVersion(null);
        when(productMapper.selectById(1L)).thenReturn(existing);

        assertThat(service.save(1L, VALID_TSL).getVersion()).isEqualTo(1);
    }

    @Test
    void save_should_evict_cache() {
        when(productMapper.selectById(1L)).thenReturn(product(1L));

        service.save(1L, VALID_TSL);

        verify(cache).evict(1L);
    }

    @Test
    void save_should_reject_invalid_model_without_touching_db() {
        when(productMapper.selectById(1L)).thenReturn(product(1L));

        assertThatThrownBy(() -> service.save(1L, INVALID_TSL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.THING_MODEL_INVALID.getCode());

        verify(productMapper, never()).updateById(any(Product.class));
        verifyNoInteractions(cache);
    }

    @Test
    void save_should_report_parse_failure_separately() {
        when(productMapper.selectById(1L)).thenReturn(product(1L));

        assertThatThrownBy(() -> service.save(1L, "{not json"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.THING_MODEL_PARSE_FAILED.getCode());
    }

    @Test
    void save_should_reject_oversized_model() {
        properties.setMaxSizeBytes(32);
        when(productMapper.selectById(1L)).thenReturn(product(1L));

        assertThatThrownBy(() -> service.save(1L, VALID_TSL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.THING_MODEL_TOO_LARGE.getCode());

        verify(productMapper, never()).updateById(any(Product.class));
    }

    @Test
    void save_should_fail_when_product_missing() {
        when(productMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.save(9L, VALID_TSL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    // ---------- clear ----------

    @Test
    void clear_should_be_idempotent_when_already_empty() {
        Product existing = product(1L);
        existing.setThingModel(null);
        existing.setThingModelVersion(3);
        when(productMapper.selectById(1L)).thenReturn(existing);

        ThingModelResponse response = service.clear(1L);

        assertThat(response.getThingModel()).isNull();
        assertThat(response.getVersion()).isEqualTo(3);
        verify(productMapper, never()).updateById(any(Product.class));
        verifyNoInteractions(cache);
    }

    @Test
    void clear_should_bump_version_and_evict_when_model_present() {
        Product existing = product(1L);
        existing.setThingModel(VALID_TSL);
        existing.setThingModelVersion(2);
        when(productMapper.selectById(1L)).thenReturn(existing);

        ThingModelResponse response = service.clear(1L);

        assertThat(response.getThingModel()).isNull();
        assertThat(response.getVersion()).isEqualTo(3);
        assertThat(response.getUpdatedAt()).isNotNull();
        verify(productMapper).updateById(existing);
        verify(cache).evict(1L);
    }

    // ---------- export ----------

    @Test
    void export_should_fail_when_model_absent() {
        Product existing = product(1L);
        existing.setThingModel(null);
        when(productMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> service.export(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void export_should_embed_product_key_and_version_in_file_name() {
        Product existing = product(1L);
        existing.setThingModel(VALID_TSL);
        existing.setThingModelVersion(7);
        when(productMapper.selectById(1L)).thenReturn(existing);

        ThingModelService.ThingModelExport export = service.export(1L);

        assertThat(export.fileName()).isEqualTo("thing-model-esp32-fall-v7.json");
        assertThat(export.content()).isEqualTo(VALID_TSL);
    }

    // ---------- getForProduct ----------

    @Test
    void getForProduct_should_return_empty_when_interpret_disabled() {
        properties.setInterpretEnabled(false);

        assertThat(service.getForProduct(1L)).isEqualTo(ThingModelDefinition.EMPTY);
        verifyNoInteractions(cache);
    }

    @Test
    void getForProduct_should_return_cached_definition_without_querying_db() {
        ThingModelDefinition cached = ThingModelValidator.parseDefinition(VALID_TSL, 6);
        when(cache.get(1L)).thenReturn(cached);

        assertThat(service.getForProduct(1L)).isSameAs(cached);
        verify(productMapper, never()).selectById(any());
    }

    @Test
    void getForProduct_should_load_from_db_and_cache_on_miss() {
        Product existing = product(1L);
        existing.setThingModel(VALID_TSL);
        existing.setThingModelVersion(2);
        when(productMapper.selectById(1L)).thenReturn(existing);

        ThingModelDefinition definition = service.getForProduct(1L);

        assertThat(definition.version()).isEqualTo(2);
        assertThat(definition.properties()).containsOnlyKeys("temperature");
        verify(cache).put(1L, definition);
    }

    @Test
    void getForProduct_should_cache_empty_when_product_missing() {
        when(productMapper.selectById(9L)).thenReturn(null);

        assertThat(service.getForProduct(9L)).isEqualTo(ThingModelDefinition.EMPTY);
        verify(cache).put(9L, ThingModelDefinition.EMPTY);
    }
}