-- 摔倒检测数据库初始化脚本

-- 启用UUID扩展（可选）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 创建运动数据表
CREATE TABLE IF NOT EXISTS motion_data (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    timestamp BIGINT NOT NULL,
    ax REAL,
    ay REAL,
    az REAL,
    gx REAL,
    gy REAL,
    gz REAL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 摔倒标注字段
    is_fall BOOLEAN DEFAULT FALSE,
    fall_type VARCHAR(50),
    confidence REAL,
    notes TEXT
);

-- 创建索引
CREATE INDEX idx_motion_data_device_id ON motion_data(device_id);
CREATE INDEX idx_motion_data_timestamp ON motion_data(timestamp);
CREATE INDEX idx_motion_data_created_at ON motion_data(created_at);
CREATE INDEX idx_motion_data_device_timestamp ON motion_data(device_id, timestamp DESC);
CREATE INDEX idx_motion_data_is_fall ON motion_data(is_fall);

-- 创建摔倒事件表
CREATE TABLE IF NOT EXISTS fall_events (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    start_time BIGINT,
    end_time BIGINT,
    peak_acceleration REAL,
    peak_angular_velocity REAL,
    duration INTEGER,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 标注信息
    is_confirmed BOOLEAN DEFAULT FALSE,
    fall_type VARCHAR(50),
    notes TEXT
);

-- 创建索引
CREATE INDEX idx_fall_events_device_id ON fall_events(device_id);
CREATE INDEX idx_fall_events_detected_at ON fall_events(detected_at DESC);

-- 创建设备表（可选，用于管理多个ESP32）
CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100),
    description TEXT,
    is_active BOOLEAN DEFAULT FALSE,
    last_seen TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建数据统计视图
CREATE OR REPLACE VIEW data_stats AS
SELECT
    device_id,
    COUNT(*) as total_records,
    COUNT(CASE WHEN is_fall = TRUE THEN 1 END) as fall_records,
    MIN(created_at) as first_record,
    MAX(created_at) as last_record,
    COUNT(CASE WHEN created_at >= CURRENT_DATE THEN 1 END) as today_records
FROM motion_data
GROUP BY device_id;

-- 创建摔倒事件统计视图
CREATE OR REPLACE VIEW fall_stats AS
SELECT
    device_id,
    COUNT(*) as total_falls,
    COUNT(CASE WHEN is_confirmed = TRUE THEN 1 END) as confirmed_falls,
    MIN(detected_at) as first_fall,
    MAX(detected_at) as last_fall
FROM fall_events
GROUP BY device_id;

-- 创建清理旧数据的函数（保留最近30天数据）
CREATE OR REPLACE FUNCTION cleanup_old_data()
RETURNS void AS $$
BEGIN
    DELETE FROM motion_data
    WHERE created_at < NOW() - INTERVAL '30 days';

    DELETE FROM fall_events
    WHERE detected_at < NOW() - INTERVAL '90 days';
END;
$$ LANGUAGE plpgsql;

-- 创建触发器函数：自动更新设备最后在线时间
CREATE OR REPLACE FUNCTION update_device_last_seen()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO devices (id, last_seen, is_active)
    VALUES (NEW.device_id, NOW(), TRUE)
    ON CONFLICT (id)
    DO UPDATE SET
        last_seen = NOW(),
        is_active = TRUE;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
CREATE TRIGGER trigger_update_device_last_seen
    AFTER INSERT ON motion_data
    FOR EACH ROW
    EXECUTE FUNCTION update_device_last_seen();

-- 插入默认设备（可选）
INSERT INTO devices (id, name, description)
VALUES ('ESP32_001', 'ESP32开发板1', '摔倒检测数据采集设备')
ON CONFLICT (id) DO NOTHING;

-- 创建用户权限（如果需要）
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO fall_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO fall_user;
