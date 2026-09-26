"""
WebSocket connection registry.

Two independent groups are tracked per device:
- ``senders``  — the device itself (ESP32) pushing sensor data.
- ``viewers``  — frontend clients subscribed to a device's live stream.

Keeping them apart means broadcasts never echo back to the device that produced
the data, and a viewer can never inject samples into the database.
"""

from typing import Dict, List, Set

from fastapi import WebSocket


class ConnectionManager:
    """Tracks live WebSocket connections grouped by device."""

    def __init__(self) -> None:
        self._senders: Dict[str, List[WebSocket]] = {}
        self._viewers: Dict[str, List[WebSocket]] = {}

    def add_sender(self, device_id: str, websocket: WebSocket) -> None:
        self._senders.setdefault(device_id, []).append(websocket)

    def remove_sender(self, device_id: str, websocket: WebSocket) -> bool:
        """Remove a sender. Returns True when it was the device's last connection."""
        return self._remove(self._senders, device_id, websocket)

    def add_viewer(self, device_id: str, websocket: WebSocket) -> None:
        self._viewers.setdefault(device_id, []).append(websocket)

    def remove_viewer(self, device_id: str, websocket: WebSocket) -> bool:
        return self._remove(self._viewers, device_id, websocket)

    @staticmethod
    def _remove(registry: Dict[str, List[WebSocket]], device_id: str, websocket: WebSocket) -> bool:
        connections = registry.get(device_id)
        if not connections:
            return False
        if websocket in connections:
            connections.remove(websocket)
        if not connections:
            del registry[device_id]
            return True
        return False

    def is_device_online(self, device_id: str) -> bool:
        return device_id in self._senders

    def online_devices(self) -> Set[str]:
        return set(self._senders.keys())

    async def broadcast_to_viewers(self, device_id: str, message: dict) -> None:
        """Push a message to every viewer of a device, dropping dead sockets."""
        for websocket in list(self._viewers.get(device_id, [])):
            try:
                await websocket.send_json(message)
            except Exception:
                self._viewers[device_id].remove(websocket)

    def stats(self) -> dict:
        return {
            "active_devices": sorted(self._senders.keys()),
            "sender_connections": sum(len(c) for c in self._senders.values()),
            "viewer_connections": sum(len(c) for c in self._viewers.values()),
        }


manager = ConnectionManager()