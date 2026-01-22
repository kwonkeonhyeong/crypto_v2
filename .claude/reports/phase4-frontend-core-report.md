# Phase 4: Frontend Core - 구현 보고서

## 구현 요약
React 앱의 핵심 인프라(상태 관리, WebSocket, i18n, 테마)를 구축하였습니다.

## 구현된 기능

### 1. 타입 정의 (4개 파일)
- `prayer.ts`: Side, PrayerCount, PrayerRequest, PendingPrayer
- `websocket.ts`: ConnectionStatus, WebSocketState, StompMessage
- `ticker.ts`: Ticker 인터페이스
- `liquidation.ts`: LiquidationSide, Liquidation 인터페이스

### 2. Jotai 스토어 (5개 파일)
- `prayerStore.ts`: 기도 카운트 + 낙관적 업데이트 (localCountAtom)
- `websocketStore.ts`: 연결 상태 관리
- `themeStore.ts`: 테마 설정 + 시스템 테마 감지
- `soundStore.ts`: 사운드/BGM 설정 (localStorage 영속)
- `toastStore.ts`: 토스트 알림 시스템

### 3. 유틸리티 라이브러리 (2개 파일)
- `exponentialBackoff.ts`: 지수 백오프 재연결 알고리즘
- `stomp.ts`: STOMP 클라이언트 래퍼 (자동 재연결)

### 4. 훅 (3개 파일)
- `usePrayerSocket.ts`: WebSocket 연결, 배칭, 낙관적 업데이트
- `useTheme.ts`: 테마 전환 + 시스템 테마 감지
- `useToast.ts`: 토스트 표시 헬퍼

### 5. i18n (2개 로케일)
- `ko.json`: 한국어 번역
- `en.json`: 영어 번역

### 6. 레이아웃 컴포넌트 (4개 파일)
- `Layout.tsx`: 전체 레이아웃
- `Header.tsx`: 헤더 (연결 상태, 토글)
- `ThemeToggle.tsx`: 다크모드 토글
- `LanguageToggle.tsx`: 언어 전환

### 7. UI 컴포넌트 (2개 파일)
- `Toast.tsx`: 단일 토스트
- `ToastContainer.tsx`: 토스트 컨테이너

## 생성/수정된 파일

### 생성된 파일 (19개)
```
frontend/src/
├── types/
│   ├── prayer.ts
│   ├── websocket.ts
│   ├── ticker.ts
│   ├── liquidation.ts
│   └── index.ts
├── stores/
│   ├── prayerStore.ts
│   ├── websocketStore.ts
│   ├── themeStore.ts
│   ├── soundStore.ts
│   ├── toastStore.ts
│   └── index.ts
├── lib/
│   ├── exponentialBackoff.ts
│   ├── stomp.ts
│   └── index.ts
├── hooks/
│   ├── usePrayerSocket.ts
│   ├── useTheme.ts
│   ├── useToast.ts
│   └── index.ts
├── components/
│   ├── layout/
│   │   ├── Layout.tsx
│   │   ├── Header.tsx
│   │   ├── ThemeToggle.tsx
│   │   ├── LanguageToggle.tsx
│   │   └── index.ts
│   ├── ui/
│   │   ├── Toast.tsx
│   │   ├── ToastContainer.tsx
│   │   └── index.ts
│   └── index.ts
├── __tests__/
│   ├── setup.ts
│   ├── exponentialBackoff.test.ts
│   ├── prayerStore.test.ts
│   ├── themeStore.test.ts
│   └── toastStore.test.ts
├── vite-env.d.ts
└── vitest.config.ts
```

### 수정된 파일 (5개)
- `frontend/src/App.tsx`: Provider, Layout 통합
- `frontend/src/i18n/locales/ko.json`: 번역 확장
- `frontend/src/i18n/locales/en.json`: 번역 확장
- `frontend/vite.config.ts`: path alias 추가
- `frontend/eslint.config.js`: ESLint 9 설정

## 리뷰 필수 코드

### 보안
- `frontend/src/lib/stomp.ts:91-98`: WebSocket 메시지 JSON 파싱
  - 악의적인 JSON 페이로드 처리 검토 필요

### 성능
- `frontend/src/hooks/usePrayerSocket.ts:46-61`: 500ms 배칭 로직
  - 배치 타이머 중복 방지 및 메모리 누수 방지 확인
- `frontend/src/stores/prayerStore.ts:18-37`: localCountAtom 계산
  - pending 배열이 커질 경우 성능 영향 검토

### 정합성
- `frontend/src/hooks/usePrayerSocket.ts:72-118`: WebSocket 이벤트 핸들링
  - 연결 상태 전이 로직 검토
  - 재연결 시 구독 복구 로직 확인
- `frontend/src/lib/stomp.ts:63-80`: 구독 관리
  - 지연 구독 시 콜백 처리 로직 검토

## 테스트 현황

### 단위 테스트
- `exponentialBackoff.test.ts`: 5개 테스트 통과
- `prayerStore.test.ts`: 5개 테스트 통과
- `themeStore.test.ts`: 5개 테스트 통과
- `toastStore.test.ts`: 6개 테스트 통과

총 21개 테스트 통과

### 검증 명령어
```bash
cd frontend
npm run lint      # ESLint 검사
npx tsc --noEmit  # 타입 검사
npm test -- --run # 테스트 실행
npm run build     # 프로덕션 빌드
```

## 알려진 제한사항

1. **WebSocket 테스트 미구현**: usePrayerSocket 훅의 통합 테스트는 Phase 6에서 구현 예정
2. **E2E 테스트 미구현**: Playwright E2E 테스트는 Phase 6에서 구현 예정
3. **사운드 기능 미구현**: soundStore만 정의, 실제 사운드 재생은 Phase 5에서 구현

## 의존성

### 기존 의존성 활용
- jotai (2.10.3): 상태 관리
- @stomp/stompjs (7.0.0): STOMP 클라이언트
- react-i18next (15.1.3): 다국어 지원
- i18next-browser-languagedetector (8.0.2): 브라우저 언어 감지
- clsx (2.1.1): 조건부 클래스

### 추가 설치된 의존성
- @types/node: path 모듈 타입

## 다음 단계

Phase 5a로 진행하여 기도 버튼 및 게이지 바 UI 컴포넌트를 구현합니다.
