"""
Fall detection algorithm using sliding window analysis.

Detection criteria:
1. Acceleration magnitude > 2.5g
2. Angular velocity magnitude > 300°/s
3. Rapid deceleration after spike (impact followed by stillness)

Uses a sliding window of 500ms (50 samples at 100Hz).
"""

import numpy as np
from collections import deque
from typing import Tuple, Optional, Dict
from app.config import settings


class FallDetectionService:
    """Real-time fall detection using sliding window algorithm."""

    def __init__(self):
        # Sliding window buffer per device
        # Each buffer stores recent sensor readings
        self.accel_buffers: Dict[str, deque] = {}
        self.gyro_buffers: Dict[str, deque] = {}

        # Detection parameters from config
        self.window_size = settings.FALL_WINDOW_SIZE  # 50 samples
        self.accel_threshold = settings.FALL_ACCEL_THRESHOLD  # 2.5g
        self.gyro_threshold = settings.FALL_GYRO_THRESHOLD  # 300°/s

        # State tracking per device
        self.fall_states: Dict[str, dict] = {}

    def detect(
        self,
        ax: float, ay: float, az: float,
        gx: float, gy: float, gz: float,
        device_id: str,
    ) -> Tuple[bool, Optional[str], Optional[float]]:
        """
        Analyze sensor data for fall detection.

        Returns:
            Tuple of (is_fall, fall_type, confidence)
        """
        # Initialize buffers for new devices
        if device_id not in self.accel_buffers:
            self._init_device(device_id)

        # Calculate magnitudes
        accel_magnitude = np.sqrt(ax**2 + ay**2 + az**2)
        gyro_magnitude = np.sqrt(gx**2 + gy**2 + gz**2)

        # Add to sliding window
        self.accel_buffers[device_id].append(accel_magnitude)
        self.gyro_buffers[device_id].append(gyro_magnitude)

        # Need at least window_size samples to detect
        if len(self.accel_buffers[device_id]) < self.window_size:
            return False, None, None

        # Get recent window
        accel_window = np.array(list(self.accel_buffers[device_id]))
        gyro_window = np.array(list(self.gyro_buffers[device_id]))

        # Fall detection logic
        is_fall, fall_type, confidence = self._analyze_window(
            accel_window, gyro_window, device_id
        )

        # Update state
        if is_fall:
            self.fall_states[device_id] = {
                "is_falling": True,
                "peak_acceleration": float(np.max(accel_window)),
            }
        elif self.fall_states.get(device_id, {}).get("is_falling"):
            # Reset fall state after recovery
            if accel_magnitude < self.accel_threshold * 0.5:
                self.fall_states[device_id]["is_falling"] = False

        return is_fall, fall_type, confidence

    def _analyze_window(
        self,
        accel_window: np.ndarray,
        gyro_window: np.ndarray,
        device_id: str,
    ) -> Tuple[bool, Optional[str], Optional[float]]:
        """Analyze a window of sensor data for fall patterns."""

        # Find peak values in window
        peak_accel = np.max(accel_window)
        peak_gyro = np.max(gyro_window)

        # Condition 1: High acceleration spike
        has_accel_spike = peak_accel > self.accel_threshold

        # Condition 2: High angular velocity (rotation during fall)
        has_gyro_spike = peak_gyro > self.gyro_threshold

        # Condition 3: Rapid deceleration after spike (impact -> stillness)
        has_deceleration = self._check_deceleration_pattern(accel_window)

        # Fall is detected when conditions are met
        if has_accel_spike and (has_gyro_spike or has_deceleration):
            # Determine fall type based on acceleration components
            fall_type = self._classify_fall_type(accel_window)
            confidence = self._calculate_confidence(
                peak_accel, peak_gyro, has_deceleration
            )
            return True, fall_type, confidence

        return False, None, None

    def _check_deceleration_pattern(self, accel_window: np.ndarray) -> bool:
        """
        Check if there's a rapid deceleration pattern in the window.
        Pattern: spike followed by drop below normal gravity.
        """
        # Find the index of peak acceleration
        peak_idx = np.argmax(accel_window)

        # Peak should not be at the very end
        if peak_idx >= len(accel_window) - 5:
            return False

        # Check if acceleration drops significantly after peak
        post_peak = accel_window[peak_idx + 1:]
        if len(post_peak) < 5:
            return False

        # Calculate average after peak
        avg_post_peak = np.mean(post_peak)

        # Should drop below threshold * 0.6 (indicating stillness)
        return avg_post_peak < self.accel_threshold * 0.6

    def _classify_fall_type(self, accel_window: np.ndarray) -> str:
        """
        Classify the type of fall based on acceleration pattern.
        This is a simplified classification - can be enhanced with ML.
        """
        # For now, return generic fall type
        # In production, analyze ax/ay/az components separately
        peak_idx = np.argmax(accel_window)

        # Simple heuristic based on which axis has highest contribution
        # This would need per-axis data for proper implementation
        return "detected"  # Generic fall type

    def _calculate_confidence(
        self,
        peak_accel: float,
        peak_gyro: float,
        has_deceleration: bool,
    ) -> float:
        """Calculate confidence score for fall detection."""
        confidence = 0.0

        # Acceleration contribution (0-40%)
        accel_ratio = min(peak_accel / (self.accel_threshold * 2), 1.0)
        confidence += accel_ratio * 0.4

        # Gyroscope contribution (0-40%)
        gyro_ratio = min(peak_gyro / (self.gyro_threshold * 2), 1.0)
        confidence += gyro_ratio * 0.4

        # Deceleration pattern bonus (0-20%)
        if has_deceleration:
            confidence += 0.2

        return min(confidence, 1.0)

    def get_peak_acceleration(self, device_id: str) -> float:
        """Get the peak acceleration from the current window."""
        if device_id in self.accel_buffers and self.accel_buffers[device_id]:
            return float(max(self.accel_buffers[device_id]))
        return 0.0

    def _init_device(self, device_id: str):
        """Initialize buffers for a new device."""
        self.accel_buffers[device_id] = deque(maxlen=self.window_size)
        self.gyro_buffers[device_id] = deque(maxlen=self.window_size)
        self.fall_states[device_id] = {"is_falling": False, "peak_acceleration": 0.0}

    def cleanup_device(self, device_id: str):
        """Clean up buffers when device disconnects."""
        self.accel_buffers.pop(device_id, None)
        self.gyro_buffers.pop(device_id, None)
        self.fall_states.pop(device_id, None)

    def get_device_state(self, device_id: str) -> dict:
        """Get current state for a device."""
        return self.fall_states.get(device_id, {
            "is_falling": False,
            "peak_acceleration": 0.0,
        })
