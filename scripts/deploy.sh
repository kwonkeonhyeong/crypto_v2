#!/bin/bash
set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "${GREEN}=== Crypto Prayer Deployment ===${NC}"

# 환경 변수 확인
if [ ! -f "$PROJECT_DIR/docker/.env" ]; then
    echo -e "${RED}Error: docker/.env file not found${NC}"
    echo "Copy docker/.env.example to docker/.env and configure it"
    exit 1
fi

# Git pull (선택사항)
if [ "$1" != "--no-pull" ]; then
    read -p "Pull latest changes from git? (y/n): " pull_git
    if [ "$pull_git" == "y" ]; then
        echo -e "${YELLOW}Pulling latest changes...${NC}"
        cd "$PROJECT_DIR"
        git pull origin main
    fi
fi

# Docker 이미지 빌드
echo -e "${YELLOW}Building Docker images...${NC}"
cd "$PROJECT_DIR/docker"
docker-compose -f docker-compose.prod.yml build --no-cache

# 기존 컨테이너 백업 (Redis 데이터)
echo -e "${YELLOW}Backing up Redis data...${NC}"
"$SCRIPT_DIR/backup.sh" || echo "No existing Redis container to backup"

# 컨테이너 재시작
echo -e "${YELLOW}Restarting containers...${NC}"
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d

# 헬스체크 대기
echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
sleep 10
"$SCRIPT_DIR/health-check.sh"

echo -e "${GREEN}=== Deployment completed! ===${NC}"
