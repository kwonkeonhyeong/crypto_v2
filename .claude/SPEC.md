# 📜 청산 기도 메타 - 완성된 기술 스펙 (v4.0)

## 1. 프로젝트 개요

### 1.1. 서비스 정의
실시간 암호화폐 시세 및 청산 데이터와 연동하여, 사용자가 **상승(Long)** 또는 **하락(Short)**을 기원하며 버튼을 클릭(기도)하는 대규모 동시성 참여형 웹 서비스.

### 1.2. 핵심 가치
- **엔터테인먼트:** 시장 변동성에 대한 투자자 심리를 '클릭'으로 해소
- **커뮤니티:** "지금 나와 같은 생각을 하는 사람이 N명 있다"는 실시간 동질감
- **긴박감:** 청산 이벤트가 화면 전체에 폭포처럼 쏟아지며 시장의 긴장감 전달

---

## 2. MVP 범위

### 2.1. 포함 기능 (v1.0)
- ✅ Up/Down 기도 버튼 (좌우 대칭 배치)
- ✅ 실시간 게이지 바 (RPM 기준, 수평 바)
- ✅ 전체 코인 청산 피드 (폭포 애니메이션 + 떠다니는 텍스트)
- ✅ BTC 실시간 시세 표시
- ✅ 사운드 효과 (ON/OFF 토글)
- ✅ 다크모드 (토글 지원)
- ✅ 한국어 + 영어 지원 (i18n)
- ✅ 모바일 동등 지원

### 2.2. v1.1 기능 (다음 버전)
- 🔜 실시간 채팅 (Redis 일정 기간 저장, Rate Limit + 길이 제한, 자동 닉네임)

---

## 3. 비기능 요구사항

| 항목 | 값 | 비고 |
|------|-----|------|
| 동시 접속 목표 | 100명 이하 | MVP 소규모 |
| 클릭 처리량 | 초당 5회/사용자 | 일반적 연타 속도 |
| 브로드캐스트 지연 | 500ms 이하 | 허용 지연 시간 |
| 클라이언트 배칭 | 500ms | 클릭 누적 후 전송 |
| 서버 브로드캐스트 | 200ms | 스케줄러 주기 |
| Rate Limit | 5회/초, 버스트 20회 | 세션 기준 |
| 최대 클릭/전송 | 20회 | 비정상 트래픽 방지 |
| Redis TTL | 48시간 | 기도 데이터 보존 |
| 데이터 리셋 | UTC 00:00 | 한국 시간 09:00 |

---

## 4. 기술 스택

### 4.1. 백엔드
| 영역 | 기술 | 상세 |
|------|------|------|
| Runtime | Java 21 (LTS) | Virtual Threads 활성화 |
| Framework | Spring Boot 3.4+ | WebSocket Native Handler |
| Architecture | 헥사고날 (Ports & Adapters) | 도메인 격리, 테스트 용이 |
| Database | Redis 7.x | 고속 카운터, TTL 관리 |
| Protocol | STOMP over WebSocket | Spring WebSocket Message Broker |

### 4.2. 프론트엔드
| 영역 | 기술 | 상세 |
|------|------|------|
| Framework | React 19 | useOptimistic, React Compiler |
| Build | Vite 6 | 빠른 HMR |
| State | Jotai | 아토믹 상태 관리 |
| Styling | Emotion | CSS-in-JS |
| Animation | Framer Motion | 폭포, 파티클, 흔들림 |
| i18n | react-i18next | 한국어 + 영어 |

### 4.3. 인프라 & 테스트
| 영역 | 기술 | 상세 |
|------|------|------|
| 배포 | AWS EC2 (t4g) | Docker Compose |
| E2E Test | Playwright | 모든 브라우저 지원 |
| Unit/Integration | JUnit 5, Jest | 핵심 로직 + WebSocket |
| Repo | 모노레포 | /backend + /frontend |

---

## 5. UI/UX 상세

### 5.1. 레이아웃 우선순위
```
1. 청산 피드 (전체 배경)
2. 기도 버튼 (좌우 대칭)
3. 게이지 바 (중앙 상단)
```

