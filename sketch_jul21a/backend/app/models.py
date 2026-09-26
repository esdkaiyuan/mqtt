"""
SQLAlchemy models for motion data and fall events.
"""

from datetime import datetime
from sqlalchemy import (
    Column, Integer, String, Float, DateTime, Boolean, Text, Index, func
)
from app.database import Base


class MotionData(Base):
    """
    Stores raw motion sensor data from ESP32.
    Each record represents one sensor reading at a specific timestamp.
    """
    __tablename__ = "motion_data"

    id = Column(Integer, primary_key=True, autoincrement=True)
    device_id = Column(String(50), nullable=False, index=True)
    timestamp = Column(DateTime, nullable=False, index=True)

    # Accelerometer data (m/s^2 or g units)
    ax = Column(Float, nullable=False)  # X-axis acceleration
    ay = Column(Float, nullable=False)  # Y-axis acceleration
    az = Column(Float, nullable=False)  # Z-axis acceleration

    # Gyroscope data (degrees/second)
    gx = Column(Float, nullable=False)  # X-axis angular velocity
    gy = Column(Float, nullable=False)  # Y-axis angular velocity
    gz = Column(Float, nullable=False)  # Z-axis angular velocity

    # Metadata
    created_at = Column(DateTime, default=func.now(), server_default=func.now())

    # Fall detection fields
    is_fall = Column(Boolean, default=False)
    fall_type = Column(String(50), nullable=True)  # e.g., "forward", "backward", "lateral"
    confidence = Column(Float, nullable=True)  # 0.0 - 1.0
    notes = Column(Text, nullable=True)

    # Composite indexes for common queries
    __table_args__ = (
        Index("idx_device_timestamp", "device_id", "timestamp"),
        Index("idx_is_fall", "is_fall"),
    )

    def to_dict(self):
        """Convert to dictionary for JSON serialization."""
        return {
            "id": self.id,
            "device_id": self.device_id,
            "timestamp": self.timestamp.isoformat() if self.timestamp else None,
            "ax": self.ax,
            "ay": self.ay,
            "az": self.az,
            "gx": self.gx,
            "gy": self.gy,
            "gz": self.gz,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "is_fall": self.is_fall,
            "fall_type": self.fall_type,
            "confidence": self.confidence,
            "notes": self.notes,
        }


class FallEvent(Base):
    """
    Stores detected fall events.
    Each event represents a fall incident with start/end time and peak values.
    """
    __tablename__ = "fall_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    device_id = Column(String(50), nullable=False, index=True)
    start_time = Column(DateTime, nullable=False)
    end_time = Column(DateTime, nullable=True)
    peak_acceleration = Column(Float, nullable=False)  # Max acceleration during fall
    detected_at = Column(DateTime, default=func.now(), server_default=func.now())

    # Additional metadata
    fall_type = Column(String(50), nullable=True)
    confidence = Column(Float, nullable=True)
    notes = Column(Text, nullable=True)
    is_confirmed = Column(Boolean, default=False)  # Manual confirmation

    def to_dict(self):
        """Convert to dictionary for JSON serialization."""
        return {
            "id": self.id,
            "device_id": self.device_id,
            "start_time": self.start_time.isoformat() if self.start_time else None,
            "end_time": self.end_time.isoformat() if self.end_time else None,
            "peak_acceleration": self.peak_acceleration,
            "detected_at": self.detected_at.isoformat() if self.detected_at else None,
            "fall_type": self.fall_type,
            "confidence": self.confidence,
            "notes": self.notes,
            "is_confirmed": self.is_confirmed,
        }
