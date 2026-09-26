-- 原始数据采集系统 - 数据库初始化
-- 版本: 2.0.0

-- 创建数据库（如果不存在）
-- CREATE DATABASE fall_detection;

-- 创建运动数据表（简化版）
CREATE TABLE IF NOT EXISTS motion_data (
    id BIGSERIAL PRIMARY KEY,

    -- 设备信息
    device_id VARCHAR(50) NOT NULL,
    timestamp BIGINT NOT NULL,

    -- 加速度数据（单位：g）
    ax REAL,
    ay REAL,
    az REAL,

    -- 角速度数据（单位：°/s）
    gx REAL,
    gy REAL,
    gz REAL,

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引（提高查询性能）
CREATE INDEX IF NOT EXISTS idx_motion_device_id ON motion_data(device_id);
CREATE INDEX IF NOT EXISTS idx_motion_timestamp ON motion_data(timestamp);
CREATE INDEX IF NOT EXISTS idx_motion_created_at ON motion_data(created_at);
CREATE INDEX IF NOT EXISTS idx_motion_device_timestamp ON motion_data(device_id, timestamp DESC);

-- 统计视图
CREATE OR REPLACE VIEW data_stats AS
SELECT
    device_id,
    COUNT(*) as total_records,
    MIN(created_at) as first_record,
    MAX(created_at) as last_record,
    COUNT(CASE WHEN created_at >= CURRENT_DATE THEN 1 END) as today_records
FROM motion_data
GROUP BY device_id;

-- 清理函数（可选：删除30天前的数据）
CREATE OR REPLACE FUNCTION cleanup_old_data(days_to_keep INTEGER DEFAULT 30)
RETURNS void AS $$
BEGIN
    DELETE FROM motion_data
    WHERE created_at < NOW() - (days_to_keep || ' days')::INTERVAL;
END;
$$ LANGUAGE plpgsql;

-- 示例查询
-- SELECT * FROM data_stats;
-- SELECT cleanup_old_data(30);
-- SELECT * FROM motion_data WHERE device_id = 'ESP32_001' ORDER BY id DESC LIMIT 100;
