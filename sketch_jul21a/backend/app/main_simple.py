"""
FastAPI 后端 - 原始数据采集服务
版本: 2.0.0

功能：
  1. 接收ESP32发来的原始传感器数据
  2. 存储到PostgreSQL数据库
  3. 提供REST API查询数据
  4. 导出CSV数据

去除：
  - 摔倒检测算法
  - 数据标注
  - 事件统计
"""

import asyncio
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Optional
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlalchemy import Column, BigInteger, Float, String, DateTime, create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
import json
import csv
import io
from fastapi.responses import StreamingResponse

# =============================================================================
# 配置
# =============================================================================

DATABASE_URL = "postgresql+asyncpg://fall_user:fall_password@localhost:5432/fall_detection"
DATABASE_URL_SYNC = "postgresql://fall_user:fall_password@localhost:5432/fall_detection"

# =============================================================================
# 数据库模型
# =============================================================================

Base = declarative_base()

class MotionData(Base):
    """原始运动数据表"""
    __tablename__ = "motion_data"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    device_id = Column(String(50), nullable=False, index=True)
    timestamp = Column(BigInteger, nullable=False)
    ax = Column(Float)
    ay = Column(Float)
    az = Column(Float)
    gx = Column(Float)
    gy = Column(Float)
    gz = Column(Float)
    created_at = Column(DateTime, default=datetime.utcnow)

# =============================================================================
# 数据库连接
# =============================================================================

# 异步引擎（用于FastAPI）
async_engine = create_async_engine(
    DATABASE_URL,
    echo=False,
    pool_size=20,
    max_overflow=10
)

async_session_factory = async_sessionmaker(
    async_engine,
    class_=AsyncSession,
    expire_on_commit=False
)

# 同步引擎（用于数据库初始化）
sync_engine = create_engine(DATABASE_URL_SYNC, echo=False)

# =============================================================================
# Pydantic 模型
# =============================================================================

class SensorData(BaseModel):
    """传感器数据"""
    device_id: str
    timestamp: int
    ax: float
    ay: float
    az: float
    gx: float
    gy: float
    gz: float

class DataResponse(BaseModel):
    """数据响应"""
    id: int
    device_id: str
    timestamp: int
    ax: float
    ay: float
    az: float
    gx: float
    gy: float
    gz: float
    created_at: datetime

class StatsResponse(BaseModel):
    """统计响应"""
    total_records: int
    device_count: int
    first_record: Optional[datetime]
    last_record: Optional[datetime]

# =============================================================================
# FastAPI 应用
# =============================================================================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期"""
    # 启动时初始化数据库
    async with async_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield

