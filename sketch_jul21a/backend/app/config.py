"""
Application configuration using pydantic-settings.
Loads from environment variables or the .env file.
"""

from typing import Optional

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Database (SQLite by default; PostgreSQL via DATABASE_URL override)
    DATABASE_URL: str = "sqlite+aiosqlite:///./fall_detection.db"
    DATABASE_URL_SYNC: str = "sqlite:///./fall_detection.db"

    # Application
    APP_NAME: str = "Fall Detection System"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False

    # CORS — JSON array in .env, e.g. ["http://localhost:3000"]
    CORS_ORIGINS: list[str] = ["*"]

    # Fall detection parameters
    FALL_WINDOW_SIZE: int = 50  # 500ms at 100Hz
    FALL_ACCEL_THRESHOLD: float = 2.5  # g units
    FALL_GYRO_THRESHOLD: float = 300.0  # degrees/second
    FALL_SAMPLE_RATE: int = 100  # Hz

    # Data retention (optional)
    DATA_RETENTION_DAYS: Optional[int] = 365


settings = Settings()