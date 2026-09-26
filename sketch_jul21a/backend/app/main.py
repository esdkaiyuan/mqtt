"""
FastAPI Backend for Fall Detection System
==========================================
Receives real-time motion data from ESP32 over WebSocket, stores it in
PostgreSQL/SQLite, and exposes a REST API for data management.
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database import engine, Base
from app.routers import annotations, api, websocket


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Create tables on startup and dispose the pool on shutdown."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    await engine.dispose()


app = FastAPI(
    title=settings.APP_NAME,
    description="Backend service for ESP32 fall detection system",
    version=settings.APP_VERSION,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# WebSocket routes are absolute (/ws/...); REST routes live under /api.
app.include_router(websocket.router)
app.include_router(api.router, prefix="/api", tags=["data"])
app.include_router(annotations.router, prefix="/api")


@app.get("/", tags=["health"])
async def root():
    """Service metadata."""
    return {
        "status": "running",
        "service": settings.APP_NAME,
        "version": settings.APP_VERSION,
    }


@app.get("/health", tags=["health"])
async def health_check():
    """Liveness probe."""
    return {"status": "healthy"}
