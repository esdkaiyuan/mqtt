# Fall Detection System - FastAPI Backend

FastAPI backend service for real-time fall detection using ESP32 sensor data.

## Features

- **WebSocket Endpoint**: Real-time data reception from ESP32 devices
- **PostgreSQL Storage**: Async database operations with SQLAlchemy
- **REST API**: Data querying, annotation, export, and statistics
- **Fall Detection**: Real-time algorithm with sliding window analysis
- **CORS Support**: Cross-origin access for frontend

## Quick Start

### 1. Setup PostgreSQL

```bash
# Create database
psql -U postgres -c "CREATE DATABASE fall_detection;"
```

### 2. Configure Environment

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your database credentials
```

### 3. Install Dependencies

```bash
# Create virtual environment
python -m venv venv
source venv/bin/activate  # Linux/Mac
# or: venv\Scripts\activate  # Windows

# Install dependencies
pip install -r requirements.txt
```

### 4. Run Database Migrations

```bash
# Initialize Alembic (first time only)
alembic init alembic

# Create initial migration
alembic revision --autogenerate -m "Initial migration"

# Apply migration
alembic upgrade head
```

### 5. Start the Server

```bash
# Development mode (with auto-reload)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Or use the Makefile (if available)
make run
```

## API Documentation

Once running, access:
- **API Docs**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc
- **Health Check**: http://localhost:8000/health

## API Endpoints

### WebSocket

| Endpoint | Description |
|----------|-------------|
| `/ws/motion/{device_id}` | Receive real-time motion data from ESP32 |

**Data Format from ESP32:**
```json
{
  "timestamp": "2026-07-21T10:30:00.000Z",
  "ax": 0.12, "ay": 0.05, "az": 9.81,
  "gx": 1.23, "gy": -0.45, "gz": 0.67
}
```

### REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/data` | List motion data (paginated, filterable) |
| GET | `/api/data/{id}` | Get single record |
| POST | `/api/data/annotate` | Annotate data with fall type |
| GET | `/api/fall-events` | List fall events |
| GET | `/api/stats` | Get statistics |
| GET | `/api/export` | Export to CSV |

### Query Parameters

**GET /api/data:**
- `device_id`: Filter by device
- `start_time`: ISO format start time
- `end_time`: ISO format end time
- `is_fall`: Filter by fall status (true/false)
- `limit`: Records per page (default: 100, max: 10000)
- `offset`: Pagination offset

## Fall Detection Algorithm

**Detection Criteria:**
1. Acceleration magnitude > 2.5g
2. Angular velocity magnitude > 300°/s
3. Rapid deceleration after spike (impact → stillness)

**Configuration (in .env):**
- `FALL_WINDOW_SIZE=50` (500ms at 100Hz)
- `FALL_ACCEL_THRESHOLD=2.5` (g units)
- `FALL_GYRO_THRESHOLD=300.0` (°/s)

## Docker

### Build and Run

```bash
# Build image
docker build -t fall-detection-backend .

# Run container
docker run -p 8000:8000 \
  -e DATABASE_URL=postgresql+asyncpg://user:pass@host:5432/db \
  fall-detection-backend
```

### Docker Compose (with PostgreSQL)

Create `docker-compose.yml`:
```yaml
version: '3.8'
services:
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: fall_detection
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  backend:
    build: .
    environment:
      DATABASE_URL: postgresql+asyncpg://postgres:postgres@db:5432/fall_detection
    ports:
      - "8000:8000"
    depends_on:
      - db

volumes:
  postgres_data:
```

Run with:
```bash
docker-compose up -d
```

## Testing

```bash
# Test WebSocket connection
python -m websockets ws://localhost:8000/ws/motion/test-device

# Test REST API
curl http://localhost:8000/api/stats
curl http://localhost:8000/api/data?limit=10
```

## Project Structure

```
backend/
├── app/
│   ├── __init__.py
│   ├── main.py           # FastAPI application
│   ├── config.py          # Configuration
│   ├── database.py        # Database setup
│   ├── models.py          # SQLAlchemy models
│   ├── routers/
│   │   ├── __init__.py
│   │   ├── api.py         # REST endpoints
│   │   └── websocket.py   # WebSocket endpoint
│   └── services/
│       ├── __init__.py
│       ├── data_service.py    # Database operations
│       └── fall_detection.py  # Detection algorithm
├── alembic/
│   ├── env.py             # Alembic environment
│   └── script.py.mako     # Migration template
├── alembic.ini            # Alembic config
├── requirements.txt
├── Dockerfile
└── .env.example
```

## License

MIT
