"""
Data service for database operations and statistics.
"""

from datetime import datetime, timedelta
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, and_, distinct
from app.models import MotionData, FallEvent


class DataService:
    """Service class for data-related database operations."""

    @staticmethod
    async def create_fall_event(
        db: AsyncSession,
        device_id: str,
        timestamp: datetime,
        peak_accel: float,
        fall_type: Optional[str] = None,
        confidence: Optional[float] = None,
    ) -> FallEvent:
        """Create a new fall event record."""
        fall_event = FallEvent(
            device_id=device_id,
            start_time=timestamp,
            end_time=timestamp + timedelta(seconds=1),  # Assume 1-second fall
            peak_acceleration=peak_accel,
            fall_type=fall_type,
            confidence=confidence,
        )
        db.add(fall_event)
        await db.commit()
        await db.refresh(fall_event)
        return fall_event

    @staticmethod
    async def get_statistics(
        db: AsyncSession,
        device_id: Optional[str] = None,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
    ) -> dict:
        """
        Get comprehensive statistics.

        Returns dict with:
        - total_records: Total motion data records
        - total_falls: Total fall events
        - unique_devices: Number of unique devices
        - fall_rate: Percentage of fall records
        - avg_acceleration: Average acceleration magnitude
        - recent_falls: Last 24 hours fall count
        """
        # Base filter conditions
        filters = []
        if device_id:
            filters.append(MotionData.device_id == device_id)
        if start_time:
            filters.append(MotionData.timestamp >= start_time)
        if end_time:
            filters.append(MotionData.timestamp <= end_time)

        # Total records count
        count_query = select(func.count()).select_from(MotionData)
        if filters:
            count_query = count_query.where(and_(*filters))
        total_result = await db.execute(count_query)
        total_records = total_result.scalar()

        # Total falls count
        fall_filter = [MotionData.is_fall == True] + filters
        fall_count_query = select(func.count()).select_from(MotionData).where(and_(*fall_filter))
        fall_result = await db.execute(fall_count_query)
        total_falls = fall_result.scalar()

        # Unique devices count
        device_query = select(func.count(distinct(MotionData.device_id)))
        if filters:
            device_query = device_query.where(and_(*filters))
        device_result = await db.execute(device_query)
        unique_devices = device_result.scalar()

        # Fall events count
        fall_event_filters = []
        if device_id:
            fall_event_filters.append(FallEvent.device_id == device_id)
        if start_time:
            fall_event_filters.append(FallEvent.detected_at >= start_time)
        if end_time:
            fall_event_filters.append(FallEvent.detected_at <= end_time)

        fall_event_query = select(func.count()).select_from(FallEvent)
        if fall_event_filters:
            fall_event_query = fall_event_query.where(and_(*fall_event_filters))
        fall_event_result = await db.execute(fall_event_query)
        total_fall_events = fall_event_result.scalar()

        # Recent falls (last 24 hours)
        yesterday = datetime.utcnow() - timedelta(hours=24)
        recent_filters = [MotionData.is_fall == True, MotionData.timestamp >= yesterday]
        if device_id:
            recent_filters.append(MotionData.device_id == device_id)
        recent_query = select(func.count()).select_from(MotionData).where(and_(*recent_filters))
        recent_result = await db.execute(recent_query)
        recent_falls = recent_result.scalar()

        # Calculate fall rate
        fall_rate = (total_falls / total_records * 100) if total_records > 0 else 0.0

        return {
            "total_records": total_records,
            "total_falls": total_falls,
            "total_fall_events": total_fall_events,
            "unique_devices": unique_devices,
            "fall_rate_percent": round(fall_rate, 2),
            "recent_falls_24h": recent_falls,
            "query_params": {
                "device_id": device_id,
                "start_time": start_time.isoformat() if start_time else None,
                "end_time": end_time.isoformat() if end_time else None,
            },
        }

    @staticmethod
    async def get_device_list(db: AsyncSession) -> list:
        """Get list of all unique device IDs."""
        query = select(distinct(MotionData.device_id))
        result = await db.execute(query)
        return [row[0] for row in result.all()]

    @staticmethod
    async def get_data_by_device(
        db: AsyncSession,
        device_id: str,
        limit: int = 100,
        offset: int = 0,
    ) -> list:
        """Get motion data for a specific device."""
        query = (
            select(MotionData)
            .where(MotionData.device_id == device_id)
            .order_by(MotionData.timestamp.desc())
            .limit(limit)
            .offset(offset)
        )
        result = await db.execute(query)
        return result.scalars().all()
