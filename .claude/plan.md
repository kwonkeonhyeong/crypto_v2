# 작업 현황

## 현재 진행 중인 Phase
- **Phase**: Phase 2a - Backend 기반
- **파일**: `.claude/plan/phase2a-backend-foundation.md`
- **시작일**: 2026-01-20

## 현재 작업 항목
- [ ] Redis 연결 및 카운터 로직 구현
- [ ] 도메인 모델 정의

## 최근 완료 항목
- [x] Phase 1 프로젝트 셋업 완료 (2026-01-20)
  - 모노레포 구조 생성 (backend + frontend + docker)
  - Backend: Spring Boot 3.4 + Java 21 설정
  - Frontend: Vite 6 + React 19 + TypeScript + Tailwind CSS
  - Docker Compose: Redis 7.x 로컬 개발 환경
  - GitHub 커밋 완료

## 다음 작업
- Phase 2a: Backend 기반 (Redis 연결, 도메인 모델)
- Phase 2b: Backend WebSocket & STOMP

## 블로커 / 이슈
- (없음)

## 컨텍스트 메모
- 헥사고날 아키텍처 패키지 구조 준비됨
- STOMP 프로토콜 사용 예정
- Rate Limit: 5회/초, 버스트 20회