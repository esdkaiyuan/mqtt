-- T-30 · 接入协议抽象（路线 A + 路线 B 的共用底座）
--
-- 背景：改造前product 表没有任何「接入协议」维度，导致：
--   1. 一台纯 Modbus 设备只能被建模成「网关/采集器」，无法在数据模型上区分它
--      究竟是用 MQTT 直连、经边缘网关转换、还是由平台侧主站轮询采集；
--   2. 平台无法按协议下发不同的凭据类型（MQTT 密钥 / Modbus 无密钥 / X.509 证书）；
--   3. 路线 A（边缘驱动）与路线 B（平台侧 Modbus 主站）缺少共同的挂载点。
--
-- 本迁移只加「描述性维度」，不改任何既有写入路径：
-- 新列均可为 NULL，NULL 一律按 MQTT 直连处理，存量产品与设备行为完全不变。

ALTER TABLE product
    -- 接入协议：MQTT / HTTP / MODBUS / OPC_UA / GATEWAY_MQTT
    ADD COLUMN protocol_type VARCHAR(32) NULL
        COMMENT '接入协议：MQTT/HTTP/MODBUS/OPC_UA/GATEWAY_MQTT，NULL=默认MQTT' AFTER auth_mode,
    -- 驱动标识：平台侧协议栈用；MQTT/HTTP 恒为 NULL
    ADD COLUMN driver_id VARCHAR(64) NULL
        COMMENT '平台侧驱动标识（路线B：Modbus 主站用），MQTT/HTTP 为 NULL' AFTER protocol_type,
    -- 驱动配置 JSON：点表、轮询周期、连接参数等
    ADD COLUMN driver_config MEDIUMTEXT NULL
        COMMENT '驱动配置 JSON（点表/轮询周期/连接参数），随protocol_type 生效' AFTER driver_id;

-- 点表：平台侧协议栈（路线 B）与边缘驱动（路线 A）共用的寄存器/节点映射模型。
-- 两者的差别只在「谁来执行采集」——平台主站还是边缘网关——数据模型统一。
CREATE TABLE device_point_table (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    device_id       BIGINT       NOT NULL COMMENT '所属设备ID（采集网关或子设备）',
    point_key       VARCHAR(128) NOT NULL COMMENT '点位标识（映射到物模型属性/事件 identifier）',
    -- 点位类型：HOLDING_REGISTER / INPUT_REGISTER / COIL / DISCRETE_INPUT /
    --            DB / TAG / NODE
    point_type      VARCHAR(32)  NOT NULL COMMENT '点位类型（寄存器/线圈/DB/TAG/节点）',
    address         VARCHAR(64)  NULL COMMENT '地址（寄存器号/节点ID/tag 名）',
    data_type       VARCHAR(32)  NOT NULL DEFAULT 'int' COMMENT '数据类型 int/float/double/bool/string',
    scale           DECIMAL(18,6) NULL COMMENT '线性缩放系数（工程值 = 原始值 * scale + offset）',
    offset          DECIMAL(18,6) NULL COMMENT '线性偏移',
    unit            VARCHAR(32)  NULL COMMENT '单位',
    -- 执行方：PLATFORM=平台侧主站轮询（T-30 路线B）；EDGE=下发给边缘网关自行采集（路线A）
    executor        VARCHAR(16)  NOT NULL DEFAULT 'EDGE'
        COMMENT '执行方 PLATFORM/EDGE：平台主站轮询 或 边缘网关采集',
    read_interval_ms INT         NOT NULL DEFAULT 60000 COMMENT '采集周期（毫秒）',
    enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_point (device_id, point_key),
    KEY idx_device_enabled (device_id, enabled),
    KEY idx_executor (executor, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备点位表（Modbus 寄存器 / OPC-UA 节点映射）';

-- 采集任务执行日志：平台侧主站与边缘驱动上报统一留痕，便于区分「点位没配」与「采不到值」
CREATE TABLE device_point_run (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    point_id     BIGINT      NOT NULL COMMENT '点位ID',
    device_id    BIGINT      NOT NULL COMMENT '设备ID（冗余，便于按设备检索）',
    status       VARCHAR(16) NOT NULL COMMENT 'OK/FAILED/SKIPPED',
    raw_value    VARCHAR(255) NULL COMMENT '原始值',
    value        VARCHAR(255) NULL COMMENT '换算后工程值',
    error_message VARCHAR(500) NULL COMMENT '失败原因',
    collected_at DATETIME(3) NOT NULL COMMENT '采集时间',
    PRIMARY KEY (id),
    KEY idx_point_collected (point_id, collected_at),
    KEY idx_device_collected (device_id, collected_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '点位采集执行日志';
