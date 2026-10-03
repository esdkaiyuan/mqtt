package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.OtaProperties;
import com.mqtt.cloud.dto.response.OtaFirmwareVO;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.entity.OtaUpgradeTask;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.OtaFirmwareMapper;
import com.mqtt.cloud.mapper.OtaUpgradeTaskMapper;
import com.mqtt.cloud.service.OtaFirmwareService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OTA 固件包服务实现（T-22 设计文档 §7.1）。
 * <p>
 * 落盘相对路径固定为 {@code {productId}/{version}/{fileName}}，与 nginx 静态托管目录同构；
 * MD5 用 {@code FileChannel + MessageDigest} 流式计算，避免整文件读入内存；
 * 同产品版本唯一由表级 {@code uk_product_version} 兜底，并捕获 {@link DuplicateKeyException}
 * 消除并发上传的检查-写入竞态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaFirmwareServiceImpl implements OtaFirmwareService {

    /** 版本号白名单字符集与长度上限。 */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9A-Za-z._-]{1,64}$");

    private static final int DIGEST_BUFFER = 8192;

    private final OtaFirmwareMapper firmwareMapper;
    private final OtaUpgradeTaskMapper taskMapper;
    private final ProductService productService;
    private final OtaProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OtaFirmwareVO upload(Long userId, Long productId, String version, String description, MultipartFile file) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_INVALID, "OTA 功能已关闭");
        }
        if (productId == null) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_INVALID, "固件所属产品不能为空");
        }
        Product product = productService.requireById(productId);
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_INVALID, "固件版本号格式非法");
        }
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_INVALID, "固件文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_INVALID,
                    "固件文件超出大小上限 " + properties.getMaxFileSize() + " 字节");
        }
        if (existsProductVersion(productId, version)) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_DUPLICATE, "同产品下该固件版本已存在");
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String relativePath = productId + "/" + version + "/" + fileName;
        Path absolute = resolveStorage(relativePath);
        try {
            Files.createDirectories(absolute.getParent());
            file.transferTo(absolute);
        } catch (IOException e) {
            log.warn("固件落盘失败: productId={}, version={}, file={}", productId, version, fileName, e);
            throw new BusinessException(ResultCode.OTA_UPLOAD_FAILED, "固件文件保存失败");
        }

        String md5;
        try {
            md5 = md5(absolute);
        } catch (Exception e) {
            deleteQuietly(absolute);
            log.warn("固件 MD5 计算失败: path={}", absolute, e);
            throw new BusinessException(ResultCode.OTA_UPLOAD_FAILED, "固件文件校验失败");
        }

        OtaFirmware entity = new OtaFirmware();
        entity.setUserId(userId);
        entity.setProductId(productId);
        entity.setVersion(version);
        entity.setFileName(fileName);
        entity.setFilePath(relativePath);
        entity.setFileSize(file.getSize());
        entity.setMd5(md5);
        entity.setDescription(description);
        try {
            firmwareMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            deleteQuietly(absolute);
            throw new BusinessException(ResultCode.OTA_FIRMWARE_DUPLICATE, "同产品下该固件版本已存在");
        }
        return toVO(entity, product.getProductName());
    }

    @Override
    public List<OtaFirmwareVO> list(Long userId, Long productId) {
        List<OtaFirmware> firmwares = firmwareMapper.selectList(Wrappers.<OtaFirmware>lambdaQuery()
                .eq(OtaFirmware::getUserId, userId)
                .eq(productId != null, OtaFirmware::getProductId, productId)
                .orderByDesc(OtaFirmware::getCreatedAt)
                .orderByDesc(OtaFirmware::getId));
        Map<Long, String> productNames = productNames();
        List<OtaFirmwareVO> result = new ArrayList<>(firmwares.size());
        for (OtaFirmware firmware : firmwares) {
            result.add(toVO(firmware, productNames.get(firmware.getProductId())));
        }
        return result;
    }

    @Override
    public OtaFirmwareVO detail(Long userId, Long id) {
        OtaFirmware firmware = requireOwned(userId, id);
        Map<Long, String> productNames = productNames();
        return toVO(firmware, productNames.get(firmware.getProductId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        OtaFirmware firmware = requireOwned(userId, id);
        Long referenced = taskMapper.selectCount(Wrappers.<OtaUpgradeTask>lambdaQuery()
                .eq(OtaUpgradeTask::getFirmwareId, id));
        if (referenced != null && referenced > 0) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_IN_USE, "固件包已被升级任务引用，无法删除");
        }
        firmwareMapper.deleteById(firmware.getId());
        deleteQuietly(resolveStorage(firmware.getFilePath()));
    }

    @Override
    public OtaFirmwareService.Download download(Long userId, Long id) {
        OtaFirmware firmware = requireOwned(userId, id);
        return new OtaFirmwareService.Download(firmware.getFileName(),
                resolveStorage(firmware.getFilePath()).toString(), firmware.getFileSize());
    }

    private boolean existsProductVersion(Long productId, String version) {
        Long count = firmwareMapper.selectCount(Wrappers.<OtaFirmware>lambdaQuery()
                .eq(OtaFirmware::getProductId, productId)
                .eq(OtaFirmware::getVersion, version));
        return count != null && count > 0;
    }

    private OtaFirmware requireOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_NOT_FOUND, "固件包不存在");
        }
        OtaFirmware firmware = firmwareMapper.selectById(id);
        if (firmware == null || !userId.equals(firmware.getUserId())) {
            throw new BusinessException(ResultCode.OTA_FIRMWARE_NOT_FOUND, "固件包不存在");
        }
        return firmware;
    }

    private OtaFirmwareVO toVO(OtaFirmware firmware, String productName) {
        OtaFirmwareVO vo = new OtaFirmwareVO();
        vo.setId(firmware.getId());
        vo.setProductId(firmware.getProductId());
        vo.setProductName(productName);
        vo.setVersion(firmware.getVersion());
        vo.setFileName(firmware.getFileName());
        vo.setFileSize(firmware.getFileSize());
        vo.setMd5(firmware.getMd5());
        vo.setDownloadUrl(buildDownloadUrl(firmware.getFilePath()));
        vo.setDescription(firmware.getDescription());
        vo.setCreatedAt(firmware.getCreatedAt());
        return vo;
    }

    private String buildDownloadUrl(String relativePath) {
        String base = properties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return relativePath;
        }
        return base.endsWith("/") ? base + relativePath : base + "/" + relativePath;
    }

    private Map<Long, String> productNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Product product : productService.list()) {
            names.put(product.getId(), product.getProductName());
        }
        return names;
    }

    private Path resolveStorage(String relativePath) {
        return Paths.get(properties.getStorageDir()).resolve(relativePath).normalize();
    }

    /** 只取原始文件名的最后一段，剔除路径分隔符，避免目录穿越。 */
    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "firmware.bin";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        return name.isEmpty() ? "firmware.bin" : name;
    }

    private String md5(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IOException("MD5 算法不可用", e);
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(DIGEST_BUFFER);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理固件文件失败: path={}", path, e);
        }
    }
}
