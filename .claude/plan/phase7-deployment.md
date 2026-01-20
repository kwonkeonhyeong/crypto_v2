# Phase 7: 배포

## 목표
프로덕션 환경에 애플리케이션을 배포하고 모니터링을 설정한다.

## 선행 의존성
- Phase 6: 테스트 완료 (모든 테스트 통과)

## 범위
- Docker Compose 프로덕션 설정
- AWS EC2 배포
- Nginx 리버스 프록시 + SSL
- 도메인 설정
- 로그 및 기본 모니터링

---

## 디렉토리 구조

```
crypto_v2/
├── docker/
│   ├── docker-compose.yml          # 개발용
│   ├── docker-compose.prod.yml     # 프로덕션용
│   ├── nginx/
│   │   ├── nginx.conf
│   │   └── conf.d/
│   │       └── default.conf
│   └── .env.example
├── scripts/
│   ├── deploy.sh
│   ├── backup.sh
│   └── health-check.sh
├── backend/
│   └── Dockerfile
├── frontend/
│   ├── Dockerfile
│   └── nginx.conf
└── .github/
    └── workflows/
        └── deploy.yml
```

---

## 상세 구현 단계

### 7.1 프로덕션 Docker Compose

#### docker/docker-compose.prod.yml
```yaml
version: '3.8'

services:
  # Nginx 리버스 프록시
  nginx:
    image: nginx:1.25-alpine
    container_name: prayer-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./certbot/conf:/etc/letsencrypt:ro
      - ./certbot/www:/var/www/certbot:ro
    depends_on:
      - backend
      - frontend
    restart: always
    networks:
      - prayer-network

  # Certbot (SSL 인증서)
  certbot:
    image: certbot/certbot
    container_name: prayer-certbot
    volumes:
      - ./certbot/conf:/etc/letsencrypt
      - ./certbot/www:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done;'"
    restart: unless-stopped

  # Redis
  redis:
    image: redis:7.4-alpine
    container_name: prayer-redis
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: always
    networks:
      - prayer-network

  # Backend
  backend:
    build:
      context: ../backend
      dockerfile: Dockerfile
    container_name: prayer-backend
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_DATA_REDIS_PORT=6379
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    restart: always
    networks:
      - prayer-network

  # Frontend
  frontend:
    build:
      context: ../frontend
      dockerfile: Dockerfile
    container_name: prayer-frontend
    restart: always
    networks:
      - prayer-network

volumes:
  redis_data:

networks:
  prayer-network:
    driver: bridge
```

### 7.2 Nginx 설정

#### docker/nginx/nginx.conf
```nginx
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
    use epoll;
    multi_accept on;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';

    access_log /var/log/nginx/access.log main;

    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;

    # Gzip 압축
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css text/xml application/json application/javascript application/rss+xml application/atom+xml image/svg+xml;

    # Rate Limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    limit_conn_zone $binary_remote_addr zone=conn:10m;

    # WebSocket 업그레이드 맵
    map $http_upgrade $connection_upgrade {
        default upgrade;
        '' close;
    }

    include /etc/nginx/conf.d/*.conf;
}
```

#### docker/nginx/conf.d/default.conf
```nginx
upstream backend {
    server backend:8080;
    keepalive 32;
}

upstream frontend {
    server frontend:80;
}

server {
    listen 80;
    server_name prayer.example.com;  # 실제 도메인으로 변경

    # Let's Encrypt 인증
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # HTTPS 리다이렉트
    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name prayer.example.com;  # 실제 도메인으로 변경

    # SSL 인증서
    ssl_certificate /etc/letsencrypt/live/prayer.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/prayer.example.com/privkey.pem;

    # SSL 설정
    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:50m;
    ssl_session_tickets off;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;

    # HSTS
    add_header Strict-Transport-Security "max-age=63072000" always;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # WebSocket
    location /ws {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 7d;
        proxy_send_timeout 7d;
        proxy_read_timeout 7d;

        limit_conn conn 100;
    }

    # API 엔드포인트
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        limit_req zone=api burst=20 nodelay;
    }

    # Actuator (내부용)
    location /actuator/ {
        deny all;
        # 또는 특정 IP만 허용
        # allow 10.0.0.0/8;
        # deny all;
    }

    # Frontend (정적 파일)
    location / {
        proxy_pass http://frontend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # 캐싱
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
            proxy_pass http://frontend;
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }
}
```

