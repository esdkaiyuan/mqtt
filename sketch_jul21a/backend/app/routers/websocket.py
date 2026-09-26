"""
WebSocket endpoint for receiving real-time motion data from ESP32.
"""

import json
from datetime import datetime
from typing import Dict, List
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db, AsyncSessionLocal
from app.models import MotionData
from app.services.fall_detection import FallDetectionService
from app.services.data_service import DataService

router = APIRouter()

# Store active WebSocket connections per device
active_connections: Dict[str, List[WebSocket]] = {}

# Fall detection service instance
fall_detector = FallDetectionService()


@router.websocket("/ws/motion/{device_id}")
async def websocket_motion_data(websocket: WebSocket, device_id: str):
    """
    WebSocket endpoint to receive real-time motion data from ESP32.

    Data format expected from ESP32:
    {
        "timestamp": "2026-07-21T10:30:00.000Z",
        "ax": 0.12, "ay": 0.05, "az": 9.81,
        "gx": 1.23, "gy": -0.45, "gz": 0.67
    }
    """
    await websocket.accept()

    # Add connection to active connections
    if device_id not in active_connections:
        active_connections[device_id] = []
    active_connections[device_id].append(websocket)

    print(f"[WS] Device {device_id} connected. Active connections: {len(active_connections[device_id])}")

    try:
        while True:
            # Receive data from ESP32
            data = await websocket.receive_text()

            try:
                # Parse JSON data
                payload = json.loads(data)

                # Create database session
                async with AsyncSessionLocal() as db:
                    # Parse timestamp
                    timestamp_str = payload.get("timestamp")
                    if timestamp_str:
                        # Handle ISO format with Z suffix
                        timestamp_str = timestamp_str.replace("Z", "+00:00")
                        timestamp = datetime.fromisoformat(timestamp_str)
                    else:
                        timestamp = datetime.utcnow()

                    # Create motion data record
                    motion_data = MotionData(
                        device_id=device_id,
                        timestamp=timestamp,
                        ax=payload.get("ax", 0.0),
                        ay=payload.get("ay", 0.0),
                        az=payload.get("az", 0.0),
                        gx=payload.get("gx", 0.0),
                        gy=payload.get("gy", 0.0),
                        gz=payload.get("gz", 0.0),
                    )

                    # Run fall detection algorithm
                    is_fall, fall_type, confidence = fall_detector.detect(
                        ax=motion_data.ax,
                        ay=motion_data.ay,
                        az=motion_data.az,
                        gx=motion_data.gx,
                        gy=motion_data.gy,
                        gz=motion_data.gz,
                        device_id=device_id,
                    )

                    # Update motion data with fall detection results
                    motion_data.is_fall = is_fall
                    motion_data.fall_type = fall_type
                    motion_data.confidence = confidence

                    # Save to database
                    db.add(motion_data)
                    await db.commit()
                    await db.refresh(motion_data)

                    # If fall detected, create fall event
                    if is_fall:
                        await DataService.create_fall_event(
                            db=db,
                            device_id=device_id,
                            timestamp=timestamp,
                            peak_accel=fall_detector.get_peak_acceleration(device_id),
                            fall_type=fall_type,
                            confidence=confidence,
                        )

                    # Send acknowledgment back to ESP32
                    response = {
                        "status": "received",
                        "id": motion_data.id,
                        "is_fall": is_fall,
                        "timestamp": timestamp.isoformat(),
                    }
                    await websocket.send_json(response)

                    # Broadcast to other connected clients (optional)
                    await broadcast_motion_data(device_id, motion_data.to_dict())

            except json.JSONDecodeError as e:
                await websocket.send_json({"error": f"Invalid JSON: {str(e)}"})
            except Exception as e:
                print(f"[WS] Error processing data: {e}")
                await websocket.send_json({"error": str(e)})

    except WebSocketDisconnect:
        print(f"[WS] Device {device_id} disconnected")
    except Exception as e:
        print(f"[WS] Unexpected error for device {device_id}: {e}")
    finally:
        # Remove connection from active connections
        if device_id in active_connections:
            active_connections[device_id].remove(websocket)
            if not active_connections[device_id]:
                del active_connections[device_id]
                # Clean up fall detector buffer for this device
                fall_detector.cleanup_device(device_id)


async def broadcast_motion_data(device_id: str, data: dict):
    """Broadcast motion data to all connected clients for a device (except sender)."""
    if device_id in active_connections:
        for connection in active_connections[device_id]:
            try:
                await connection.send_json({"type": "motion_update", "data": data})
            except:
                pass  # Connection might be closed


@router.get("/ws/status")
async def websocket_status():
    """Get status of active WebSocket connections."""
    return {
        "active_devices": list(active_connections.keys()),
        "total_connections": sum(len(conns) for conns in active_connections.values()),
    }
