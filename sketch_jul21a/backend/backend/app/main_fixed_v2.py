"""
简化版后端服务 - 使用SQLite
版本: 2.2.0 - 包含5秒记录功能
"""

import asyncio
import json
import csv
import io
import os
import time
from datetime import datetime
from contextlib import asynccontextmanager
from typing import Optional
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
import aiosqlite

DATABASE_FILE = "motion_data.db"
RECORDINGS_DIR = "data/recordings"

# 确保记录目录存在
os.makedirs(RECORDINGS_DIR, exist_ok=True)

# 初始化记录手册
LOG_FILE = os.path.join(RECORDINGS_DIR, "record_log.json")
if not os.path.exists(LOG_FILE):
    with open(LOG_FILE, 'w', encoding='utf-8') as f:
        json.dump({"recordings": []}, f, ensure_ascii=False)

@asynccontextmanager
async def lifespan(app: FastAPI):
    async with aiosqlite.connect(DATABASE_FILE) as db:
        await db.execute("""
            CREATE TABLE IF NOT EXISTS motion_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                ax REAL, ay REAL, az REAL,
                gx REAL, gy REAL, gz REAL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        await db.execute("CREATE INDEX IF NOT EXISTS idx_device ON motion_data(device_id)")
        await db.execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON motion_data(timestamp)")
        await db.commit()
    yield

app = FastAPI(title="数据采集服务", version="2.2.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.websocket("/ws/motion/{device_id}")
async def websocket_motion(websocket: WebSocket, device_id: str):
    await websocket.accept()
    print(f"设备已连接: {device_id}")
    try:
        while True:
            data = await websocket.receive_text()
            try:
                if data.startswith('['):
                    batch = json.loads(data)
                    async with aiosqlite.connect(DATABASE_FILE) as db:
                        for item in batch:
                            await db.execute(
                                "INSERT INTO motion_data (device_id, timestamp, ax, ay, az, gx, gy, gz) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                (item.get("device_id", device_id), item.get("timestamp", 0),
                                 item.get("ax", 0.0), item.get("ay", 0.0), item.get("az", 0.0),
                                 item.get("gx", 0.0), item.get("gy", 0.0), item.get("gz", 0.0))
                            )
                        await db.commit()
                    print(f"存储 {len(batch)} 个样本")
                else:
                    item = json.loads(data)
                    async with aiosqlite.connect(DATABASE_FILE) as db:
                        await db.execute(
                            "INSERT INTO motion_data (device_id, timestamp, ax, ay, az, gx, gy, gz) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            (item.get("device_id", device_id), item.get("timestamp", 0),
                             item.get("ax", 0.0), item.get("ay", 0.0), item.get("az", 0.0),
                             item.get("gx", 0.0), item.get("gy", 0.0), item.get("gz", 0.0))
                        )
                        await db.commit()
                await websocket.send_text(json.dumps({"status": "ok"}))
            except Exception as e:
                print(f"错误: {e}")
                await websocket.send_text(json.dumps({"status": "error", "message": str(e)}))
    except WebSocketDisconnect:
        print(f"设备已断开: {device_id}")

@app.get("/")
async def root():
    return {"service": "数据采集服务", "version": "2.2.0", "status": "running"}

@app.get("/health")
async def health():
    return {"status": "healthy"}

@app.get("/api/stats")
async def get_stats(device_id: Optional[str] = Query(None)):
    async with aiosqlite.connect(DATABASE_FILE) as db:
        if device_id:
            cursor = await db.execute(
                "SELECT COUNT(*), COUNT(DISTINCT device_id), MIN(created_at), MAX(created_at) FROM motion_data WHERE device_id = ?",
                (device_id,)
            )
        else:
            cursor = await db.execute(
                "SELECT COUNT(*), COUNT(DISTINCT device_id), MIN(created_at), MAX(created_at) FROM motion_data"
            )
        row = await cursor.fetchone()
        return {
            "total_records": row[0] or 0,
            "device_count": row[1] or 0,
            "first_record": row[2],
            "last_record": row[3]
        }

@app.get("/api/data")
async def get_data(
    page: int = Query(1, ge=1),
    page_size: int = Query(100, ge=1, le=1000),
    device_id: Optional[str] = Query(None),
    start_time: Optional[int] = Query(None),
    end_time: Optional[int] = Query(None)
):
    async with aiosqlite.connect(DATABASE_FILE) as db:
        where_clauses = []
        params = []
        if device_id:
            where_clauses.append("device_id = ?")
            params.append(device_id)
        if start_time:
            where_clauses.append("timestamp >= ?")
            params.append(start_time)
        if end_time:
            where_clauses.append("timestamp <= ?")
            params.append(end_time)
        where_sql = " WHERE " + " AND ".join(where_clauses) if where_clauses else ""
        cursor = await db.execute(f"SELECT COUNT(*) FROM motion_data{where_sql}", params)
        total = (await cursor.fetchone())[0]
        offset = (page - 1) * page_size
        cursor = await db.execute(
            f"SELECT id, device_id, timestamp, ax, ay, az, gx, gy, gz, created_at FROM motion_data{where_sql} ORDER BY id DESC LIMIT ? OFFSET ?",
            params + [page_size, offset]
        )
        rows = await cursor.fetchall()
        return {
            "data": [
                {"id": r[0], "device_id": r[1], "timestamp": r[2],
                 "ax": r[3], "ay": r[4], "az": r[5],
                 "gx": r[6], "gy": r[7], "gz": r[8], "created_at": r[9]}
                for r in rows
            ],
            "pagination": {
                "page": page, "page_size": page_size,
                "total_items": total,
                "total_pages": (total + page_size - 1) // page_size
            }
        }

@app.post("/api/clear")
async def clear_data(device_id: Optional[str] = Query(None)):
    async with aiosqlite.connect(DATABASE_FILE) as db:
        if device_id:
            cursor = await db.execute("DELETE FROM motion_data WHERE device_id = ?", (device_id,))
        else:
            cursor = await db.execute("DELETE FROM motion_data")
        deleted = cursor.rowcount
        await db.commit()
        return {"message": f"已清除 {deleted} 条数据", "deleted_count": deleted}

@app.post("/api/record")
async def record_5s(device_id: str = Query("ESP32_001")):
    """5秒数据记录，保存为单独的CSV文件"""
    start_time = int(time.time() * 1000)
    end_time = start_time + 5000

    # 等待5秒收集数据
    await asyncio.sleep(5)

    # 查询这5秒内的数据
    async with aiosqlite.connect(DATABASE_FILE) as db:
        cursor = await db.execute(
            "SELECT id, device_id, timestamp, ax, ay, az, gx, gy, gz, created_at FROM motion_data WHERE device_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY id ASC",
            (device_id, start_time, end_time)
        )
        rows = await cursor.fetchall()

    # 创建记录文件名
    record_time = time.strftime("%Y%m%d_%H%M%S")
    filename = f"recording_{record_time}.csv"
    filepath = os.path.join(RECORDINGS_DIR, filename)

    # 保存为CSV文件
    with open(filepath, 'w', newline='', encoding='