### 7.3 Backend 프로덕션 설정

#### backend/src/main/resources/application-prod.yml
```yaml
spring:
  application:
    name: prayer-backend
  threads:
    virtual:
      enabled: true
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:redis}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5

server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized

logging:
  level:
    root: INFO
    com.crypto.prayer: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

binance:
  liquidation-stream-url: wss://fstream.binance.com/ws/!forceOrder@arr
  ticker-stream-url: wss://fstream.binance.com/ws/btcusdt@ticker
  reconnect-initial-delay-ms: 1000
  reconnect-max-delay-ms: 30000
```

### 7.4 Frontend 프로덕션 Nginx

#### frontend/nginx.conf
```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # Gzip
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

    # SPA 라우팅
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 정적 파일 캐싱
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # index.html은 캐시하지 않음
    location = /index.html {
        add_header Cache-Control "no-cache";
    }

    # 헬스체크
    location /health {
        return 200 'OK';
        add_header Content-Type text/plain;
    }
}
```

### 7.5 배포 스크립트

#### scripts/deploy.sh
```bash
#!/bin/bash
set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Crypto Prayer Deployment ===${NC}"

# 환경 변수 확인
if [ ! -f docker/.env ]; then
    echo -e "${RED}Error: docker/.env file not found${NC}"
    echo "Copy docker/.env.example to docker/.env and configure it"
    exit 1
fi

# Git pull (선택사항)
read -p "Pull latest changes from git? (y/n): " pull_git
if [ "$pull_git" == "y" ]; then
    echo -e "${YELLOW}Pulling latest changes...${NC}"
    git pull origin main
fi

# Docker 이미지 빌드
echo -e "${YELLOW}Building Docker images...${NC}"
cd docker
docker-compose -f docker-compose.prod.yml build --no-cache

# 기존 컨테이너 백업 (Redis 데이터)
echo -e "${YELLOW}Backing up Redis data...${NC}"
../scripts/backup.sh

# 컨테이너 재시작
echo -e "${YELLOW}Restarting containers...${NC}"
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d

# 헬스체크 대기
echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
sleep 10
../scripts/health-check.sh

echo -e "${GREEN}=== Deployment completed! ===${NC}"
```

#### scripts/backup.sh
```bash
#!/bin/bash
set -e

BACKUP_DIR="/var/backups/prayer"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

echo "Creating Redis backup..."
docker exec prayer-redis redis-cli BGSAVE
sleep 2
docker cp prayer-redis:/data/dump.rdb $BACKUP_DIR/redis_$DATE.rdb

# 오래된 백업 삭제 (7일 이상)
find $BACKUP_DIR -name "*.rdb" -mtime +7 -delete

echo "Backup completed: $BACKUP_DIR/redis_$DATE.rdb"
```

#### scripts/health-check.sh
```bash
#!/bin/bash

MAX_RETRIES=30
RETRY_INTERVAL=2

check_backend() {
    curl -sf http://localhost/actuator/health > /dev/null 2>&1
    return $?
}

check_frontend() {
    curl -sf http://localhost/ > /dev/null 2>&1
    return $?
}

check_redis() {
    docker exec prayer-redis redis-cli ping > /dev/null 2>&1
    return $?
}

echo "Checking service health..."

for i in $(seq 1 $MAX_RETRIES); do
    echo "Attempt $i/$MAX_RETRIES..."

    backend_ok=false
    frontend_ok=false
    redis_ok=false

    if check_backend; then
        echo "  ✓ Backend is healthy"
        backend_ok=true
    else
        echo "  ✗ Backend is not responding"
    fi

    if check_frontend; then
        echo "  ✓ Frontend is healthy"
        frontend_ok=true
    else
        echo "  ✗ Frontend is not responding"
    fi

    if check_redis; then
        echo "  ✓ Redis is healthy"
        redis_ok=true
    else
        echo "  ✗ Redis is not responding"
    fi

    if $backend_ok && $frontend_ok && $redis_ok; then
        echo ""
        echo "All services are healthy!"
        exit 0
    fi

    sleep $RETRY_INTERVAL
done

echo ""
echo "Health check failed after $MAX_RETRIES attempts"
exit 1
```

