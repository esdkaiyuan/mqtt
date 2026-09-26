#!/usr/bin/env python3
"""
更新后端代码，添加5秒记录功能
"""

import os

# 读取原文件
with open('backend/backend/app/main_fixed.py', 'r', encoding='utf-8') as f:
    content = f.read()

# 在文件末尾（if __name__之前）添加新功能
new_code = '''
@app.post("/api/record")
async def record_5s(device_id: str = Query("ESP32_001")):
    """5秒数据记录，保存为单独的CSV文件"""
    import asyncio
    import time
    import csv
    import json

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
    filepath = os.path.join("data", "recordings", filename)

    # 保存为CSV文件
    with open(filepath, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(["id", "device_id", "timestamp", "ax", "ay", "az", "gx", "gy", "gz", "created_at"])
        for row in rows:
            writer.writerow(row)

    # 更新记录手册
    log_path = os.path.join("data", "recordings", "record_log.json")
    with open(log_path, 'r', encoding='utf-8') as f:
        log_data = json.load(f)

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

    with open(log_path, 'w', encoding='utf-8') as f:
        json.dump(log_data, f, ensure_ascii=False, indent=2)

    return {
        "status": "completed",
        "device_id": device_id,
        "filename": filename,
        "record_time": record_entry["record_time"],
        "data_count": len(rows),
        "message": f"5秒记录已保存到 {filename}"
    }

@app.get("/api/recordings")
async def get_recordings():
    """获取所有记录列表"""
    log_path = os.path.join("data", "recordings", "record_log.json")
    with open(log_path, 'r', encoding='utf-8') as f:
        log_data = json.load(f)
    return {"recordings": list(reversed(log_data["recordings"]))}

@app.get("/api/recordings/{filename}")
async def download_recording(filename: str):
    """下载记录文件"""
    filepath = os.path.join("data", "recordings", filename)
    if not os.path.exists(filepath):
        return {"error": "文件不存在"}

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    return StreamingResponse(
        iter([content]),
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename={filename}"}
    )

'''

# 找到if __name__的位置并插入新代码
if 'if __name__ == "__main__":' in content:
    content = content.replace(
        'if __name__ == "__main__":',
        new_code + '\nif __name__ == "__main__":'
    )
    
    # 写入更新后的文件
    with open('backend/backend/app/main_fixed.py', 'w', encoding='utf-8') as f:
        f.write(content)
    
    print("后端代码已更新，添加了记录功能")
else:
    print("未找到插入点")
