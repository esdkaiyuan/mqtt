package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.response.DeviceImportResultDTO;

/**
 * 设备 CSV 导入（T-53）。
 * <p>
 * 与「批量注册」互补：批量注册按前缀 + 序号生成 deviceKey，导入则接受设备台账文件，
 * 适合从既有系统迁移或由 Excel 维护设备清单。
 * <p>
 * 每台设备复用 {@link DeviceService#createDevice} 的独立事务：单行失败只记失败明细，
 * 不回滚已成功的行——1000 台里错 1 台不应导致整批作废。
 */
public interface DeviceImportService {

    /**
     * 导入设备台账。
     *
     * @param userId 设备归属用户（ADMIN / OPERATOR 为当前登录用户）
     * @param content 文件字节（UTF-8，可带 BOM）
     * @param fileName 原始文件名（仅用于错误提示）
     */
    DeviceImportResultDTO importCsv(Long userId, byte[] content, String fileName);

    /** 导入模板（带 BOM 的 CSV，Excel 直接打开不乱码）。 */
    byte[] template();
}