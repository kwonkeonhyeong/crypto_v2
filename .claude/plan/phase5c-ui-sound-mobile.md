# Phase 5c: UI - 사운드 & 모바일

## 목표
사운드 시스템(BGM + 효과음)과 모바일 반응형 UI를 구현한다.

## 선행 의존성
- Phase 5b: 청산 피드 완료

## 범위
- 사운드 시스템 (Howler.js)
  - BGM 루프
  - 클릭 효과음
  - 청산 효과음
  - 대형 청산 특별 효과음
- 사운드 토글 (ON/OFF)
- 모바일 반응형
  - 하단 1/3 기도 버튼 영역 고정
  - 터치 최적화
  - Safe Area 지원

---

## 디렉토리 구조

```
frontend/src/
├── components/
│   ├── sound/
│   │   ├── SoundControl.tsx
│   │   ├── BgmToggle.tsx
│   │   └── SoundToggle.tsx
│   └── mobile/
│       ├── MobileLayout.tsx
│       ├── MobilePrayerButtons.tsx
│       └── SwipeHint.tsx
├── hooks/
│   ├── useSound.ts
│   ├── useSoundEffect.ts
│   └── useIsMobile.ts
├── lib/
│   └── soundManager.ts
├── assets/
│   └── sounds/
│       ├── bgm.mp3
│       ├── click.mp3
│       ├── liquidation.mp3
│       └── large-liquidation.mp3
└── stores/
    └── soundStore.ts (Phase 4에서 생성됨)
```

---

## 상세 구현 단계

### 5c.1 사운드 매니저

#### lib/soundManager.ts
```typescript
import { Howl, Howler } from 'howler';

interface SoundConfig {
  src: string;
  volume?: number;
  loop?: boolean;
  sprite?: Record<string, [number, number]>;
}

class SoundManager {
  private sounds: Map<string, Howl> = new Map();
  private enabled = true;
  private bgmEnabled = false;

  constructor() {
    // 글로벌 볼륨 설정
    Howler.volume(0.7);
  }

  /**
   * 사운드 등록
   */
  register(name: string, config: SoundConfig): void {
    const sound = new Howl({
      src: [config.src],
      volume: config.volume ?? 1.0,
      loop: config.loop ?? false,
      sprite: config.sprite,
      html5: config.loop, // BGM은 HTML5 오디오 사용 (메모리 효율)
    });

    this.sounds.set(name, sound);
  }

  /**
   * 사운드 재생
   */
  play(name: string, spriteId?: string): number | undefined {
    if (!this.enabled) return;

    const sound = this.sounds.get(name);
    if (!sound) {
      console.warn(`Sound not found: ${name}`);
      return;
    }

    return sound.play(spriteId);
  }

  /**
   * BGM 재생
   */
  playBgm(): void {
    if (!this.bgmEnabled) return;

    const bgm = this.sounds.get('bgm');
    if (bgm && !bgm.playing()) {
      bgm.play();
    }
  }

  /**
   * BGM 정지
   */
  stopBgm(): void {
    const bgm = this.sounds.get('bgm');
    if (bgm) {
      bgm.stop();
    }
  }

  /**
   * 사운드 중지
   */
  stop(name: string): void {
    const sound = this.sounds.get(name);
    if (sound) {
      sound.stop();
    }
  }

  /**
   * 볼륨 조절
   */
  setVolume(name: string, volume: number): void {
    const sound = this.sounds.get(name);
    if (sound) {
      sound.volume(volume);
    }
  }

  /**
   * 글로벌 볼륨 조절
   */
  setGlobalVolume(volume: number): void {
    Howler.volume(volume);
  }

  /**
   * 사운드 활성화/비활성화
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;
    if (!enabled) {
      this.stopAll();
    }
  }

  /**
   * BGM 활성화/비활성화
   */
  setBgmEnabled(enabled: boolean): void {
    this.bgmEnabled = enabled;
    if (enabled) {
      this.playBgm();
    } else {
      this.stopBgm();
    }
  }

  /**
   * 모든 사운드 정지
   */
  stopAll(): void {
    this.sounds.forEach((sound) => sound.stop());
  }

  /**
   * 사운드 해제
   */
  unload(name: string): void {
    const sound = this.sounds.get(name);
    if (sound) {
      sound.unload();
      this.sounds.delete(name);
    }
  }

  /**
   * 모든 사운드 해제
   */
  unloadAll(): void {
    this.sounds.forEach((sound) => sound.unload());
    this.sounds.clear();
  }

  isEnabled(): boolean {
    return this.enabled;
  }

  isBgmEnabled(): boolean {
    return this.bgmEnabled;
  }
}

// 싱글톤 인스턴스
export const soundManager = new SoundManager();

// 초기 사운드 등록
export function initializeSounds(): void {
  soundManager.register('bgm', {
    src: '/sounds/bgm.mp3',
    volume: 0.3,
    loop: true,
  });

  soundManager.register('click', {
    src: '/sounds/click.mp3',
    volume: 0.5,
  });

  soundManager.register('liquidation', {
    src: '/sounds/liquidation.mp3',
    volume: 0.4,
  });

  soundManager.register('large-liquidation', {
    src: '/sounds/large-liquidation.mp3',
    volume: 0.8,
  });
}
```

