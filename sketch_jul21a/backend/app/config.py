"""
Application configuration using pydantic-settings.
Loads from environment variables or .env file.
"""

from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """Application settings."""

    # Database (SQLite for local development)
    DATABASE_URL: str = "sqlite+aiosqlite:///./fall_detection.db"
    DATABASE_URL_SYNC: str = "sqlite:///./fall_detection.db"

    # Application
    APP_NAME: str = "Fall Detection System"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = True

    # CORS
    CORS_ORIGINS: list[str] = ["*"]

    # Fall detection parameters
    FALL_WINDOW_SIZE: int = 50  # 500ms at 100Hz = 50 samples
    FALL_ACCEL_THRESHOLD: float = 2.5  # g units
    FALL_GYRO_THRESHOLD: float = 300.0  # degrees/second
    FALL_SAMPLE_RATE: int = 100  # Hz

    # Data retention (optional)
    DATA_RETENTION_DAYS: Optional[int] = 365

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# Create global settings instance
settings = Settings()
