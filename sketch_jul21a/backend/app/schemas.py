"""
Pydantic schemas (DTOs) for request validation and response shaping.
"""

from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


class AnnotationCreate(BaseModel):
    """Payload for creating an annotation over a selected data range."""

    type: str = Field(..., min_length=1, max_length=50)
    start_time: datetime
    end_time: datetime
    device_id: Optional[str] = Field(None, max_length=50)
    data_count: int = Field(0, ge=0)
    confidence: Optional[int] = Field(None, ge=0, le=100)
    quality: Optional[int] = Field(None, ge=1, le=5)
    tags: List[str] = Field(default_factory=list)
    notes: Optional[str] = None


class AnnotationUpdate(BaseModel):
    """Partial update payload for an existing annotation."""

    type: Optional[str] = Field(None, min_length=1, max_length=50)
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    device_id: Optional[str] = Field(None, max_length=50)
    data_count: Optional[int] = Field(None, ge=0)
    confidence: Optional[int] = Field(None, ge=0, le=100)
    quality: Optional[int] = Field(None, ge=1, le=5)
    tags: Optional[List[str]] = None
    notes: Optional[str] = None


class AnnotationOut(BaseModel):
    """Annotation representation returned by the API."""

    model_config = ConfigDict(from_attributes=True)

    id: int
    device_id: Optional[str] = None
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    data_count: int
    type: str
    confidence: Optional[int] = None
    quality: Optional[int] = None
    tags: List[str] = Field(default_factory=list)
    notes: Optional[str] = None
    created_at: Optional[datetime] = None


class PaginatedAnnotations(BaseModel):
    """Paginated annotation list."""

    total: int
    limit: int
    offset: int
    data: List[AnnotationOut]


class DeviceInfo(BaseModel):
    """Runtime metadata for a single device, derived from stored motion data."""

    device_id: str
    total_records: int
    fall_records: int
    first_seen: Optional[datetime] = None
    last_seen: Optional[datetime] = None
    last_fall_at: Optional[datetime] = None
    online: bool


class MotionAnnotateRequest(BaseModel):
    """Manual override of the fall-detection result for one sample."""

    data_id: int
    is_fall: Optional[bool] = None
    fall_type: Optional[str] = Field(None, max_length=50)
    notes: Optional[str] = None