### 5c.2 사운드 훅

#### hooks/useSound.ts
```typescript
import { useEffect } from 'react';
import { useAtom } from 'jotai';
import { soundEnabledAtom, bgmEnabledAtom } from '@/stores/soundStore';
import { soundManager, initializeSounds } from '@/lib/soundManager';

export function useSound() {
  const [soundEnabled, setSoundEnabled] = useAtom(soundEnabledAtom);
  const [bgmEnabled, setBgmEnabled] = useAtom(bgmEnabledAtom);

  // 초기화
  useEffect(() => {
    initializeSounds();

    // 초기 상태 동기화
    soundManager.setEnabled(soundEnabled);
    soundManager.setBgmEnabled(bgmEnabled);

    return () => {
      soundManager.unloadAll();
    };
  }, []);

  // 상태 변경 동기화
  useEffect(() => {
    soundManager.setEnabled(soundEnabled);
  }, [soundEnabled]);

  useEffect(() => {
    soundManager.setBgmEnabled(bgmEnabled);
  }, [bgmEnabled]);

  const toggleSound = () => {
    setSoundEnabled((prev) => !prev);
  };

  const toggleBgm = () => {
    setBgmEnabled((prev) => !prev);
  };

  return {
    soundEnabled,
    bgmEnabled,
    toggleSound,
    toggleBgm,
    setSoundEnabled,
    setBgmEnabled,
  };
}
```

#### hooks/useSoundEffect.ts
```typescript
import { useCallback } from 'react';
import { useAtomValue } from 'jotai';
import { soundEnabledAtom } from '@/stores/soundStore';
import { soundManager } from '@/lib/soundManager';

export function useSoundEffect() {
  const soundEnabled = useAtomValue(soundEnabledAtom);

  const playClick = useCallback(() => {
    if (soundEnabled) {
      soundManager.play('click');
    }
  }, [soundEnabled]);

  const playLiquidation = useCallback(() => {
    if (soundEnabled) {
      soundManager.play('liquidation');
    }
  }, [soundEnabled]);

  const playLargeLiquidation = useCallback(() => {
    if (soundEnabled) {
      soundManager.play('large-liquidation');
    }
  }, [soundEnabled]);

  return {
    playClick,
    playLiquidation,
    playLargeLiquidation,
  };
}
```

### 5c.3 사운드 컨트롤 컴포넌트

#### components/sound/SoundControl.tsx
```typescript
import { useSound } from '@/hooks/useSound';
import { SoundToggle } from './SoundToggle';
import { BgmToggle } from './BgmToggle';

export function SoundControl() {
  const { soundEnabled, bgmEnabled, toggleSound, toggleBgm } = useSound();

  return (
    <div className="flex items-center gap-2">
      <SoundToggle enabled={soundEnabled} onToggle={toggleSound} />
      <BgmToggle enabled={bgmEnabled} onToggle={toggleBgm} />
    </div>
  );
}
```