### 7.6 SSL 인증서 설정

#### scripts/init-ssl.sh
```bash
#!/bin/bash
set -e

DOMAIN=${1:-prayer.example.com}
EMAIL=${2:-admin@example.com}

echo "Initializing SSL certificate for $DOMAIN..."

# Certbot 디렉토리 생성
mkdir -p docker/certbot/conf
mkdir -p docker/certbot/www

# 더미 인증서 생성 (Nginx 시작용)
if [ ! -f "docker/certbot/conf/live/$DOMAIN/fullchain.pem" ]; then
    echo "Creating dummy certificate..."
    mkdir -p "docker/certbot/conf/live/$DOMAIN"
    openssl req -x509 -nodes -newkey rsa:4096 -days 1 \
        -keyout "docker/certbot/conf/live/$DOMAIN/privkey.pem" \
        -out "docker/certbot/conf/live/$DOMAIN/fullchain.pem" \
        -subj "/CN=$DOMAIN"
fi

# Nginx 시작
cd docker
docker-compose -f docker-compose.prod.yml up -d nginx

# Let's Encrypt 인증서 발급
docker-compose -f docker-compose.prod.yml run --rm certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email $EMAIL \
    --agree-tos \
    --no-eff-email \
    -d $DOMAIN

# Nginx 재시작
docker-compose -f docker-compose.prod.yml restart nginx

echo "SSL certificate initialized for $DOMAIN"
```

### 7.7 GitHub Actions CI/CD

#### .github/workflows/deploy.yml
```yaml
name: Deploy

on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      redis:
        image: redis:7.4-alpine
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Gradle
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}

      - name: Run Backend Tests
        working-directory: ./backend
        run: ./gradlew test

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '22'

      - name: Install pnpm
        run: npm install -g pnpm

      - name: Install Frontend Dependencies
        working-directory: ./frontend
        run: pnpm install

      - name: Run Frontend Tests
        working-directory: ./frontend
        run: pnpm test

      - name: Build Frontend
        working-directory: ./frontend
        run: pnpm build

  deploy:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'

    steps:
      - uses: actions/checkout@v4

      - name: Deploy to Server
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            cd /opt/crypto-prayer
            git pull origin main
            ./scripts/deploy.sh
```

### 7.8 환경 변수 예시

#### docker/.env.example
```bash
# Domain
DOMAIN=prayer.example.com
SSL_EMAIL=admin@example.com

# Redis
REDIS_PASSWORD=your_secure_password

# Backend
SPRING_PROFILES_ACTIVE=prod
JAVA_OPTS=-Xmx512m -Xms256m

# Logging
LOG_LEVEL=INFO
```

### 7.9 AWS EC2 설정 가이드

```bash
# 1. EC2 인스턴스 생성
# - AMI: Amazon Linux 2023 또는 Ubuntu 22.04
# - Instance Type: t4g.small (ARM) 또는 t3.small (x86)
# - Storage: 20GB SSD
# - Security Group:
#   - 22 (SSH)
#   - 80 (HTTP)
#   - 443 (HTTPS)

# 2. 서버 초기 설정
sudo yum update -y  # Amazon Linux
# 또는
sudo apt update && sudo apt upgrade -y  # Ubuntu

# 3. Docker 설치
sudo yum install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER

# 4. Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 5. Git 설치 및 프로젝트 클론
sudo yum install git -y
cd /opt
sudo git clone https://github.com/your-repo/crypto-prayer.git
sudo chown -R $USER:$USER crypto-prayer

# 6. 환경 설정
cd crypto-prayer/docker
cp .env.example .env
nano .env  # 설정 편집

# 7. SSL 인증서 발급
./scripts/init-ssl.sh your-domain.com your-email@example.com

# 8. 배포
./scripts/deploy.sh
```

