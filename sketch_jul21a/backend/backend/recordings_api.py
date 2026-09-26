#!/usr/bin/env python3
"""
简单的记录管理API
"""

import json
import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

RECORDINGS_DIR = "data/recordings"
LOG_FILE = os.path.join(RECORDINGS_DIR, "record_log.json")

@app.get("/api/recordings")
async def get_recordings():
    """获取所有记录列表"""
    if not os.path.exists(LOG_FILE):
        return {"recordings": []}

    with open(LOG_FILE, "r", encoding="utf-8") as f:
        log_data = json.load(f)

    return {"recordings": list(reversed(log_data["recordings"]))}

@app.get("/api/recordings/{filename}")
async def download_recording(filename: str):
    """下载记录文件"""
    filepath = os.path.join(RECORDINGS_DIR, filename)

    if not os.path.exists(filepath):
        return {"error": "文件不存在"}

    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    return StreamingResponse(
        iter([content]),
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename={filename}"}
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)
