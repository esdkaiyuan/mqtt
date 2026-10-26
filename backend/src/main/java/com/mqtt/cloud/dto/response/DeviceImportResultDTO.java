package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备 CSV 导入结果（T-53）。
 * <p>
 * {@code credentials} 为一次性明文，仅本次响应返回；平台只保存密钥哈希。
 */
@Data
public class DeviceImportResultDTO {

    private int totalCount;

    private int succeededCount;

    private int failedCount;

    private List<Credential> credentials = new ArrayList<>();

    private List<FailedItem> failedItems = new ArrayList<>();

    @Data
    public static class Credential {
        private String deviceKey;
        private String username;
        /** 一次性明文密钥，仅本次返回 */
        private String secret;
    }

    @Data
    public static class FailedItem {
        /** 数据行号（表头为第 1 行，数据从第 2 行开始） */
        private int rowNumber;
        private String deviceKey;
        private String reason;
    }
}