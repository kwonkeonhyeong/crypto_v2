# Phase 7: 배포 설정 완료 보고서

## 개요
프로덕션 환경 배포를 위한 Docker, Nginx, CI/CD 설정을 완료했습니다.

## 구현 내용

### 1. Docker Compose 프로덕션 설정
**파일: `docker/docker-compose.prod.yml`**

구성된 서비스:
- **nginx**: 리버스 프록시 (80/443 포트)
- **certbot**: Let's Encrypt SSL 인증서 자동 갱신
- **redis**: 데이터 저장 (256MB 메모리 제한)
- **backend**: Spring Boot 애플리케이션
- **frontend**: React 정적 파일 서빙

### 2. Nginx 리버스 프록시 설정
**파일: `docker/nginx/nginx.conf`, `docker/nginx/conf.d/default.conf`**

기능:
- WebSocket 프록시 (`/ws` 경로)
- API Rate Limiting (10 req/s, 버스트 20)
- Gzip 압축
- 정적 파일 캐싱 (1년)
- HTTPS 리다이렉트 (SSL 설정 후)
- Security Headers (HSTS, X-Frame-Options 등)

### 3. 프로덕션 환경 설정
**파일: `backend/src/main/resources/application-prod.yml`**

설정:
- Redis 연결 풀 (max-active: 20)
- Graceful Shutdown
- Actuator 엔드포인트 (health, info, metrics)
- 로깅 레벨 (INFO)

### 4. 배포 스크립트
**파일: `scripts/deploy.sh`, `scripts/backup.sh`, `scripts/health-check.sh`, `scripts/init-ssl.sh`**

| 스크립트 | 기능 |
|---------|------|
| deploy.sh | 전체 배포 프로세스 자동화 |
| backup.sh | Redis 데이터 백업 (7일 보관) |
| health-check.sh | 서비스 상태 확인 (30회 재시도) |
| init-ssl.sh | Let's Encrypt SSL 인증서 발급 |

### 5. CI/CD 파이프라인
**파일: `.github/workflows/deploy.yml`**

워크플로우:
1. **test-backend**: Java 21, Gradle 빌드 및 테스트
2. **test-frontend**: Node.js 22, pnpm 빌드 및 테스트
3. **deploy**: main 브랜치 푸시 시 서버 자동 배포

## 생성된 파일 목록

```
docker/
├── docker-compose.prod.yml    # 프로덕션 Docker Compose
├── nginx/
│   ├── nginx.conf             # Nginx 전역 설정
│   └── conf.d/
│       └── default.conf       # 리버스 프록시 설정
└── env.example                # 환경 변수 템플릿

backend/src/main/resources/
└── application-prod.yml       # 프로덕션 환경 설정

scripts/
├── deploy.sh                  # 배포 스크립트
├── backup.sh                  # 백업 스크립트
├── health-check.sh            # 헬스체크 스크립트
└── init-ssl.sh                # SSL 초기화 스크립트

.github/workflows/
└── deploy.yml                 # CI/CD 워크플로우
```

## 리뷰 필수 코드

### 보안 관련
- `docker/nginx/conf.d/default.conf:1-120`: Nginx 프록시 설정, Rate Limiting, 보안 헤더
- `scripts/init-ssl.sh:1-50`: SSL 인증서 발급 프로세스

### 인프라 관련
- `docker/docker-compose.prod.yml:50-70`: Backend 컨테이너 헬스체크 설정
- `.github/workflows/deploy.yml:70-85`: 배포 Job SSH 연결 설정

## 배포 절차

### 1. EC2 인스턴스 준비
```bash
# Docker 설치
sudo yum install docker -y
sudo systemctl start docker && sudo systemctl enable docker

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 프로젝트 클론
cd /opt
sudo git clone <repository-url> crypto-prayer
sudo chown -R $USER:$USER crypto-prayer
```

### 2. 환경 설정
```bash
cd /opt/crypto-prayer/docker
cp env.example .env
# .env 파일 편집
```

### 3. SSL 인증서 발급 (선택)
```bash
./scripts/init-ssl.sh your-domain.com your-email@example.com
```

### 4. 배포
```bash
./scripts/deploy.sh
```

## GitHub Secrets 설정

| Secret | 설명 |
|--------|------|
| SERVER_HOST | EC2 퍼블릭 IP 또는 도메인 |
| SERVER_USER | SSH 사용자명 (ec2-user 등) |
| SERVER_SSH_KEY | SSH 프라이빗 키 |

## 알려진 제한사항

1. **SSL 설정**: 초기 배포 시 HTTP로 시작, SSL은 수동으로 활성화 필요
2. **로그 수집**: 기본 Docker 로깅만 사용, 별도 로그 수집 시스템 미구축
3. **모니터링**: Actuator 메트릭만 제공, Prometheus/Grafana 미구축

## 다음 단계 (v1.1)

1. 모니터링 시스템 (Prometheus + Grafana)
2. 로그 수집 (ELK Stack 또는 CloudWatch)
3. 자동 스케일링 (AWS Auto Scaling Group)
4. CDN 설정 (CloudFront)
