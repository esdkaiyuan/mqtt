-- T-38 · 快速上位机（Web 组态）
-- 目标：对标 ThingsCloud「应用工作台」/ 阿里云 IoT「可视化应用」，
-- 按物模型一键生成「实时监控 + 控制」的单设备 Web 面板。
-- 说明：只加表，不改既有表；一个 HMI 固定绑定一台设备，归属创建者本人，硬删除。
--       config 为 JSON 文本（控件数组），由服务层解析并按物模型校验，非法配置不落库。
CREATE TABLE IF NOT EXISTS hmi_view (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id    BIGINT       NOT NULL COMMENT '创建者（sys_user.id）',
    device_id  BIGINT       NOT NULL COMMENT '绑定设备（device.id）',
    name       VARCHAR(64)  NOT NULL COMMENT '上位机名称',
    config     JSON         NOT NULL COMMENT '控件配置（title + widgets）',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_updated (user_id, updated_at),
    KEY idx_device (device_id),
    CONSTRAINT fk_hmi_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_hmi_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '快速上位机（Web 组态）';
