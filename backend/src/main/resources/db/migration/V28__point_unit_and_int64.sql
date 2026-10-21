-- T-50：Modbus 多从站与 64 位整型。
-- unit_id 为空表示继承设备级 driver_config.unitId；点位级配置优先。

ALTER TABLE device_point_table
    ADD COLUMN unit_id INT NULL COMMENT 'Modbus 从站地址（0~247）；NULL=继承 driver_config.unitId' AFTER address;

ALTER TABLE device_point_table
    MODIFY COLUMN data_type VARCHAR(32) NOT NULL DEFAULT 'int'
        COMMENT '数据类型 int/int32/uint32/int64/uint64/float/double/bool/string';
