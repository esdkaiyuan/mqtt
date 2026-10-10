-- ============================================================
-- V14：设备拓扑（T-26 子设备与网关代理接入）
-- 用途：
--   1) device.node_type  拓扑角色：DIRECT / GATEWAY / SUB
--   2) device.parent_id  子设备指向所属网关（自引用）
-- 说明：只加列与索引，不改既有列，应用版本可安全回滚（回滚后新列留存，旧代码忽略）。
--       存量设备 node_type 一律为 DIRECT、parent_id 一律为 NULL，
--       与既有「全部为直连设备」语义等价，迁移安全。
--       深度上限（默认 2）与单网关子设备数上限（默认 200）在应用层校验，
--       库层只保证引用完整性：父设备删除时子设备 parent_id 置 NULL，
--       子设备回退为无父状态，不做级联删除。
-- 注意：索引 idx_device_parent (parent_id, deleted) 支撑「按网关列子设备」与
--       「按子设备查归属」两条热路径；同一产品内的设备键唯一约束 uk_product_device
--       不变，子设备键天然受其覆盖，无需另建约束。
--       外键 fk_device_parent 的 ON DELETE SET NULL 使父设备被硬删时子设备自动回退，
--       不产生级联删除；逻辑删除（deleted=1）不触发该动作，父设备仍可被查回。
-- ============================================================

ALTER TABLE device ADD COLUMN node_type VARCHAR(16) NOT NULL DEFAULT 'DIRECT' COMMENT '拓扑角色：DIRECT=普通设备，GATEWAY=网关，SUB=子设备';
ALTER TABLE device ADD COLUMN parent_id BIGINT NULL COMMENT '父网关设备（device.id），仅 node_type=SUB 时非空';
CREATE INDEX idx_device_parent ON device (parent_id, deleted);
ALTER TABLE device ADD CONSTRAINT fk_device_parent FOREIGN KEY (parent_id) REFERENCES device(id) ON DELETE SET NULL;
