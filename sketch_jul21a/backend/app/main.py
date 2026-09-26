"""
FastAPI Backend for Fall Detection System
==========================================
Receives real-time motion data from ESP32 via WebSocket,
stores in PostgreSQL, and provides REST API for data management.
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.database import engine, Base
from app.routers import websocket, api


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events."""
    # Create tables on startup
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print("Database tables created/verified")
    yield
    # Cleanup on shutdown
    await engine.dispose()
    print("Database connections closed")


app = FastAPI(
    title="Fall Detection System API",
    description="Backend service for ESP32 fall detection system",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS middleware - allow frontend cross-origin access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure specific origins in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(websocket.router)
app.include_router(api.router, prefix="/api")


@app.get("/")
async def root():
    """Health check endpoint."""
    return {
        "status": "running",
        "service": "Fall Detection System API",
        "version": "1.0.0",
    }


@app.get("/health")
async def health_check():
    """Detailed health check."""
    return {
        "status": "healthy",
        "database": "connected",
    }
