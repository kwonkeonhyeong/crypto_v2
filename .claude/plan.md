# 작업 현황

## 현재 진행 중인 Phase
- **Phase**: 모든 Phase 완료
- **완료일**: 2026-01-20

## 완료된 작업 항목 (Phase 7)
- [x] Docker Compose 프로덕션 설정
  - [x] docker/docker-compose.prod.yml (Nginx, Certbot, Redis, Backend, Frontend)
- [x] Nginx 리버스 프록시 설정
  - [x] docker/nginx/nginx.conf
  - [x] docker/nginx/conf.d/default.conf
  - [x] WebSocket 프록시 (/ws)
  - [x] API Rate Limiting
  - [x] Security Headers
- [x] 프로덕션 환경 설정
  - [x] backend/src/main/resources/application-prod.yml
  - [x] docker/env.example
- [x] 배포 스크립트
  - [x] scripts/deploy.sh
  - [x] scripts/backup.sh
  - [x] scripts/health-check.sh
  - [x] scripts/init-ssl.sh
- [x] CI/CD
  - [x] .github/workflows/deploy.yml
- [x] Phase 7 보고서 작성
  - [x] .claude/reports/phase7-deployment-report.md
  - [x] .claude/reports/phase7-deployment-diagram.md

## 전체 완료 항목
- [x] Phase 1: 프로젝트 셋업 완료 (2026-01-20)
- [x] Phase 2a: Backend 기반 완료 (2026-01-20)
- [x] Phase 2b: Backend WebSocket & STOMP 완료 (2026-01-20)
- [x] Phase 3: Binance Integration 완료 (2026-01-20)
- [x] Phase 4: Frontend Core 완료 (2026-01-20)
- [x] Phase 5a: UI 기도 버튼 & 게이지 완료 (2026-01-20)
- [x] Phase 5b: UI 청산 피드 & 효과 완료 (2026-01-20)
- [x] Phase 5c: UI 사운드 & 모바일 완료 (2026-01-20)
- [x] Phase 6: 테스트 작성 완료 (2026-01-20)
- [x] Phase 7: 배포 설정 완료 (2026-01-20)

## 다음 작업 (v1.1)
- 실시간 채팅 기능
- 모니터링 시스템 (Prometheus + Grafana)
- 로그 수집 (ELK Stack)
- CDN 설정 (CloudFront)

## 블로커 / 이슈
- 실제 오디오 파일이 없음 (placeholder만 존재)
- 배포 전 오디오 파일 준비 필요

## 컨텍스트 메모
- 헥사고날 아키텍처 패키지 구조 완성
- STOMP 프로토콜 /app/prayer, /topic/prayer, /topic/ticker, /topic/liquidation
- Rate Limit: 5회/초, 버스트 20회 (TokenBucket)
- Redis 폴백: 인메모리 자동 전환 구현됨
- 브로드캐스트는 변경 감지 시에만 전송 (최적화)
- Frontend 상태 관리: Jotai 아토믹 패턴
- 클라이언트 배칭: 500ms 간격
- 낙관적 업데이트: localCountAtom = serverCount + pendingPrayers
- 테마: 시스템 테마 감지 + localStorage 영속
- i18n: react-i18next + 브라우저 언어 감지
- 바이낸스 WebSocket: Java 21 HttpClient 사용
- 재연결 전략: Exponential Backoff (1s, 2s, 4s, ... max 30s) + Jitter
- 청산 스트림: wss://fstream.binance.com/ws/!forceOrder@arr
- 시세 스트림: wss://fstream.binance.com/ws/btcusdt@ticker
- 대형 청산 기준: $100,000 이상
- UI 색상: Up = 빨강 (#FF4444), Down = 파랑 (#4444FF)
- Framer Motion: 버튼 펄스, 파티클 떠오름, 게이지 애니메이션
- 파티클: 클릭 위치에서 +1 텍스트가 1초간 떠오름
- 청산 피드: 화면 가로질러 떠다니는 애니메이션 (동적 fade-out)
- 대형 청산 효과: ScreenFlash + ScreenShake + 중앙 알림
- 동적 fade-out: 청산 빈도에 따라 3초~10초 자동 조절
- 사운드: Howler.js, 싱글톤 매니저, localStorage 영속
- 모바일: 768px + 터치 디바이스 기준, 하단 1/3 고정 버튼
- Safe Area: iOS 노치 대응, env(safe-area-inset-*)
- 테스트: Backend JUnit 5 + Testcontainers, Frontend Vitest + Testing Library
- 배포: Docker Compose + Nginx 리버스 프록시 + Let's Encrypt SSL
- CI/CD: GitHub Actions (test-backend, test-frontend, deploy)
