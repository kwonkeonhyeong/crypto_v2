# Phase 5c: UI 사운드 & 모바일 구현 보고서

## 1. 구현 개요

Phase 5c에서는 사운드 시스템(Howler.js 기반)과 모바일 반응형 레이아웃을 구현하여 사용자 경험을 향상시켰습니다.

## 2. 구현 항목

### 2.1 사운드 시스템

| 파일 | 설명 |
|------|------|
| `frontend/src/lib/soundManager.ts` | Howler.js 래퍼, 싱글톤 매니저 |
| `frontend/src/hooks/useSound.ts` | 사운드 설정 관리 훅 |
| `frontend/src/hooks/useSoundEffect.ts` | 효과음 재생 훅 |
| `frontend/src/stores/soundStore.ts` | (기존) Jotai atoms |

**기능:**
- BGM 루프 재생 (volume 30%)
- 클릭 효과음 (volume 50%)
- 일반 청산 효과음 (volume 40%)
- 대형 청산 효과음 (volume 80%, $100,000 이상)
- localStorage 영속 (soundEnabled, bgmEnabled)

### 2.2 사운드 컨트롤 컴포넌트

| 파일 | 설명 |
|------|------|
| `frontend/src/components/sound/SoundToggle.tsx` | 효과음 ON/OFF 토글 |
| `frontend/src/components/sound/BgmToggle.tsx` | BGM ON/OFF 토글 |
| `frontend/src/components/sound/SoundControl.tsx` | 통합 컨트롤 패널 |
| `frontend/src/components/sound/index.ts` | 배럴 파일 |

### 2.3 모바일 시스템

| 파일 | 설명 |
|------|------|
| `frontend/src/hooks/useIsMobile.ts` | 모바일 디바이스 감지 훅 |
| `frontend/src/components/mobile/MobileLayout.tsx` | 반응형 레이아웃 래퍼 |
| `frontend/src/components/mobile/MobilePrayerButtons.tsx` | 모바일 전용 버튼 (하단 1/3) |
| `frontend/src/components/mobile/index.ts` | 배럴 파일 |

**모바일 기준:**
- 화면 너비 < 768px AND 터치 디바이스

### 2.4 CSS 업데이트

| 파일 | 변경 내용 |
|------|----------|
| `frontend/src/index.css` | Safe Area 지원, 터치 최적화 스타일 추가 |

### 2.5 기존 컴포넌트 수정

| 파일 | 변경 내용 |
|------|----------|
| `frontend/src/components/prayer/PrayerButtonPair.tsx` | 클릭 사운드 연동 |
| `frontend/src/components/layout/Header.tsx` | SoundControl 추가 |
| `frontend/src/pages/PrayerPage.tsx` | 청산 사운드 + MobileLayout 적용 |

### 2.6 인덱스 파일 업데이트

| 파일 | 변경 내용 |
|------|----------|
| `frontend/src/hooks/index.ts` | useSound, useSoundEffect, useIsMobile export 추가 |
| `frontend/src/components/index.ts` | sound, mobile export 추가 |

### 2.7 오디오 파일 구조

```
frontend/public/sounds/
├── .gitkeep          # 파일 목록 및 스펙 설명
├── bgm.mp3           # (추가 필요) 배경음악
├── click.mp3         # (추가 필요) 클릭 효과음
├── liquidation.mp3   # (추가 필요) 일반 청산 효과음
└── large-liquidation.mp3  # (추가 필요) 대형 청산 효과음
```

## 3. 리뷰 필수 코드

### 3.1 성능 관련

| 파일:라인 | 이유 |
|----------|------|
| `soundManager.ts:42` | Howl 인스턴스 생성 - 중복 등록 방지 로직 |
| `soundManager.ts:178-190` | 사운드 초기화 - 앱 시작 시 한 번만 호출되어야 함 |
| `useSound.ts:18-26` | useEffect 의존성 배열 - 무한 루프 방지 |

### 3.2 사용자 경험 관련

| 파일:라인 | 이유 |
|----------|------|
| `MobilePrayerButtons.tsx:50-55` | 터치 이벤트 처리 - 중복 호출 방지 |
| `MobilePrayerButtons.tsx:95-97` | onTouchStart preventDefault - 스크롤 방지 |
| `PrayerPage.tsx:44` | 대형 청산 판단 기준 - $100,000 |

### 3.3 브라우저 정책 관련

| 파일:라인 | 이유 |
|----------|------|
| `soundManager.ts:112-113` | BGM 재생 - 브라우저 자동 재생 정책 고려 필요 |

## 4. 테스트 현황

### 4.1 빌드 테스트
- ESLint: 통과
- TypeScript: 컴파일 성공
- Vite Build: 성공

### 4.2 수동 테스트 필요 항목
- [ ] 데스크탑에서 사운드 토글 동작 확인
- [ ] BGM 재생/정지 확인
- [ ] 클릭 사운드 재생 확인
- [ ] 청산 사운드 재생 확인
- [ ] 모바일에서 하단 버튼 영역 확인
- [ ] iOS Safe Area 적용 확인
- [ ] 터치 이벤트 반응성 확인

## 5. 알려진 제한사항

1. **오디오 파일 미포함**: 실제 mp3 파일은 배포 전 추가 필요
2. **브라우저 자동 재생 정책**: BGM은 사용자 인터랙션 후에만 재생 가능
3. **모바일 감지 기준**: 768px + 터치 디바이스 조합으로, 태블릿은 터치 모드로 동작

## 6. 다음 Phase 연계

Phase 6 (통합 테스트)에서 확인할 항목:
- 사운드 시스템 통합 테스트
- 모바일 반응형 E2E 테스트
- 브라우저 호환성 테스트
