#!/usr/bin/env python3
"""
5秒数据记录脚本
"""

import asyncio
import json
import csv
import os
import time
import aiosqlite

DATABASE_FILE = "motion_data.db"
RECORDINGS_DIR = "data/recordings"

async def record_5s(device_id="ESP32_001"):
    """执行5秒记录"""
    os.makedirs(RECORDINGS_DIR, exist_ok=True)

    start_time = int(time.time() * 1000)
    end_time = start_time + 5000

    print(f"开始记录5秒数据...")
    await asyncio.sleep(5)

    async with aiosqlite.connect(DATABASE_FILE) as db:
        cursor = await db.execute(
            "SELECT id, device_id, timestamp, ax, ay, az, gx, gy, gz, created_at FROM motion_data WHERE device_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY id ASC",
            (device_id, start_time, end_time)
        )
        rows = await cursor.fetchall()

    record_time = time.strftime("%Y%m%d_%H%M%S")
    filename = f"recording_{record_time}.csv"
    filepath = os.path.join(RECORDINGS_DIR, filename)

    with open(filepath, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "device_id", "timestamp", "ax", "ay", "az", "gx", "gy", "gz", "created_at"])
        for row in rows:
            writer.writerow(row)

    log_path = os.path.join(RECORDINGS_DIR, "record_log.json")
    if os.path.exists(log_path):
        with open(log_path, "r", encoding="utf-8") as f:
            log_data = json.load(f)
    else:
        log_data = {"recordings": []}

    record_entry = {
        "id": len(log_data["recordings"]) + 1,
        "filename": filename,
        "device_id": device_id,
        "record_time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "start_time": start_time,
        "end_time": end_time,
        "data_count": len(rows),
        "duration_ms": 5000
    }

    log_data["recordings"].append(record_entry)

    with open(log_path, "w", encoding="utf-8") as f:
        json.dump(log_data, f, ensure_ascii=False, indent=2)

    print(f"记录完成！")
    print(f"文件: {filename}")
    print(f"数据量: {len(rows)} 条")

    return {
        "status": "completed",
        "filename": filename,
        "record_time": record_entry["record_time"],
        "data_count": len(rows)
    }

if __name__ == "__main__":
    asyncio.run(record_5s())
