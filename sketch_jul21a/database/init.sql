-- Fall Detection System — PostgreSQL bootstrap
--
-- This script mirrors the SQLAlchemy models in `backend/app/models.py`, which
-- remain the single source of truth (`Base.metadata.create_all` runs on
-- backend startup). Keeping both in sync lets Postgres come up pre-seeded for
-- docker-compose while still supporting plain SQLite for local development.
--
-- It runs once, from docker-entrypoint-initdb.d, on an empty data volume.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS motion_data (
    id          BIGSERIAL PRIMARY KEY,
    device_id   VARCHAR(50)  NOT NULL,
    timestamp   TIMESTAMP    NOT NULL,
    ax          FLOAT        NOT NULL,
    ay          FLOAT        NOT NULL,
    az          FLOAT        NOT NULL,
    gx          FLOAT        NOT NULL,
    gy          FLOAT        NOT NULL,
    gz          FLOAT        NOT NULL,
    created_at  TIMESTAMP    DEFAULT NOW(),
    is_fall     BOOLEAN      DEFAULT FALSE,
    fall_type   VARCHAR(50),
    confidence  FLOAT,
    notes       TEXT
);

CREATE INDEX IF NOT EXISTS idx_device_timestamp ON motion_data (device_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_is_fall          ON motion_data (is_fall);

CREATE TABLE IF NOT EXISTS fall_events (
    id                 BIGSERIAL PRIMARY KEY,
    device_id          VARCHAR(50) NOT NULL,
    start_time         TIMESTAMP   NOT NULL,
    end_time           TIMESTAMP,
    peak_acceleration  FLOAT       NOT NULL,
    detected_at        TIMESTAMP   DEFAULT NOW(),
    fall_type          VARCHAR(50),
    confidence         FLOAT,
    notes              TEXT,
    is_confirmed       BOOLEAN     DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_fall_events_device_id   ON fall_events (device_id);
CREATE INDEX IF NOT EXISTS idx_fall_events_detected_at ON fall_events (detected_at DESC);

CREATE TABLE IF NOT EXISTS annotations (
    id          BIGSERIAL PRIMARY KEY,
    device_id   VARCHAR(50),
    start_time  TIMESTAMP    NOT NULL,
    end_time    TIMESTAMP    NOT NULL,
    data_count  INTEGER      NOT NULL DEFAULT 0,
    type        VARCHAR(50)  NOT NULL,
    confidence  INTEGER,
    quality     INTEGER,
    tags        TEXT,
    notes       TEXT,
    created_at  TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_annotations_device_id  ON annotations (device_id);
CREATE INDEX IF NOT EXISTS idx_annotations_start_time ON annotations (start_time);

-- ---------------------------------------------------------------------------
-- Reporting views
-- ---------------------------------------------------------------------------

CREATE OR REPLACE VIEW data_stats AS
SELECT
    device_id,
    COUNT(*)                                        AS total_records,
    COUNT(*) FILTER (WHERE is_fall)                 AS fall_records,
    MIN(timestamp)                                  AS first_seen,
    MAX(timestamp)                                  AS last_seen
FROM motion_data
GROUP BY device_id;

CREATE OR REPLACE VIEW fall_stats AS
SELECT
    device_id,
    COUNT(*)                                        AS total_falls,
    COUNT(*) FILTER (WHERE is_confirmed)            AS confirmed_falls,
    MIN(detected_at)                                AS first_fall,
    MAX(detected_at)                                AS last_fall
FROM fall_events
GROUP BY device_id;

-- ---------------------------------------------------------------------------
-- Retention helper
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION cleanup_old_data(retention_days INTEGER DEFAULT 365)
RETURNS void AS $$
BEGIN
    DELETE FROM motion_data
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;

    DELETE FROM fall_events
    WHERE detected_at < NOW() - (retention_days || ' days')::INTERVAL;
END;
$$ LANGUAGE plpgsql;