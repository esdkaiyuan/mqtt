#!/bin/bash

# 数据库备份脚本

set -e

# 配置
BACKUP_DIR="./backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/fall_detection_$TIMESTAMP.sql"

# 创建备份目录
mkdir -p $BACKUP_DIR

echo "开始备份数据库..."

# 备份数据库
docker-compose exec -T postgres pg_dump -U fall_user -d fall_detection > $BACKUP_FILE

# 压缩备份文件
gzip $BACKUP_FILE

echo "备份完成: $BACKUP_FILE.gz"

# 清理30天前的备份
find $BACKUP_DIR -name "*.sql.gz" -mtime +30 -delete

echo "清理旧备份完成"
