"""
WebSocket endpoints.

- ``/ws/motion/{device_id}`` — ingest stream for the ESP32 device (write path).
- ``/ws/view/{device_id}``   — live stream for frontend clients (read-only).
"""

import json
from datetime import datetime
from typing import Any, Dict, Optional

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.database import AsyncSessionLocal
from app.models import MotionData
from app.services.connection_manager import manager
from app.services.data_service import DataService
from app.services.fall_detection import FallDetectionService

router = APIRouter()

fall_detector = FallDetectionService()

# Device-relative clock offset (device millis -> wall clock), per device.
_clock_offsets: Dict[str, float] = {}


def _parse_timestamp(raw: Any, device_id: str) -> datetime:
    """
    Normalise the timestamp sent by the firmware.

    Accepts an ISO-8601 string, epoch seconds/milliseconds, or a device-relative
    millisecond counter (as sent by the Arduino firmware, which reports
    ``millis()``). Relative counters are anchored to wall-clock time using a
    per-device offset captured on first contact.
    """
    if isinstance(raw, str):
        try:
            return datetime.fromisoformat(raw.replace("Z", "+00:00"))
        except ValueError:
            pass
    elif isinstance(raw, (int, float)):
        value = float(raw)
        if value > 1e11:  # epoch milliseconds
            return datetime.fromtimestamp(value / 1000)
        if value > 1e9:  # epoch seconds
            return datetime.fromtimestamp(value)
        # Device-relative millis: anchor to wall clock.
        now = datetime.utcnow()
        offset = _clock_offsets.setdefault(
            device_id, now.timestamp() - value / 1000
        )
        return datetime.fromtimestamp(value / 1000 + offset)

    return datetime.utcnow()


def _normalise_payload(payload: Any, device_id: str) -> list:
    """Coerce a single sample or a JSON array of samples into a list of samples."""
    if isinstance(payload, dict):
        return [payload]
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    return []


async def _persist_sample(device_id: str, sample: dict) -> Optional[MotionData]:
    """Persist one sensor sample, run fall detection, and return the stored row."""
    async with AsyncSessionLocal() as db:
        timestamp = _parse_timestamp(sample.get("timestamp"), device_id)

        motion_data = MotionData(
            device_id=device_id,
            timestamp=timestamp,
            ax=float(sample.get("ax", 0.0)),
            ay=float(sample.get("ay", 0.0)),
            az=float(sample.get("az", 0.0)),
            gx=float(sample.get("gx", 0.0)),
            gy=float(sample.get("gy", 0.0)),
            gz=float(sample.get("gz", 0.0)),
        )

        is_fall, fall_type, confidence = fall_detector.detect(
            ax=motion_data.ax,
            ay=motion_data.ay,
            az=motion_data.az,
            gx=motion_data.gx,
            gy=motion_data.gy,
            gz=motion_data.gz,
            device_id=device_id,
        )

        motion_data.is_fall = is_fall
        motion_data.fall_type = fall_type
        motion_data.confidence = confidence

        db.add(motion_data)
        await db.commit()
        await db.refresh(motion_data)

        if is_fall:
            await DataService.create_fall_event(
                db=db,
                device_id=device_id,
                timestamp=timestamp,
                peak_accel=fall_detector.get_peak_acceleration(device_id),
                fall_type=fall_type,
                confidence=confidence,
            )

        return motion_data


async def _handle_batch(websocket: WebSocket, device_id: str, raw: str) -> int:
    """Process one inbound frame. Returns the number of stored samples."""
    payload = json.loads(raw)
    samples = _normalise_payload(payload, device_id)
    if not samples:
        await websocket.send_json({"status": "error", "message": "Unsupported payload"})
        return 0

    stored = []
    for sample in samples:
        motion_data = await _persist_sample(device_id, sample)
        if motion_data is not None:
            stored.append(motion_data)

    last = stored[-1]
    fall_sample = next((item for item in stored if item.is_fall), None)

    await websocket.send_json(
        {
            "status": "ok",
            "count": len(stored),
            "id": last.id,
            "is_fall": bool(fall_sample),
            "timestamp": last.timestamp.isoformat(),
        }
    )

    await manager.broadcast_to_viewers(
        device_id,
        {
            "type": "sensor_data",
            "device_id": device_id,
            "data": [item.to_dict() for item in stored],
        },
    )

    if fall_sample is not None:
        await manager.broadcast_to_viewers(
            device_id,
            {
                "type": "fall_detected",
                "device_id": device_id,
                "fall_type": fall_sample.fall_type,
                "confidence": fall_sample.confidence,
                "timestamp": fall_sample.timestamp.isoformat(),
            },
        )

    return len(stored)


@router.websocket("/ws/motion/{device_id}")
async def websocket_motion_data(websocket: WebSocket, device_id: str):
    """
    Ingest endpoint for the ESP32 device.

    Accepts either a single sample object or a JSON array of samples::

        [{"timestamp": 12345, "ax": 0.12, "ay": 0.05, "az": 9.81,
          "gx": 1.23, "gy": -0.45, "gz": 0.67}, ...]
    """
    await websocket.accept()
    manager.add_sender(device_id, websocket)

    try:
        while True:
            raw = await websocket.receive_text()
            try:
                await _handle_batch(websocket, device_id, raw)
            except json.JSONDecodeError as exc:
                await websocket.send_json({"status": "error", "message": f"Invalid JSON: {exc}"})
            except Exception as exc:  # keep the socket alive on per-frame errors
                await websocket.send_json({"status": "error", "message": str(exc)})
    except WebSocketDisconnect:
        pass
    finally:
        was_last = manager.remove_sender(device_id, websocket)
        if was_last:
            fall_detector.cleanup_device(device_id)
            _clock_offsets.pop(device_id, None)


@router.websocket("/ws/view/{device_id}")
async def websocket_viewer(websocket: WebSocket, device_id: str):
    """Read-only live stream for frontend clients."""
    await websocket.accept()
    manager.add_viewer(device_id, websocket)

    await websocket.send_json(
        {
            "type": "device_info",
            "device_id": device_id,
            "online": manager.is_device_online(device_id),
        }
    )

    try:
        while True:
            # Viewers are consumers only; drain keep-alive frames.
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        manager.remove_viewer(device_id, websocket)


@router.get("/ws/status", tags=["websocket"])
async def websocket_status():
    """Report active WebSocket connections."""
    return manager.stats()