# Phase 5a: UI - 기도 버튼 & 게이지 완료 보고서

## 개요
Phase 5a에서는 사용자가 실제로 상호작용할 UI 컴포넌트들을 구현했습니다. 기도 버튼(Up/Down), 게이지 바, 카운터 디스플레이, 그리고 파티클 시스템이 포함됩니다.

## 구현된 기능

### 1. 파티클 시스템
버튼 클릭 시 시각적 피드백을 제공하는 파티클 효과입니다.

**구현 파일:**
- `frontend/src/stores/particleStore.ts` - 파티클 상태 관리 (Jotai atom)
- `frontend/src/hooks/useParticles.ts` - 파티클 생성 훅
- `frontend/src/components/prayer/NumberParticle.tsx` - 개별 파티클 컴포넌트
- `frontend/src/components/prayer/ParticleContainer.tsx` - 파티클 컨테이너

**동작 방식:**
- 클릭 위치에서 "+1" 텍스트가 생성
- Framer Motion으로 떠오르며 페이드아웃 (0.8초)
- 1초 후 자동 제거
- 랜덤 오프셋으로 연속 클릭 시 겹침 방지

### 2. 기도 버튼
사용자가 Up/Down 방향을 선택하는 메인 인터랙션 컴포넌트입니다.

**구현 파일:**
- `frontend/src/components/prayer/PrayerButton.tsx` - 개별 버튼
- `frontend/src/components/prayer/PrayerButtonPair.tsx` - 버튼 페어 배치

**기능:**
- 좌우 대칭 배치 (Up 좌측, Down 우측)
- 그라디언트 배경 (Up = 빨강, Down = 파랑)
- 호버/탭 애니메이션 (Framer Motion)
- 연결 끊김 시 비활성화
- 현재 카운트 표시 (i18n 지원)
- 반응형 크기 (모바일/태블릿/데스크탑)

### 3. 게이지 바
Up/Down 비율을 시각적으로 표시하는 수평 바입니다.

**구현 파일:**
- `frontend/src/components/gauge/GaugeBar.tsx` - 비율 게이지
- `frontend/src/components/gauge/RpmIndicator.tsx` - RPM 인디케이터

**기능:**
- RPM 비율 기준 너비 조절
- 부드러운 애니메이션 (0.3초)
- 퍼센트 및 RPM 수치 표시
- 다크모드 지원

### 4. 카운터 디스플레이
총 기도 수와 개별 Up/Down 카운트를 표시합니다.

**구현 파일:**
- `frontend/src/components/counter/AnimatedNumber.tsx` - 숫자 애니메이션
- `frontend/src/components/counter/CounterDisplay.tsx` - 카운터 레이아웃

**기능:**
- 숫자 변경 시 슬라이드 애니메이션
- 증가/감소 방향에 따른 다른 애니메이션
- 천 단위 구분자 표시

### 5. 페이지 통합
**구현 파일:**
- `frontend/src/pages/PrayerPage.tsx` - 메인 페이지
- `frontend/src/App.tsx` - 앱 루트 업데이트

**구조:**
```
PrayerPage
├── ParticleContainer (파티클 레이어)
├── CounterDisplay (총계 표시)
├── RpmIndicator (RPM 표시)
├── GaugeBar (비율 게이지)
└── PrayerButtonPair (Up/Down 버튼)
```

## 생성된 파일 목록