#### components/sound/SoundToggle.tsx
```typescript
interface SoundToggleProps {
  enabled: boolean;
  onToggle: () => void;
}

export function SoundToggle({ enabled, onToggle }: SoundToggleProps) {
  return (
    <button
      onClick={onToggle}
      className={`
        p-2 rounded-lg transition-colors
        ${enabled
          ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
          : 'bg-gray-100 dark:bg-gray-800 text-gray-400'
        }
      `}
      aria-label={enabled ? 'Mute sound' : 'Unmute sound'}
    >
      {enabled ? (
        <SoundOnIcon className="w-5 h-5" />
      ) : (
        <SoundOffIcon className="w-5 h-5" />
      )}
    </button>
  );
}

function SoundOnIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
    </svg>
  );
}

function SoundOffIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M17 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2" />
    </svg>
  );
}
```

#### components/sound/BgmToggle.tsx
```typescript
interface BgmToggleProps {
  enabled: boolean;
  onToggle: () => void;
}

export function BgmToggle({ enabled, onToggle }: BgmToggleProps) {
  return (
    <button
      onClick={onToggle}
      className={`
        p-2 rounded-lg transition-colors
        ${enabled
          ? 'bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400'
          : 'bg-gray-100 dark:bg-gray-800 text-gray-400'
        }
      `}
      aria-label={enabled ? 'Stop BGM' : 'Play BGM'}
    >
      {enabled ? (
        <MusicOnIcon className="w-5 h-5" />
      ) : (
        <MusicOffIcon className="w-5 h-5" />
      )}
    </button>
  );
}

function MusicOnIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
    </svg>
  );
}

function MusicOffIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M3 3l18 18" />
    </svg>
  );
}
```

### 5c.4 모바일 감지 훅

#### hooks/useIsMobile.ts
```typescript
import { useState, useEffect } from 'react';

export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkMobile = () => {
      // 화면 너비 또는 터치 지원으로 판단
      const isMobileScreen = window.innerWidth < 768;
      const isTouchDevice = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

      setIsMobile(isMobileScreen || isTouchDevice);
    };

    checkMobile();
    window.addEventListener('resize', checkMobile);

    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  return isMobile;
}
```

### 5c.5 모바일 레이아웃

#### components/mobile/MobileLayout.tsx
```typescript
import { ReactNode } from 'react';
import { useIsMobile } from '@/hooks/useIsMobile';
import { MobilePrayerButtons } from './MobilePrayerButtons';

interface MobileLayoutProps {
  children: ReactNode;
}

export function MobileLayout({ children }: MobileLayoutProps) {
  const isMobile = useIsMobile();

  if (!isMobile) {
    return <>{children}</>;
  }

  return (
    <div className="min-h-screen flex flex-col">
      {/* 상단 2/3: 콘텐츠 영역 */}
      <div className="flex-1 overflow-auto pb-safe">
        {children}
      </div>

      {/* 하단 1/3: 기도 버튼 고정 */}
      <MobilePrayerButtons />
    </div>
  );
}
```

