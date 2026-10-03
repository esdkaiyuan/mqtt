package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.OtaProperties;
import com.mqtt.cloud.dto.response.OtaFirmwareVO;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.OtaFirmwareMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.OtaFirmwareService;
import com.mqtt.cloud.service.ProductService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OTA 固件包服务单测（T-22 实施计划 P6）。
 * <p>
 * 覆盖上传落盘与 MD5 一致性、版本 / 文件 / 大小三类参数校验（{@code 6232}）、产品不存在（{@code 6002}）、
 * 同产品版本重复（{@code 6233}，含唯一键竞态兜底与落盘清理）、落盘失败（{@code 6235}）、
 * 被引用禁止删除（{@code 6234}）、归属校验（{@code 6231}）以及下载句柄装配。
 */
class OtaFirmwareServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 3L;
    private static final Long FIRMWARE_ID = 1L;
    private static final String VERSION = "1.0.0";
    private static final String FILE_NAME = "fw.bin";

    @TempDir
    Path tempDir;

    private OtaFirmwareMapper firmwareMapper;
    private OtaUpgradeTaskMapper taskMapper;
    private ProductService productService;
    private OtaProperties properties;
    private OtaFirmwareServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OtaFirmware.class);
        firmwareMapper = mock(OtaFirmwareMapper.class);
        taskMapper = mock(OtaUpgradeTaskMapper.class);
        productService = mock(ProductService.class);
        properties = new OtaProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setPublicBaseUrl("http://localhost/firmware");
        service = new OtaFirmwareServiceImpl(firmwareMapper, taskMapper, productService, properties);
    }

    @Test
    void upload_should_persist_file_and_return_md5() throws Exception {
        byte[] content = "firmware-binary".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", FILE_NAME, "application/octet-stream", content);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(firmwareMapper.selectCount(any())).thenReturn(0L);
        when(firmwareMapper.insert(any(OtaFirmware.class))).thenReturn(1);

        OtaFirmwareVO vo = service.upload(USER_ID, PRODUCT_ID, VERSION, "首个版本", file);

        assertThat(vo.getVersion()).isEqualTo(VERSION);
        assertThat(vo.getFileName()).isEqualTo(FILE_NAME);
        assertThat(vo.getFileSize()).isEqualTo((long) content.length);
        assertThat(vo.getMd5()).isEqualTo(md5Hex(content));
        assertThat(vo.getProductName()).isEqualTo("温湿度传感器");
        assertThat(vo.getDownloadUrl()).isEqualTo("http://localhost/firmware/3/1.0.0/fw.bin");
        assertThat(storedFile()).exists().hasBinaryContent(content);
        verify(firmwareMapper).insert(any(OtaFirmware.class));
    }

    @Test
    void upload_should_strip_traversal_from_file_name() {
        MockMultipartFile file = new MockMultipartFile("file", "C:\\firm\\..\\fw.bin",
                "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8));
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(firmwareMapper.selectCount(any())).thenReturn(0L);
        when(firmwareMapper.insert(any(OtaFirmware.class))).thenReturn(1);

        OtaFirmwareVO vo = service.upload(USER_ID, PRODUCT_ID, VERSION, null, file);

        assertThat(vo.getFileName()).isEqualTo(FILE_NAME);
        assertThat(storedFile()).exists();
    }

    @Test
    void upload_should_reject_when_disabled() {
        properties.setEnabled(false);

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null, file()), ResultCode.OTA_FIRMWARE_INVALID);
    }

    @Test
    void upload_should_reject_blank_product() {
        assertCode(() -> service.upload(USER_ID, null, VERSION, null, file()), ResultCode.OTA_FIRMWARE_INVALID);
    }

    @Test
    void upload_should_reject_when_product_missing() {
        when(productService.requireById(PRODUCT_ID)).thenThrow(new BusinessException(ResultCode.PRODUCT_NOT_FOUND));

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null, file()), ResultCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void upload_should_reject_invalid_version() {
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, "bad version", null, file()),
                ResultCode.OTA_FIRMWARE_INVALID);
        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, null, null, file()),
                ResultCode.OTA_FIRMWARE_INVALID);
    }

    @Test
    void upload_should_reject_empty_file() {
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null,
                new MockMultipartFile("file", FILE_NAME, null, new byte[0])), ResultCode.OTA_FIRMWARE_INVALID);
        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null, null), ResultCode.OTA_FIRMWARE_INVALID);
    }

    @Test
    void upload_should_reject_oversize_file() {
        properties.setMaxFileSize(4L);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null,
                new MockMultipartFile("file", FILE_NAME, null, "too-large".getBytes(StandardCharsets.UTF_8))),
                ResultCode.OTA_FIRMWARE_INVALID);
    }

    @Test
    void upload_should_reject_existing_product_version() {
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(firmwareMapper.selectCount(any())).thenReturn(1L);

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null, file()),
                ResultCode.OTA_FIRMWARE_DUPLICATE);
        verify(firmwareMapper, never()).insert(any(OtaFirmware.class));
    }

    @Test
    void upload_should_cleanup_and_throw_duplicate_on_key_conflict() {
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(firmwareMapper.selectCount(any())).thenReturn(0L);
        when(firmwareMapper.insert(any(OtaFirmware.class))).thenThrow(new DuplicateKeyException("uk_product_version"));

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null, file()),
                ResultCode.OTA_FIRMWARE_DUPLICATE);
        assertThat(storedFile()).doesNotExist();
    }

    @Test
    void upload_should_throw_upload_failed_when_storage_unwritable() throws Exception {
        Path blocker = tempDir.resolve("blocker");
        Files.write(blocker, "x".getBytes(StandardCharsets.UTF_8));
        properties.setStorageDir(blocker.toString());
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(firmwareMapper.selectCount(any())).thenReturn(0L);

        assertCode(() -> service.upload(USER_ID, PRODUCT_ID, VERSION, null, file()), ResultCode.OTA_UPLOAD_FAILED);
    }

    @Test
    void list_should_fill_product_name_and_download_url() {
        when(firmwareMapper.selectList(any())).thenReturn(List.of(firmware(FIRMWARE_ID, USER_ID)));
        when(productService.list()).thenReturn(List.of(product()));

        List<OtaFirmwareVO> result = service.list(USER_ID, PRODUCT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("温湿度传感器");
        assertThat(result.get(0).getDownloadUrl()).isEqualTo("http://localhost/firmware/3/1.0.0/fw.bin");
    }

    @Test
    void detail_should_throw_when_missing_or_not_owned() {
        assertCode(() -> service.detail(USER_ID, null), ResultCode.OTA_FIRMWARE_NOT_FOUND);

        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(null);
        assertCode(() -> service.detail(USER_ID, FIRMWARE_ID), ResultCode.OTA_FIRMWARE_NOT_FOUND);

        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, 999L));
        assertCode(() -> service.detail(USER_ID, FIRMWARE_ID), ResultCode.OTA_FIRMWARE_NOT_FOUND);
    }

    @Test
    void remove_should_throw_when_referenced_by_task() {
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID));
        when(taskMapper.selectCount(any())).thenReturn(1L);

        assertCode(() -> service.remove(USER_ID, FIRMWARE_ID), ResultCode.OTA_FIRMWARE_IN_USE);
        verify(firmwareMapper, never()).deleteById(anyLong());
    }

    @Test
    void remove_should_physically_delete_when_not_referenced() {
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID));
        when(taskMapper.selectCount(any())).thenReturn(0L);

        service.remove(USER_ID, FIRMWARE_ID);

        verify(firmwareMapper).deleteById(FIRMWARE_ID);
    }

    @Test
    void remove_should_throw_when_missing() {
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(null);

        assertCode(() -> service.remove(USER_ID, FIRMWARE_ID), ResultCode.OTA_FIRMWARE_NOT_FOUND);
    }

    @Test
    void download_should_return_absolute_path() {
        when(firmwareMapper.selectById(FIRMWARE_ID)).thenReturn(firmware(FIRMWARE_ID, USER_ID));

        OtaFirmwareService.Download download = service.download(USER_ID, FIRMWARE_ID);

        assertThat(download.fileName()).isEqualTo(FILE_NAME);
        assertThat(download.filePath()).isEqualTo(storedFile().toString());
        assertThat(download.fileSize()).isEqualTo(1024L);
    }

    // ---------- 辅助 ----------

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", FILE_NAME, "application/octet-stream",
                "firmware-binary".getBytes(StandardCharsets.UTF_8));
    }

    private Path storedFile() {
        return tempDir.resolve(PRODUCT_ID + "/" + VERSION + "/" + FILE_NAME);
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProductName("温湿度传感器");
        return product;
    }

    private OtaFirmware firmware(Long id, Long userId) {
        OtaFirmware firmware = new OtaFirmware();
        firmware.setId(id);
        firmware.setUserId(userId);
        firmware.setProductId(PRODUCT_ID);
        firmware.setVersion(VERSION);
        firmware.setFileName(FILE_NAME);
        firmware.setFilePath(PRODUCT_ID + "/" + VERSION + "/" + FILE_NAME);
        firmware.setFileSize(1024L);
        firmware.setMd5("d41d8cd98f00b204e9800998ecf8427e");
        return firmware;
    }

    private String md5Hex(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(content);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
