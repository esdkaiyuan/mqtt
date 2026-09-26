#!/bin/bash

# 数据库恢复脚本

set -e

if [ -z "$1" ]; then
    echo "用法: $0 <backup_file.sql.gz>"
    echo "示例: $0 ./backups/fall_detection_20240101_120000.sql.gz"
    exit 1
fi

BACKUP_FILE=$1

if [ ! -f "$BACKUP_FILE" ]; then
    echo "错误: 备份文件不存在: $BACKUP_FILE"
    exit 1
fi

echo "开始恢复数据库..."
echo "备份文件: $BACKUP_FILE"

# 确认操作
read -p "确定要恢复数据库吗？这将覆盖现有数据 (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "操作已取消"
    exit 1
fi

# 解压备份文件
TEMP_FILE="/tmp/restore_$$.sql"
gunzip -c $BACKUP_FILE > $TEMP_FILE

# 恢复数据库
docker-compose exec -T postgres psql -U fall_user -d fall_detection < $TEMP_FILE

# 清理临时文件
rm $TEMP_FILE

echo "数据库恢复完成！"