#### components/mobile/MobilePrayerButtons.tsx
```typescript
import { useCallback } from 'react';
import { motion } from 'framer-motion';
import { useAtomValue } from 'jotai';
import { localCountAtom } from '@/stores/prayerStore';
import { isConnectedAtom } from '@/stores/websocketStore';
import { usePrayerSocket } from '@/hooks/usePrayerSocket';
import { useParticles } from '@/hooks/useParticles';
import { useSoundEffect } from '@/hooks/useSoundEffect';
import type { Side } from '@/types/prayer';

export function MobilePrayerButtons() {
  const count = useAtomValue(localCountAtom);
  const isConnected = useAtomValue(isConnectedAtom);
  const { pray } = usePrayerSocket();
  const { spawnParticle } = useParticles();
  const { playClick } = useSoundEffect();

  const handlePray = useCallback(
    (side: Side, event: React.TouchEvent | React.MouseEvent) => {
      if (!isConnected) return;

      // 터치/클릭 위치
      const touch = 'touches' in event ? event.touches[0] : event;
      const x = touch.clientX;
      const y = touch.clientY;

      spawnParticle(side, x, y);
      playClick();
      pray(side);
    },
    [isConnected, pray, spawnParticle, playClick]
  );

  return (
    <div
      className="
        fixed bottom-0 left-0 right-0
        h-1/3 min-h-[200px]
        bg-gradient-to-t from-black/20 to-transparent
        pb-safe
        z-40
      "
    >
      <div className="h-full grid grid-cols-2 gap-3 p-4">
        {/* 상승 버튼 */}
        <MobileButton
          side="up"
          count={count.upCount}
          disabled={!isConnected}
          onPress={handlePray}
        />

        {/* 하락 버튼 */}
        <MobileButton
          side="down"
          count={count.downCount}
          disabled={!isConnected}
          onPress={handlePray}
        />
      </div>
    </div>
  );
}

interface MobileButtonProps {
  side: Side;
  count: number;
  disabled: boolean;
  onPress: (side: Side, event: React.TouchEvent | React.MouseEvent) => void;
}

function MobileButton({ side, count, disabled, onPress }: MobileButtonProps) {
  const isUp = side === 'up';

  return (
    <motion.button
      className={`
        w-full h-full rounded-2xl
        flex flex-col items-center justify-center gap-1
        text-white font-bold
        active:scale-95 transition-transform
        touch-manipulation
        ${isUp
          ? 'bg-gradient-to-br from-green-400 to-green-600'
          : 'bg-gradient-to-br from-red-400 to-red-600'
        }
        ${disabled ? 'opacity-50' : ''}
      `}
      disabled={disabled}
      onTouchStart={(e) => onPress(side, e)}
      onClick={(e) => onPress(side, e)}
      whileTap={{ scale: 0.95 }}
    >
      <span className="text-4xl">{isUp ? '🚀' : '📉'}</span>
      <span className="text-lg">{isUp ? 'UP' : 'DOWN'}</span>
      <span className="text-2xl">{count.toLocaleString()}</span>
    </motion.button>
  );
}
```

### 5c.6 Safe Area CSS

#### index.css 추가
```css
/* Safe Area 지원 */
:root {
  --safe-area-inset-bottom: env(safe-area-inset-bottom, 0px);
}

.pb-safe {
  padding-bottom: var(--safe-area-inset-bottom);
}

/* 터치 최적화 */
.touch-manipulation {
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
}

/* 모바일에서 텍스트 선택 방지 */
@media (max-width: 768px) {
  .no-select {
    -webkit-user-select: none;
    user-select: none;
  }

  button {
    -webkit-tap-highlight-color: transparent;
  }
}

/* iOS 노치 대응 */
@supports (padding-top: env(safe-area-inset-top)) {
  .pt-safe {
    padding-top: env(safe-area-inset-top);
  }

  .pb-safe {
    padding-bottom: env(safe-area-inset-bottom);
  }
}
```

### 5c.7 기도 버튼에 사운드 연동

#### 수정된 PrayerButton.tsx (발췌)
```typescript
import { useSoundEffect } from '@/hooks/useSoundEffect';

export function PrayerButton({ side, count, disabled, onPray }: PrayerButtonProps) {
  const { playClick } = useSoundEffect();

  const handleClick = useCallback(
    (event: React.MouseEvent) => {
      if (disabled) return;

      playClick(); // 클릭 사운드 재생
      onPray(side, event);
    },
    [side, disabled, onPray, playClick]
  );

  // ... 나머지 코드
}
```

### 5c.8 청산에 사운드 연동

#### 수정된 usePrayerSocket (발췌)
```typescript
import { useSoundEffect } from '@/hooks/useSoundEffect';

export function usePrayerSocket(options: UsePrayerSocketOptions = {}) {
  const { playLiquidation, playLargeLiquidation } = useSoundEffect();

  // 청산 처리에 사운드 추가
  const handleLiquidation = useCallback((liquidation: Liquidation) => {
    if (liquidation.isLarge) {
      playLargeLiquidation();
    } else {
      playLiquidation();
    }
    options.onLiquidation?.(liquidation);
  }, [options.onLiquidation, playLiquidation, playLargeLiquidation]);

  // ... WebSocket 구독에서 handleLiquidation 사용
}
```

### 5c.9 반응형 레이아웃 업데이트