| 파일 경로 | 설명 |
|---------|------|
| `frontend/src/stores/particleStore.ts` | 파티클 상태 관리 |
| `frontend/src/hooks/useParticles.ts` | 파티클 생성 훅 |
| `frontend/src/components/prayer/NumberParticle.tsx` | 숫자 파티클 |
| `frontend/src/components/prayer/ParticleContainer.tsx` | 파티클 컨테이너 |
| `frontend/src/components/prayer/PrayerButton.tsx` | 기도 버튼 |
| `frontend/src/components/prayer/PrayerButtonPair.tsx` | 버튼 페어 |
| `frontend/src/components/prayer/index.ts` | Prayer 컴포넌트 배럴 |
| `frontend/src/components/gauge/GaugeBar.tsx` | 게이지 바 |
| `frontend/src/components/gauge/RpmIndicator.tsx` | RPM 인디케이터 |
| `frontend/src/components/gauge/index.ts` | Gauge 컴포넌트 배럴 |
| `frontend/src/components/counter/AnimatedNumber.tsx` | 애니메이션 숫자 |
| `frontend/src/components/counter/CounterDisplay.tsx` | 카운터 디스플레이 |
| `frontend/src/components/counter/index.ts` | Counter 컴포넌트 배럴 |
| `frontend/src/pages/PrayerPage.tsx` | 메인 페이지 |
| `frontend/src/pages/index.ts` | Pages 배럴 |

## 수정된 파일 목록

| 파일 경로 | 변경 내용 |
|---------|---------|
| `frontend/src/App.tsx` | PrayerPage 통합 |
| `frontend/src/hooks/index.ts` | useParticles 추가 |
| `frontend/src/stores/index.ts` | particleStore 추가 |
| `frontend/src/components/index.ts` | prayer, gauge, counter 추가 |

## 리뷰 필수 코드

### 성능 관련
| 파일:라인 | 설명 |
|---------|------|
| `particleStore.ts:29-31` | setTimeout을 사용한 파티클 자동 제거 - 대량 클릭 시 타이머 누적 가능성 확인 필요 |
| `AnimatedNumber.tsx:16-19` | useEffect 내 상태 비교 - 빠른 업데이트 시 불필요한 리렌더 가능성 |

### 사용자 경험 관련
| 파일:라인 | 설명 |
|---------|------|
| `PrayerButton.tsx:46-61` | 버튼 색상 스키마 - SPEC의 Up=빨강, Down=파랑 적용 확인 |
| `GaugeBar.tsx:17-25` | 비율 표시 - upRatio/downRatio 0.5 기본값 검증 |

## 기술적 결정

### 1. 색상 스키마
SPEC.md에 따라 Up = 빨강 (#FF4444 계열), Down = 파랑 (#4444FF 계열)을 적용했습니다.
- PrayerButton: Tailwind의 red-400~600, blue-400~600 그라디언트 사용
- GaugeBar: 동일한 색상 스키마 적용
- CounterDisplay: 텍스트 색상도 동일하게 적용

### 2. 파티클 라이프사이클
- 생성: 클릭 이벤트 → spawnParticle → addParticleAtom
- 애니메이션: Framer Motion (0.8초 duration)
- 제거: setTimeout 1초 후 자동 제거 (애니메이션 완료 여유 시간 확보)

### 3. 낙관적 업데이트 통합
- PrayerButtonPair에서 pray() 호출 시 pendingPrayersAtom에 추가
- localCountAtom이 serverCount + pending을 합산하여 즉각적 UI 반영
- 서버 응답 시 pendingPrayers 초기화

## 테스트 현황

### 빌드 테스트
```
npm run build - 성공
npm run lint - 성공
npm test - 21/21 통과
```

### 수동 테스트 권장 사항
1. Up/Down 버튼 클릭 → 파티클 생성 확인
2. 연속 클릭 → 파티클 겹침 방지 확인
3. 다크모드 전환 → 색상 적용 확인
4. 언어 전환 → 레이블 변경 확인
5. WebSocket 미연결 상태 → 버튼 비활성화 확인

## 제한사항

1. **테스트 커버리지**: UI 컴포넌트에 대한 단위 테스트가 추가되지 않았습니다. Phase 6에서 E2E 테스트로 커버 예정입니다.
2. **사운드**: 버튼 클릭 사운드는 Phase 5b에서 구현 예정입니다.
3. **청산 피드**: Phase 5b에서 구현 예정이며, 현재 PrayerPage에 플레이스홀더 주석으로 표시되어 있습니다.

## 다음 단계
Phase 5b: 청산 피드 & 효과 (폭포 애니메이션, BTC 시세 표시, 사운드 토글)
