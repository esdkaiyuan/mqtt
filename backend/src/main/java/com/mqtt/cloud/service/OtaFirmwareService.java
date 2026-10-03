package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.response.OtaFirmwareVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * OTA 固件包服务（T-22 设计文档 §7.1）。
 * <p>
 * 负责固件包的上传落盘（含 MD5 计算与同产品版本唯一约束）、列表、详情、删除与下载句柄。
 * 文件本体落盘到 {@code app.ota.storage-dir}，对外下载地址由 nginx 静态托管
 * （{@code app.ota.public-base-url}），本服务只维护元数据与存储相对路径。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code ..mapper..}，持久化细节只出现在
 * {@code service.impl}。
 */
public interface OtaFirmwareService {

    /**
     * 上传固件包。
     *
     * @param userId      上传者（当前登录用户）
     * @param productId   所属产品
     * @param version     固件版本号（产品内唯一，{@code ^[0-9A-Za-z._-]{1,64}$}）
     * @param description 版本说明，可空
     * @param file        固件文件本体
     * @return 固件包视图（含拼装后的 {@code downloadUrl}）
     * @throws com.mqtt.cloud.common.exception.BusinessException 产品不存在抛 {@code 6002}；
     *         版本非法 / 文件为空 / 超出大小上限抛 {@code 6232}；同产品版本已存在抛 {@code 6233}；
     *         落盘失败抛 {@code 6235}
     */
    OtaFirmwareVO upload(Long userId, Long productId, String version, String description, MultipartFile file);

    /** 固件包列表；{@code productId} 为空时返回该用户全部固件。 */
    List<OtaFirmwareVO> list(Long userId, Long productId);

    /** 固件包详情；不存在或不属于该用户抛 {@code 6231}。 */
    OtaFirmwareVO detail(Long userId, Long id);

    /** 删除固件包（删除记录并清理磁盘文件）；被升级任务引用抛 {@code 6234}，不存在抛 {@code 6231}。 */
    void remove(Long userId, Long id);

    /**
     * 取下载句柄，供 Controller 流式写回。
     *
     * @return 文件名、磁盘绝对路径与字节数
     * @throws com.mqtt.cloud.common.exception.BusinessException 记录不存在抛 {@code 6231}
     */
    OtaFirmwareService.Download download(Long userId, Long id);

    /** 下载句柄（只读投影，非实体）。 */
    record Download(String fileName, String filePath, long fileSize) {
    }
}