### 5.2. 색상 스키마
- **Up (상승):** 빨강 (#FF4444 계열)
- **Down (하락):** 파랑 (#4444FF 계열)
- **다크모드:** 기본 ON, 토글로 전환 가능

### 5.3. 청산 피드 시각 효과
- **폭포 애니메이션:** 청산이 화면 상단에서 하단으로 떨어지는 효과
- **떠다니는 텍스트:** 코인명, 방향, 금액이 화면을 가로질러 이동
- **화면 플래시/흔들림:** 대형 청산 시 화면 전체 효과
- **카운터 강조:** 청산 건수/금액 카운터 강조 표시
- **사운드:** 청산 발생 시 효과음 (토글로 ON/OFF)

### 5.4. 기도 버튼 효과
- **숫자 파티클:** +1, +5 등 숫자가 버튼에서 튀어나오는 효과
- **즉각적 반응:** useOptimistic으로 서버 응답 전 UI 즉시 업데이트

### 5.5. 청산 항목 정보
```
[코인아이콘][색상태그] BTC LONG $50,000
```
- 코인별 아이콘 + 색상 태그로 구분
- 방향 (LONG/SHORT)
- 청산 금액

### 5.6. 게이지 바
- **형태:** 수평 바 (좌측 Up, 우측 Down)
- **기준:** RPM (분당 클릭 수) 비율
- **실시간 업데이트:** 200ms마다 서버에서 수신

---

## 6. 데이터 흐름

### 6.1. Upstream (User → Server)
```
1. 사용자 클릭 → useOptimistic 즉시 반영
2. 500ms 버퍼에 클릭 누적
3. WebSocket으로 배치 전송: { type: "CLICK", payload: { side: "UP", cnt: 5 } }
4. 서버 Rate Limit 검증
5. Redis INCRBY로 카운터 증가
```

### 6.2. Downstream (Server → User)
```
1. 200ms 스케줄러 실행
2. Redis에서 Total Count 조회
3. RPM 계산 (현재 Total - 이전 Total)
4. 전체 세션에 브로드캐스트: { type: "TICKER", data: { up: 15000, uRpm: 500, ... } }
5. 클라이언트 UI 동기화
```

### 6.3. UI 불일치 처리
- **로컬 값 우선:** 자신의 클릭은 항상 반영된 것처럼 표시
- **전체 통계만 서버 동기화:** TICKER 수신 시 전체 카운트만 업데이트

---

## 7. 데이터 모델

### 7.1. Redis Key Strategy
```
prayer:{yyyyMMdd}:up     # 일별 Up 카운터, TTL 48h
prayer:{yyyyMMdd}:down   # 일별 Down 카운터, TTL 48h
```

### 7.2. WebSocket Payload

**Request (Client → Server)**
```json
{
  "type": "CLICK",
  "ver": 1,
  "payload": {
    "sid": "uuid-v4",
    "side": "UP",
    "cnt": 5
  }
}
```

**Response (Server → Client)**
```json
{
  "type": "TICKER",
  "ver": 1,
  "ts": 1705381234567,
  "data": {
    "up": 15000,
    "down": 12000,
    "uRpm": 500,
    "dRpm": 300,
    "btcPrice": 42150.50,
    "btcChange": 2.5
  }
}
```

**청산 알림 (Server → Client)**
```json
{
  "type": "LIQUIDATION",
  "ver": 1,
  "ts": 1705381234567,
  "data": {
    "symbol": "BTCUSDT",
    "side": "LONG",
    "amount": 50000,
    "price": 42100.00
  }
}
```

---

## 8. 세션 및 상태 관리

### 8.1. 세션 식별
- **방식:** 클라이언트 UUID 생성 (첫 방문 시)
- **저장:** sessionStorage (탭 닫기 전까지 유지)
- **용도:** Rate Limiting, 통계

### 8.2. 재연결 처리
- **WebSocket 끊김:** 조용한 백그라운드 재연결 (사용자 알림 없음)
- **바이낸스 연결 끊김:** 조용한 재연결, 기도 기능은 계속 동작

### 8.3. 오프라인 처리
- **클릭 비활성화:** 네트워크 단절 시 버튼 비활성화
- **상태 표시 없음:** 조용한 재연결 시도

---

## 9. 장애 대응

### 9.1. Redis 장애
- **전략:** 인메모리 폴백
- **동작:** 서버 메모리에서 임시 집계, 복구 시 Redis에 반영

### 9.2. 바이낸스 WebSocket 장애
- **전략:** 조용한 재연결
- **동작:** 청산 피드만 멈추고 기도 기능은 계속 동작

---

## 10. 외부 연동

### 10.1. 바이낸스 WebSocket 스트림
- **청산 데이터:** `wss://fstream.binance.com/ws/!forceOrder@arr`
- **시세 데이터:** `wss://fstream.binance.com/ws/btcusdt@ticker`

### 10.2. 수신 데이터 처리
- **청산:** 모든 코인 통합 표시, 코인별 아이콘/색상 구분
- **시세:** BTC만 표시

---

## 11. 프로젝트 구조

```
crypto_v2/
├── backend/
│   ├── src/main/java/
│   │   └── com/prayer/
│   │       ├── application/          # Use Cases
│   │       │   ├── port/
│   │       │   │   ├── in/           # Driving Ports
│   │       │   │   └── out/          # Driven Ports
│   │       │   └── service/          # Application Services
│   │       ├── domain/               # Domain Models
│   │       ├── adapter/
│   │       │   ├── in/
│   │       │   │   └── websocket/    # WebSocket Handlers
│   │       │   └── out/
│   │       │       ├── redis/        # Redis Adapters
│   │       │       └── binance/      # Binance WebSocket
│   │       └── config/               # Spring Configs
│   └── src/test/
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── stores/                   # Jotai atoms
│   │   ├── styles/
│   │   ├── i18n/
│   │   └── utils/
│   └── tests/                        # Playwright E2E
├── docker-compose.yml
└── README.md
```

---

## 12. MVP 구현 작업 목록

### Phase 1: 프로젝트 셋업
- [ ] 모노레포 구조 생성 (backend + frontend)
- [ ] Backend: Spring Boot 3.4 + Java 21 + Virtual Threads
- [ ] Frontend: Vite 6 + React 19 + TypeScript
- [ ] Docker Compose (Redis, 개발 환경)

### Phase 2: Backend Core
- [ ] 헥사고날 아키텍처 패키지 구조
- [ ] Redis 연결 및 카운터 로직 (INCRBY, MGET)
- [ ] WebSocket Handler + 세션 관리
- [ ] Rate Limiter (토큰 버킷)
- [ ] 200ms 브로드캐스터 스케줄러
- [ ] 인메모리 폴백 구현

### Phase 3: Binance 연동
- [ ] 바이낸스 청산 스트림 연결 (forceOrder)
- [ ] 바이낸스 시세 스트림 연결 (btcusdt@ticker)
- [ ] 재연결 로직

### Phase 4: Frontend Core
- [ ] Jotai 스토어 설정 (WebSocket, 사운드, 테마)
- [ ] WebSocket 연결 훅 (usePrayerSocket)
- [ ] i18n 설정 (한국어/영어)
- [ ] 다크모드 토글

### Phase 5: UI 컴포넌트
- [ ] 기도 버튼 (좌우 대칭, 숫자 파티클)
- [ ] 게이지 바 (수평, RPM 기준)
- [ ] 청산 피드 (폭포 애니메이션, 떠다니는 텍스트)
- [ ] BTC 시세 표시
- [ ] 사운드 토글
- [ ] 모바일 반응형

### Phase 6: 통합 및 테스트
- [ ] 전체 플로우 통합 테스트
- [ ] 단위 테스트 (Rate Limiter, 카운터)
- [ ] E2E 테스트 (Playwright)
- [ ] 부하 테스트 (100명 동시 접속)

### Phase 7: 배포
- [ ] AWS EC2 인스턴스 구성
- [ ] Docker Compose 배포
- [ ] 도메인/SSL 설정

---

## 13. 검증 방법

### 13.1. 기능 검증
1. Up/Down 버튼 클릭 → 숫자 파티클 + 게이지 변화 확인
2. 청산 발생 → 폭포 애니메이션 + 사운드 확인
3. 네트워크 끊김 → 버튼 비활성화 + 재연결 후 복구 확인
4. 다크모드 토글 → 테마 전환 확인
5. 언어 전환 → UI 텍스트 변경 확인

### 13.2. 성능 검증
1. 100명 동시 접속 시뮬레이션
2. 브로드캐스트 지연 500ms 이하 확인
3. 클릭 처리량 측정 (초당 500회 이상)

### 13.3. 장애 검증
1. Redis 중지 → 인메모리 폴백 동작 확인
2. 바이낸스 연결 끊김 → 기도 기능 지속 + 청산 피드 중단 확인