app = FastAPI(
    title="原始数据采集服务",
    version="2.0.0",
    description="ESP32-S3 + MPU6500 原始数据采集后端",
    lifespan=lifespan
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# =============================================================================
# WebSocket 连接管理
# =============================================================================

class ConnectionManager:
    def __init__(self):
        self.active_connections: dict[str, WebSocket] = {}

    async def connect(self, websocket: WebSocket, device_id: str):
        await websocket.accept()
        self.active_connections[device_id] = websocket
        print(f"设备已连接: {device_id}")

    def disconnect(self, device_id: str):
        if device_id in self.active_connections:
            del self.active_connections[device_id]
            print(f"设备已断开: {device_id}")

    async def send_message(self, device_id: str, message: str):
        if device_id in self.active_connections:
            await self.active_connections[device_id].send_text(message)

manager = ConnectionManager()

# =============================================================================
# WebSocket 端点
# =============================================================================

@app.websocket("/ws/motion/{device_id}")
async def websocket_motion(websocket: WebSocket, device_id: str):
    """接收ESP32的原始传感器数据"""
    await manager.connect(websocket, device_id)

    try:
        while True:
            # 接收数据
            data = await websocket.receive_text()

            try:
                # 解析JSON数据
                if data.startswith('['):
                    # 批量数据
                    batch = json.loads(data)
                    async with async_session_factory() as session:
                        for item in batch:
                            motion = MotionData(
                                device_id=item.get("device_id", device_id),
                                timestamp=item.get("timestamp", 0),
                                ax=item.get("ax", 0.0),
                                ay=item.get("ay", 0.0),
                                az=item.get("az", 0.0),
                                gx=item.get("gx", 0.0),
                                gy=item.get("gy", 0.0),
                                gz=item.get("gz", 0.0)
                            )
                            session.add(motion)
                        await session.commit()
                    print(f"存储 {len(batch)} 个样本，设备: {device_id}")
                else:
                    # 单条数据
                    item = json.loads(data)
                    async with async_session_factory() as session:
                        motion = MotionData(
                            device_id=item.get("device_id", device_id),
                            timestamp=item.get("timestamp", 0),
                            ax=item.get("ax", 0.0),
                            ay=item.get("ay", 0.0),
                            az=item.get("az", 0.0),
                            gx=item.get("gx", 0.0),
                            gy=item.get("gy", 0.0),
                            gz=item.get("gz", 0.0)
                        )
                        session.add(motion)
                        await session.commit()
                    print(f"存储 1 个样本，设备: {device_id}")

                # 确认收到数据
                await websocket.send_text(json.dumps({"status": "ok"}))

            except json.JSONDecodeError as e:
                print(f"JSON解析错误: {e}")
                await websocket.send_text(json.dumps({"status": "error", "message": "Invalid JSON"}))
            except Exception as e:
                print(f"存储错误: {e}")
                await websocket.send_text(json.dumps({"status": "error", "message": str(e)}))

    except WebSocketDisconnect:
        manager.disconnect(device_id)
    except Exception as e:
        print(f"WebSocket错误: {e}")
        manager.disconnect(device_id)

# =============================================================================
# REST API 端点
# =============================================================================

@app.get("/")
async def root():
    """根路径"""
    return {
        "service": "原始数据采集服务",
        "version": "2.0.0",
        "status": "running"
    }

@app.get("/health")
async def health():
    """健康检查"""
    return {"status": "healthy"}

@app.get("/api/stats")
async def get_stats(device_id: Optional[str] = Query(None)):
    """获取统计信息"""
    async with async_session_factory() as session:
        if device_id:
            from sqlalchemy import select, func
            result = await session.execute(
                select(
                    func.count(MotionData.id),
                    func.count(func.distinct(MotionData.device_id)),
                    func.min(MotionData.created_at),
                    func.max(MotionData.created_at)
                ).where(MotionData.device_id == device_id)
            )
        else:
            from sqlalchemy import select, func
            result = await session.execute(
                select(
                    func.count(MotionData.id),
                    func.count(func.distinct(MotionData.device_id)),
                    func.min(MotionData.created_at),
                    func.max(MotionData.created_at)
                )
            )

        row = result.first()
        return StatsResponse(
            total_records=row[0] or 0,
            device_count=row[1] or 0,
            first_record=row[2],
            last_record=row[3]
        )

@app.get("/api/data")
async def get_data(
    page: int = Query(1, ge=1),
    page_size: int = Query(100, ge=1, le=1000),
    device_id: Optional[str] = Query(None),
    start_time: Optional[int] = Query(None),
    end_time: Optional[int] = Query(None)
):
    """获取数据列表"""
    async with async_session_factory() as session:
        from sqlalchemy import select, func

        # 构建查询
        query = select(MotionData)
        count_query = select(func.count(MotionData.id))

        if device_id:
            query = query.where(MotionData.device_id == device_id)
            count_query = count_query.where(MotionData.device_id == device_id)

        if start_time:
            query = query.where(MotionData.timestamp >= start_time)
            count_query = count_query.where(MotionData.timestamp >= start_time)

        if end_time:
            query = query.where(MotionData.timestamp <= end_time)
            count_query = count_query.where(MotionData.timestamp <= end_time)

        # 获取总数
        total = await session.scalar(count_query)

        # 分页查询
        query = query.order_by(MotionData.id.desc())
        query = query.offset((page - 1) * page_size).limit(page_size)

        result = await session.execute(query)
        data = result.scalars().all()

        return {
            "data": [
                {
                    "id": d.id,
                    "device_id": d.device_id,
                    "timestamp": d.timestamp,
                    "ax": d.ax,
                    "ay": d.ay,
                    "az": d.az,
                    "gx": d.gx,
                    "gy": d.gy,
                    "gz": d.gz,
                    "created_at": d.created_at.isoformat()
                }
                for d in data
            ],
            "pagination": {
                "page": page,
                "page_size": page_size,
                "total_items": total,
                "total_pages": (total + page_size - 1) // page_size
            }
        }

@app.get("/api/data/{data_id}")
async def get_data_by_id(data_id: int):
    """获取单条数据"""
    async with async_session_factory() as session:
        from sqlalchemy import select
        result = await session.execute(
            select(MotionData).where(MotionData.id == data_id)
        )
        d = result.scalar_one_or_none()

        if not d:
            return {"error": "Data not found"}, 404

        return {
            "id": d.id,
            "device_id": d.device_id,
            "timestamp": d.timestamp,
            "ax": d.ax,
            "ay": d.ay,
            "az": d.az,
            "gx": d.gx,
            "gy": d.gy,
            "gz": d.gz,
            "created_at": d.created_at.isoformat()
        }

@app.get("/api/export")
async def export_csv(
    device_id: Optional[str] = Query(None),
    start_time: Optional[int] = Query(None),
    end_time: Optional[int] = Query(None),
    limit: int = Query(10000, ge=1, le=100000)
):
    """导出CSV数据"""
    async with async_session_factory() as session:
        from sqlalchemy import select

        query = select(MotionData)

        if device_id:
            query = query.where(MotionData.device_id == device_id)
        if start_time:
            query = query.where(MotionData.timestamp >= start_time)
        if end_time:
            query = query.where(MotionData.timestamp <= end_time)

        query = query.order_by(MotionData.id.asc()).limit(limit)

        result = await session.execute(query)
        data = result.scalars().all()

        # 生成CSV
        output = io.StringIO()
        writer = csv.writer(output)
        writer.writerow(["id", "device_id", "timestamp", "ax", "ay", "az", "gx", "gy", "gz", "created_at"])

        for d in data:
            writer.writerow([
                d.id, d.device_id, d.timestamp,
                d.ax, d.ay, d.az, d.gx, d.gy, d.gz,
                d.created_at.isoformat()
            ])

        output.seek(0)

        return StreamingResponse(
            iter([output.getvalue()]),
            media_type="text/csv",
            headers={
                "Content-Disposition": f"attachment; filename=motion_data_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
            }
        )

# =============================================================================
# 启动应用
# =============================================================================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
