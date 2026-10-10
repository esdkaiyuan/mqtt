package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 批量注册响应（T-30）。
 * <p>
 * {@code credentials} 为一次性凭据明文，仅本次返回，平台不提供二次查询。
 */
@Data
public class BatchRegisterResultDTO {

    /** 批次号，供后续追溯 */
    private String batchNo;

    private Integer totalCount;
    private Integer succeededCount;
    private Integer failedCount;

    /** 失败明细 */
    private List<FailedItem> failedItems;

    /** 一次性凭据明文 */
    private List<Credential> credentials;

    @Data
    public static class FailedItem {
        private Integer index;
        private String deviceKey;
        private String reason;
    }

    @Data
    public static class Credential {
        private String deviceKey;
        /** MQTT 认证用户名：{productKey}.{deviceKey} */
        private String username;
        /** 一次性明文密钥 */
        private String secret;
        /** 激活码，{@code issueActivateCode=true} 时非空 */
        private String activateCode;
    }
}
