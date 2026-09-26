"""
REST endpoints for managing data-range annotations.
"""

import json
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import and_, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Annotation
from app.schemas import (
    AnnotationCreate,
    AnnotationOut,
    AnnotationUpdate,
    PaginatedAnnotations,
)

router = APIRouter(prefix="/annotations", tags=["annotations"])


def _get_or_404(annotation: Optional[Annotation], annotation_id: int) -> Annotation:
    if annotation is None:
        raise HTTPException(status_code=404, detail=f"Annotation {annotation_id} not found")
    return annotation


@router.get("", response_model=PaginatedAnnotations)
async def list_annotations(
    device_id: Optional[str] = None,
    type: Optional[str] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    limit: int = Query(100, ge=1, le=1000),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    """List annotations with optional device/type/date filtering."""
    filters = []
    if device_id:
        filters.append(Annotation.device_id == device_id)
    if type:
        filters.append(Annotation.type == type)
    if start_time:
        filters.append(Annotation.start_time >= start_time)
    if end_time:
        filters.append(Annotation.start_time <= end_time)

    count_query = select(func.count()).select_from(Annotation)
    query = select(Annotation)
    if filters:
        count_query = count_query.where(and_(*filters))
        query = query.where(and_(*filters))

    total = (await db.execute(count_query)).scalar() or 0
    result = await db.execute(
        query.order_by(Annotation.created_at.desc()).limit(limit).offset(offset)
    )

    return {
        "total": total,
        "limit": limit,
        "offset": offset,
        "data": [item.to_dict() for item in result.scalars().all()],
    }


@router.post("", response_model=AnnotationOut, status_code=201)
async def create_annotation(
    payload: AnnotationCreate,
    db: AsyncSession = Depends(get_db),
):
    """Create a new annotation."""
    if payload.end_time < payload.start_time:
        raise HTTPException(status_code=422, detail="end_time must not be earlier than start_time")

    annotation = Annotation(
        device_id=payload.device_id,
        start_time=payload.start_time,
        end_time=payload.end_time,
        data_count=payload.data_count,
        type=payload.type,
        confidence=payload.confidence,
        quality=payload.quality,
        tags=json.dumps(payload.tags) if payload.tags else None,
        notes=payload.notes,
    )
    db.add(annotation)
    await db.commit()
    await db.refresh(annotation)
    return annotation.to_dict()


@router.get("/{annotation_id}", response_model=AnnotationOut)
async def get_annotation(annotation_id: int, db: AsyncSession = Depends(get_db)):
    """Get a single annotation by ID."""
    result = await db.execute(select(Annotation).where(Annotation.id == annotation_id))
    annotation = _get_or_404(result.scalar_one_or_none(), annotation_id)
    return annotation.to_dict()


@router.put("/{annotation_id}", response_model=AnnotationOut)
async def update_annotation(
    annotation_id: int,
    payload: AnnotationUpdate,
    db: AsyncSession = Depends(get_db),
):
    """Partially update an annotation."""
    result = await db.execute(select(Annotation).where(Annotation.id == annotation_id))
    annotation = _get_or_404(result.scalar_one_or_none(), annotation_id)

    updates = payload.model_dump(exclude_unset=True)
    if "tags" in updates:
        tags = updates.pop("tags")
        annotation.tags = json.dumps(tags) if tags else None
    for field, value in updates.items():
        setattr(annotation, field, value)

    if annotation.end_time < annotation.start_time:
        raise HTTPException(status_code=422, detail="end_time must not be earlier than start_time")

    await db.commit()
    await db.refresh(annotation)
    return annotation.to_dict()


@router.delete("/{annotation_id}", status_code=204)
async def delete_annotation(annotation_id: int, db: AsyncSession = Depends(get_db)):
    """Delete an annotation."""
    result = await db.execute(select(Annotation).where(Annotation.id == annotation_id))
    annotation = _get_or_404(result.scalar_one_or_none(), annotation_id)

    await db.delete(annotation)
    await db.commit()