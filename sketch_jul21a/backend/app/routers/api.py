"""
REST API endpoints for data management, querying, and export.
"""

from datetime import datetime, timedelta
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, and_
from app.database import get_db
from app.models import MotionData, FallEvent
from app.services.data_service import DataService
import csv
import io

router = APIRouter()


@router.get("/data")
async def get_motion_data(
    device_id: Optional[str] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    is_fall: Optional[bool] = None,
    limit: int = Query(100, le=10000),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    """
    Get motion data list with pagination and filtering.

    Parameters:
    - device_id: Filter by device
    - start_time: Filter by start timestamp
    - end_time: Filter by end timestamp
    - is_fall: Filter by fall status
    - limit: Number of records (max 10000)
    - offset: Pagination offset
    """
    # Build query
    query = select(MotionData)
    count_query = select(func.count()).select_from(MotionData)

    # Apply filters
    filters = []
    if device_id:
        filters.append(MotionData.device_id == device_id)
    if start_time:
        filters.append(MotionData.timestamp >= start_time)
    if end_time:
        filters.append(MotionData.timestamp <= end_time)
    if is_fall is not None:
        filters.append(MotionData.is_fall == is_fall)

    if filters:
        query = query.where(and_(*filters))
        count_query = count_query.where(and_(*filters))

    # Get total count
    total_result = await db.execute(count_query)
    total = total_result.scalar()

    # Get paginated results
    query = query.order_by(MotionData.timestamp.desc()).limit(limit).offset(offset)
    result = await db.execute(query)
    data = result.scalars().all()

    return {
        "total": total,
        "limit": limit,
        "offset": offset,
        "data": [item.to_dict() for item in data],
    }


@router.get("/data/{data_id}")
async def get_motion_data_by_id(data_id: int, db: AsyncSession = Depends(get_db)):
    """Get single motion data record by ID."""
    result = await db.execute(
        select(MotionData).where(MotionData.id == data_id)
    )
    data = result.scalar_one_or_none()

    if not data:
        raise HTTPException(status_code=404, detail="Data not found")

    return data.to_dict()


@router.post("/data/annotate")
async def annotate_motion_data(
    data_id: int,
    fall_type: Optional[str] = None,
    notes: Optional[str] = None,
    is_fall: Optional[bool] = None,
    db: AsyncSession = Depends(get_db),
):
    """
    Annotate motion data with fall type and notes.

    Parameters:
    - data_id: ID of the motion data record
    - fall_type: Type of fall (forward, backward, lateral, etc.)
    - notes: Additional notes
    - is_fall: Manual override for fall status
    """
    result = await db.execute(
        select(MotionData).where(MotionData.id == data_id)
    )
    data = result.scalar_one_or_none()

    if not data:
        raise HTTPException(status_code=404, detail="Data not found")

    # Update annotation fields
    if fall_type is not None:
        data.fall_type = fall_type
    if notes is not None:
        data.notes = notes
    if is_fall is not None:
        data.is_fall = is_fall

    await db.commit()
    await db.refresh(data)

    return {"status": "annotated", "data": data.to_dict()}


@router.get("/fall-events")
async def get_fall_events(
    device_id: Optional[str] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    limit: int = Query(50, le=1000),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    """
    Get fall events with optional filtering.

    Parameters:
    - device_id: Filter by device
    - start_time: Filter by start timestamp
    - end_time: Filter by end timestamp
    - limit: Number of records (max 1000)
    - offset: Pagination offset
    """
    query = select(FallEvent)
    count_query = select(func.count()).select_from(FallEvent)

    # Apply filters
    filters = []
    if device_id:
        filters.append(FallEvent.device_id == device_id)
    if start_time:
        filters.append(FallEvent.detected_at >= start_time)
    if end_time:
        filters.append(FallEvent.detected_at <= end_time)

    if filters:
        query = query.where(and_(*filters))
        count_query = count_query.where(and_(*filters))

    # Get total count
    total_result = await db.execute(count_query)
    total = total_result.scalar()

    # Get paginated results
    query = query.order_by(FallEvent.detected_at.desc()).limit(limit).offset(offset)
    result = await db.execute(query)
    events = result.scalars().all()

    return {
        "total": total,
        "limit": limit,
        "offset": offset,
        "data": [event.to_dict() for event in events],
    }


@router.get("/stats")
async def get_statistics(
    device_id: Optional[str] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    db: AsyncSession = Depends(get_db),
):
    """
    Get statistics: total records, fall count, device count, etc.
    """
    stats = await DataService.get_statistics(
        db=db,
        device_id=device_id,
        start_time=start_time,
        end_time=end_time,
    )
    return stats


@router.get("/export")
async def export_csv(
    device_id: Optional[str] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    include_falls_only: bool = False,
    db: AsyncSession = Depends(get_db),
):
    """
    Export motion data as CSV file.

    Parameters:
    - device_id: Filter by device
    - start_time: Filter by start timestamp
    - end_time: Filter by end timestamp
    - include_falls_only: Export only fall-detected records
    """
    # Build query
    query = select(MotionData)
    filters = []

    if device_id:
        filters.append(MotionData.device_id == device_id)
    if start_time:
        filters.append(MotionData.timestamp >= start_time)
    if end_time:
        filters.append(MotionData.timestamp <= end_time)
    if include_falls_only:
        filters.append(MotionData.is_fall == True)

    if filters:
        query = query.where(and_(*filters))

    query = query.order_by(MotionData.timestamp.desc())

    result = await db.execute(query)
    data = result.scalars().all()

    # Create CSV in memory
    output = io.StringIO()
    writer = csv.writer(output)

    # Write header
    writer.writerow([
        "id", "device_id", "timestamp", "ax", "ay", "az",
        "gx", "gy", "gz", "is_fall", "fall_type", "confidence", "notes"
    ])

    # Write data
    for record in data:
        writer.writerow([
            record.id, record.device_id,
            record.timestamp.isoformat() if record.timestamp else "",
            record.ax, record.ay, record.az,
            record.gx, record.gy, record.gz,
            record.is_fall, record.fall_type, record.confidence, record.notes
        ])

    output.seek(0)

    # Return as streaming response
    filename = f"motion_data_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
    return StreamingResponse(
        io.BytesIO(output.getvalue().encode("utf-8")),
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename={filename}"}
    )