---

## 아키텍처 다이어그램

```
                    Internet
                        │
                        ▼
                   ┌─────────┐
                   │  Route  │
                   │   53    │
                   └────┬────┘
                        │
                        ▼
              ┌─────────────────┐
              │    EC2 (t4g)    │
              │                 │
              │  ┌───────────┐  │
              │  │   Nginx   │  │
              │  │  (443/80) │  │
              │  └─────┬─────┘  │
              │        │        │
              │   ┌────┴────┐   │
              │   │         │   │
              │   ▼         ▼   │
              │ ┌─────┐ ┌─────┐ │
              │ │Back │ │Front│ │
              │ │end  │ │end  │ │
              │ │:8080│ │:80  │ │
              │ └──┬──┘ └─────┘ │
              │    │            │
              │    ▼            │
              │ ┌─────┐         │
              │ │Redis│         │
              │ │:6379│         │
              │ └─────┘         │
              │                 │
              └─────────────────┘
                        │
                        ▼
              ┌─────────────────┐
              │ Binance Futures │
              │   WebSocket     │
              └─────────────────┘
```

---

## 체크리스트

- [ ] Docker 설정
  - [ ] docker-compose.prod.yml
  - [ ] Backend Dockerfile
  - [ ] Frontend Dockerfile
- [ ] Nginx 설정
  - [ ] nginx.conf
  - [ ] 리버스 프록시 설정
  - [ ] SSL 설정
  - [ ] WebSocket 프록시
- [ ] 프로덕션 환경 설정
  - [ ] application-prod.yml
  - [ ] .env 파일
- [ ] SSL 인증서
  - [ ] Certbot 설정
  - [ ] 자동 갱신
- [ ] 스크립트
  - [ ] deploy.sh
  - [ ] backup.sh
  - [ ] health-check.sh
  - [ ] init-ssl.sh
- [ ] CI/CD
  - [ ] GitHub Actions 워크플로우
- [ ] AWS 인프라
  - [ ] EC2 인스턴스 생성
  - [ ] Security Group 설정
  - [ ] 도메인 연결
- [ ] 모니터링
  - [ ] 헬스체크 설정
  - [ ] 로그 수집

---

## 검증 명령어

```bash
# Docker 상태 확인
docker-compose -f docker/docker-compose.prod.yml ps

# 로그 확인
docker-compose -f docker/docker-compose.prod.yml logs -f

# 헬스체크
./scripts/health-check.sh

# SSL 인증서 상태
docker-compose -f docker/docker-compose.prod.yml run --rm certbot certificates

# 백업 확인
ls -la /var/backups/prayer/
```

---

## 롤백 절차

```bash
# 1. 이전 이미지로 롤백
docker-compose -f docker/docker-compose.prod.yml down
git checkout HEAD~1
docker-compose -f docker/docker-compose.prod.yml up -d

# 2. Redis 데이터 복원 (필요시)
docker cp /var/backups/prayer/redis_YYYYMMDD.rdb prayer-redis:/data/dump.rdb
docker restart prayer-redis
```

---

## 완료!

모든 Phase가 완료되면 다음과 같은 시스템이 구축됩니다:

1. **Backend**: Spring Boot 3.4 + Virtual Threads + WebSocket/STOMP
2. **Frontend**: React 19 + Vite 6 + Jotai + Framer Motion
3. **인프라**: Docker Compose + Nginx + Redis + Let's Encrypt
4. **실시간**: 바이낸스 Futures 청산/시세 스트림
5. **테스트**: JUnit 5 + Vitest + Playwright + k6
