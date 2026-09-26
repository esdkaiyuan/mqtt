#!/usr/bin/env python3
"""
生成测试数据脚本
用于开发和测试

使用方法:
python generate_test_data.py [--count 1000] [--device ESP32_001]
"""

import asyncio
import json
import random
import time
import argparse
import websockets
from datetime import datetime

async def generate_fall_sequence(device_id: str, start_time: int):
    """生成一个摔倒序列"""
    data_points = []

    # 正常行走阶段 (50个点, 500ms)
    for i in range(50):
        timestamp = start_time + i * 10
        data_points.append({
            "device_id": device_id,
            "timestamp": timestamp,
            "ax": random.gauss(0, 0.1),
            "ay": random.gauss(0, 0.1),
            "az": random.gauss(9.8, 0.2),
            "gx": random.gauss(0, 5),
            "gy": random.gauss(0, 5),
            "gz": random.gauss(0, 5)
        })

    # 摔倒阶段 (20个点, 200ms) - 突然的加速度变化
    for i in range(20):
        timestamp = start_time + 500 + i * 10
        factor = i / 20.0
        data_points.append({
            "device_id": device_id,
            "timestamp": timestamp,
            "ax": random.gauss(2.0 * factor, 0.3),
            "ay": random.gauss(-1.5 * factor, 0.3),
            "az": random.gauss(5.0 - 4.0 * factor, 0.5),
            "gx": random.gauss(200 * factor, 30),
            "gy": random.gauss(-150 * factor, 30),
            "gz": random.gauss(100 * factor, 20)
        })

    # 撞击阶段 (10个点, 100ms) - 峰值加速度
    for i in range(10):
        timestamp = start_time + 700 + i * 10
        data_points.append({
            "device_id": device_id,
            "timestamp": timestamp,
            "ax": random.gauss(3.0, 0.5),
            "ay": random.gauss(-2.0, 0.5),
            "az": random.gauss(1.0, 0.3),
            "gx": random.gauss(350, 50),
            "gy": random.gauss(-250, 50),
            "gz": random.gauss(150, 30)
        })

    # 静止阶段 (30个点, 300ms) - 快速减小
    for i in range(30):
        timestamp = start_time + 800 + i * 10
        factor = 1.0 - (i / 30.0)
        data_points.append({
            "device_id": device_id,
            "timestamp": timestamp,
            "ax": random.gauss(0.2 * factor, 0.1),
            "ay": random.gauss(-0.1 * factor, 0.1),
            "az": random.gauss(9.8, 0.2),
            "gx": random.gauss(20 * factor, 5),
            "gy": random.gauss(-10 * factor, 5),
            "gz": random.gauss(5 * factor, 3)
        })

    return data_points

async def generate_normal_sequence(device_id: str, start_time: int, num_points: int):
    """生成正常运动序列"""
    data_points = []

    for i in range(num_points):
        timestamp = start_time + i * 10

        # 模拟正常行走
        walk_phase = (i % 60) / 60.0 * 2 * 3.14159

        data_points.append({
            "device_id": device_id,
            "timestamp": timestamp,
            "ax": random.gauss(0.1 * (1 + 0.5 * (i % 100) / 100), 0.15),
            "ay": random.gauss(0.05 * (1 + 0.3 * (i % 80) / 80), 0.1),
            "az": random.gauss(9.8 + 0.3 * (i % 50) / 50, 0.25),
            "gx": random.gauss(10 * (i % 40) / 40, 8),
            "gy": random.gauss(5 * (i % 30) / 30, 6),
            "gz": random.gauss(3 * (i % 20) / 20, 4)
        })

    return data_points

async def send_data(websocket, data_points, delay: float = 0.01):
    """发送数据点"""
    for point in data_points:
        await websocket.send(json.dumps(point))
        await asyncio.sleep(delay)

async def main():
    parser = argparse.ArgumentParser(description='生成摔倒检测测试数据')
    parser.add_argument('--count', type=int, default=1000, help='生成数据点数量')
    parser.add_argument('--device', type=str, default='ESP32_001', help='设备ID')
    parser.add_argument('--server', type=str, default='localhost:8000', help='服务器地址')
    parser.add_argument('--falls', type=int, default=5, help='摔倒事件数量')
    args = parser.parse_args()

    print(f"生成测试数据...")
    print(f"设备ID: {args.device}")
    print(f"数据点数量: {args.count}")
    print(f"摔倒事件: {args.falls}")
    print(f"服务器: {args.server}")

    try:
        uri = f"ws://{args.server}/ws/motion/{args.device}"
        async with websockets.connect(uri) as websocket:
            print(f"已连接到 {uri}")

            current_time = int(time.time() * 1000)
            points_sent = 0

            # 生成摔倒事件
            for i in range(args.falls):
                print(f"生成摔倒事件 {i + 1}/{args.falls}...")

                # 摔倒前的正常数据
                normal_points = await generate_normal_sequence(
                    args.device,
                    current_time,
                    random.randint(100, 300)
                )
                await send_data(websocket, normal_points, delay=0.005)
                points_sent += len(normal_points)
                current_time += len(normal_points) * 10

                # 摔倒序列
                fall_points = await generate_fall_sequence(args.device, current_time)
                await send_data(websocket, fall_points, delay=0.005)
                points_sent += len(fall_points)
                current_time += len(fall_points) * 10 + 1000  # 1秒间隔

                print(f"  发送 {len(fall_points)} 个摔倒数据点")

            # 生成剩余的正常数据
            remaining = args.count - points_sent
            if remaining > 0:
                print(f"生成正常数据 ({remaining} 个点)...")
                normal_points = await generate_normal_sequence(
                    args.device,
                    current_time,
                    remaining
                )
                await send_data(websocket, normal_points, delay=0.005)
                points_sent += len(normal_points)

            print(f"\n完成！共发送 {points_sent} 个数据点")

    except websockets.exceptions.ConnectionClosed:
        print("错误: 连接被关闭")
    except ConnectionRefusedError:
        print(f"错误: 无法连接到 {args.server}")
        print("请确保后端服务正在运行")
    except Exception as e:
        print(f"错误: {e}")

if __name__ == "__main__":
    asyncio.run(main())
