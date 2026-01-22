#!/bin/bash
set -e

BACKUP_DIR="/var/backups/prayer"
DATE=$(date +%Y%m%d_%H%M%S)

# 백업 디렉토리 생성
mkdir -p "$BACKUP_DIR"

# Redis 컨테이너가 실행 중인지 확인
if ! docker ps --format '{{.Names}}' | grep -q 'prayer-redis'; then
    echo "Redis container is not running, skipping backup"
    exit 0
fi

echo "Creating Redis backup..."
docker exec prayer-redis redis-cli BGSAVE
sleep 2
docker cp prayer-redis:/data/dump.rdb "$BACKUP_DIR/redis_$DATE.rdb"

# 오래된 백업 삭제 (7일 이상)
find "$BACKUP_DIR" -name "*.rdb" -mtime +7 -delete 2>/dev/null || true

echo "Backup completed: $BACKUP_DIR/redis_$DATE.rdb"