#### 수정된 PrayerPage.tsx
```typescript
import { useIsMobile } from '@/hooks/useIsMobile';
import { MobileLayout } from '@/components/mobile/MobileLayout';
// ... 기존 import

export function PrayerPage() {
  const isMobile = useIsMobile();

  // ... 기존 로직

  return (
    <MobileLayout>
      <ScreenShake active={isLargeEffect}>
        <div className={`
          min-h-screen pt-20
          ${isMobile ? 'pb-[33vh]' : 'pb-8'}
          flex flex-col items-center justify-center gap-6
        `}>
          {/* 배경 청산 피드 */}
          <LiquidationFeed />

          {/* 대형 청산 효과 */}
          <LargeLiquidationEffect />

          {/* 파티클 컨테이너 */}
          <ParticleContainer />

          {/* BTC 티커 */}
          <TickerDisplay />

          {/* 카운터 */}
          <CounterDisplay />

          {/* 게이지 바 */}
          <GaugeBar />

          {/* 데스크탑에서만 버튼 표시 (모바일은 MobileLayout에서 처리) */}
          {!isMobile && <PrayerButtonPair />}
        </div>
      </ScreenShake>
    </MobileLayout>
  );
}
```

### 5c.10 Header에 사운드 컨트롤 추가

#### 수정된 Header.tsx
```typescript
import { SoundControl } from '../sound/SoundControl';

export function Header() {
  // ... 기존 코드

  return (
    <header className="...">
      <div className="...">
        {/* 타이틀 */}
        <div>...</div>

        <div className="flex items-center gap-4">
          {/* 사운드 컨트롤 */}
          <SoundControl />

          {/* 연결 상태 */}
          <div className="...">...</div>

          {/* 테마 토글 */}
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
```

---

## 오디오 파일 목록

| 파일명 | 용도 | 추천 길이 | 볼륨 |
|--------|------|----------|------|
| `bgm.mp3` | 배경음악 | 2-3분 (루프) | 30% |
| `click.mp3` | 버튼 클릭 | 0.1-0.2초 | 50% |
| `liquidation.mp3` | 일반 청산 | 0.3-0.5초 | 40% |
| `large-liquidation.mp3` | 대형 청산 | 0.5-1초 | 80% |

---

## 모바일 브레이크포인트

| 화면 너비 | 레이아웃 |
|----------|---------|
| < 768px | 모바일 (하단 1/3 고정 버튼) |
| >= 768px | 데스크탑 (일반 레이아웃) |

---

## 체크리스트

- [ ] 사운드 시스템
  - [ ] soundManager (Howler.js)
  - [ ] initializeSounds
  - [ ] useSound 훅
  - [ ] useSoundEffect 훅
- [ ] 사운드 컨트롤 UI
  - [ ] SoundControl
  - [ ] SoundToggle
  - [ ] BgmToggle
- [ ] 사운드 연동
  - [ ] 클릭 사운드
  - [ ] 청산 사운드
  - [ ] 대형 청산 사운드
- [ ] 모바일 레이아웃
  - [ ] useIsMobile 훅
  - [ ] MobileLayout
  - [ ] MobilePrayerButtons (하단 1/3)
- [ ] CSS
  - [ ] Safe Area 지원
  - [ ] 터치 최적화
- [ ] 페이지 통합
  - [ ] Header에 사운드 컨트롤
  - [ ] 반응형 레이아웃
- [ ] 오디오 파일 준비
  - [ ] bgm.mp3
  - [ ] click.mp3
  - [ ] liquidation.mp3
  - [ ] large-liquidation.mp3
- [ ] 테스트
  - [ ] 사운드 재생 테스트
  - [ ] 모바일 레이아웃 테스트
  - [ ] 터치 이벤트 테스트

---

## 검증 명령어

```bash
# 개발 서버 실행
cd frontend && pnpm dev

# 모바일 테스트 (Chrome DevTools)
# 1. F12 → Device Mode → iPhone/Android 선택
# 2. 터치 이벤트 확인

# 사운드 파일 배치 확인
ls -la frontend/public/sounds/

# 빌드 테스트
pnpm build
```

---

## 다음 Phase
→ [Phase 6: 테스트](phase6-testing.md)
