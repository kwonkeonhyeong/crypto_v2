# Phase 5a: UI - 기도 버튼 & 게이지

## 목표
기도 버튼과 게이지 바 UI를 구현한다. 낙관적 업데이트와 연타 파티클 효과를 포함한다.

## 선행 의존성
- Phase 4: Frontend 코어 완료

## 범위
- PrayerButton 컴포넌트 (좌/우 대칭)
- Optimistic UI + 배칭
- Rate Limit 초과 시 토스트 + 롤백
- 숫자 파티클 (연타 시 개별 +1 다수)
- GaugeBar 컴포넌트 (RPM 비율 표시)
- 카운터 디스플레이

---

## 디렉토리 구조

```
frontend/src/
├── components/
│   ├── prayer/
│   │   ├── PrayerButton.tsx
│   │   ├── PrayerButtonPair.tsx
│   │   ├── NumberParticle.tsx
│   │   └── ParticleContainer.tsx
│   ├── gauge/
│   │   ├── GaugeBar.tsx
│   │   └── RpmIndicator.tsx
│   └── counter/
│       ├── CounterDisplay.tsx
│       └── AnimatedNumber.tsx
├── hooks/
│   └── useParticles.ts
└── stores/
    └── particleStore.ts
```

---

## 상세 구현 단계

### 5a.1 파티클 스토어

#### stores/particleStore.ts
```typescript
import { atom } from 'jotai';

export interface Particle {
  id: string;
  x: number;       // 클릭 위치 X
  y: number;       // 클릭 위치 Y
  value: number;   // 표시할 숫자 (+1)
  side: 'up' | 'down';
  createdAt: number;
}

export const particlesAtom = atom<Particle[]>([]);

// 파티클 추가
export const addParticleAtom = atom(
  null,
  (get, set, particle: Omit<Particle, 'id' | 'createdAt'>) => {
    const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const newParticle: Particle = {
      ...particle,
      id,
      createdAt: Date.now(),
    };

    set(particlesAtom, (prev) => [...prev, newParticle]);

    // 1초 후 자동 제거
    setTimeout(() => {
      set(particlesAtom, (prev) => prev.filter((p) => p.id !== id));
    }, 1000);
  }
);

// 파티클 제거
export const removeParticleAtom = atom(null, (get, set, id: string) => {
  set(particlesAtom, (prev) => prev.filter((p) => p.id !== id));
});
```

### 5a.2 파티클 훅

#### hooks/useParticles.ts
```typescript
import { useCallback } from 'react';
import { useSetAtom } from 'jotai';
import { addParticleAtom } from '@/stores/particleStore';
import type { Side } from '@/types/prayer';

export function useParticles() {
  const addParticle = useSetAtom(addParticleAtom);

  const spawnParticle = useCallback(
    (side: Side, x: number, y: number) => {
      // 랜덤 오프셋으로 겹침 방지
      const offsetX = (Math.random() - 0.5) * 60;
      const offsetY = (Math.random() - 0.5) * 40;

      addParticle({
        x: x + offsetX,
        y: y + offsetY,
        value: 1,
        side,
      });
    },
    [addParticle]
  );

  return { spawnParticle };
}
```

### 5a.3 파티클 컴포넌트

#### components/prayer/NumberParticle.tsx
```typescript
import { motion } from 'framer-motion';
import type { Particle } from '@/stores/particleStore';

interface NumberParticleProps {
  particle: Particle;
}

export function NumberParticle({ particle }: NumberParticleProps) {
  const color = particle.side === 'up' ? 'text-green-500' : 'text-red-500';

  return (
    <motion.div
      className={`fixed pointer-events-none font-bold text-2xl ${color}`}
      style={{
        left: particle.x,
        top: particle.y,
        zIndex: 100,
      }}
      initial={{
        opacity: 1,
        scale: 0.5,
        y: 0,
      }}
      animate={{
        opacity: 0,
        scale: 1.5,
        y: -80,
      }}
      transition={{
        duration: 0.8,
        ease: 'easeOut',
      }}
    >
      +{particle.value}
    </motion.div>
  );
}
```

#### components/prayer/ParticleContainer.tsx
```typescript
import { useAtomValue } from 'jotai';
import { particlesAtom } from '@/stores/particleStore';
import { NumberParticle } from './NumberParticle';

export function ParticleContainer() {
  const particles = useAtomValue(particlesAtom);

  return (
    <div className="fixed inset-0 pointer-events-none z-50">
      {particles.map((particle) => (
        <NumberParticle key={particle.id} particle={particle} />
      ))}
    </div>
  );
}
```

### 5a.4 기도 버튼

#### components/prayer/PrayerButton.tsx
```typescript
import { useCallback, useRef } from 'react';
import { motion, useAnimation } from 'framer-motion';
import { clsx } from 'clsx';
import { useTranslation } from 'react-i18next';
import type { Side } from '@/types/prayer';

interface PrayerButtonProps {
  side: Side;
  count: number;
  disabled?: boolean;
  onPray: (side: Side, event: React.MouseEvent) => void;
}

export function PrayerButton({
  side,
  count,
  disabled = false,
  onPray,
}: PrayerButtonProps) {
  const { t } = useTranslation();
  const controls = useAnimation();
  const buttonRef = useRef<HTMLButtonElement>(null);

  const isUp = side === 'up';

  const handleClick = useCallback(
    (event: React.MouseEvent) => {
      if (disabled) return;

      // 버튼 펄스 애니메이션
      controls.start({
        scale: [1, 0.95, 1],
        transition: { duration: 0.15 },
      });

      onPray(side, event);
    },
    [side, disabled, onPray, controls]
  );

  return (
    <motion.button
      ref={buttonRef}
      animate={controls}
      onClick={handleClick}
      disabled={disabled}
      className={clsx(
        'relative w-full h-32 md:h-40 lg:h-48',
        'rounded-2xl font-bold text-white',
        'transition-all duration-200',
        'flex flex-col items-center justify-center gap-2',
        'shadow-lg active:shadow-md',
        {
          'bg-gradient-to-br from-green-400 to-green-600 hover:from-green-500 hover:to-green-700':
            isUp && !disabled,
          'bg-gradient-to-br from-red-400 to-red-600 hover:from-red-500 hover:to-red-700':
            !isUp && !disabled,
          'bg-gray-400 cursor-not-allowed opacity-50': disabled,
        }
      )}
      whileHover={!disabled ? { scale: 1.02 } : undefined}
      whileTap={!disabled ? { scale: 0.98 } : undefined}
    >
      {/* 아이콘 */}
      <div className="text-4xl md:text-5xl">
        {isUp ? '🚀' : '📉'}
      </div>

      {/* 레이블 */}
      <div className="text-lg md:text-xl font-semibold">
        {t(`prayer.${side}`)}
      </div>

      {/* 카운트 */}
      <div className="text-2xl md:text-3xl font-bold">
        {count.toLocaleString()}
      </div>

      {/* 글로우 효과 */}
      <div
        className={clsx(
          'absolute inset-0 rounded-2xl opacity-0 transition-opacity',
          'hover:opacity-30',
          {
            'bg-green-300': isUp,
            'bg-red-300': !isUp,
          }
        )}
      />
    </motion.button>
  );
}
```

#### components/prayer/PrayerButtonPair.tsx
```typescript
import { useCallback } from 'react';
import { useAtomValue } from 'jotai';
import { localCountAtom } from '@/stores/prayerStore';
import { isConnectedAtom } from '@/stores/websocketStore';
import { usePrayerSocket } from '@/hooks/usePrayerSocket';
import { useParticles } from '@/hooks/useParticles';
import { PrayerButton } from './PrayerButton';
import type { Side } from '@/types/prayer';

export function PrayerButtonPair() {
  const count = useAtomValue(localCountAtom);
  const isConnected = useAtomValue(isConnectedAtom);
  const { pray } = usePrayerSocket();
  const { spawnParticle } = useParticles();

  const handlePray = useCallback(
    (side: Side, event: React.MouseEvent) => {
      // 클릭 위치에서 파티클 생성
      const rect = (event.target as HTMLElement).getBoundingClientRect();
      const x = event.clientX;
      const y = event.clientY;

      spawnParticle(side, x, y);
      pray(side);
    },
    [pray, spawnParticle]
  );

  return (
    <div className="grid grid-cols-2 gap-4 md:gap-6 lg:gap-8 w-full max-w-2xl mx-auto px-4">
      <PrayerButton
        side="up"
        count={count.upCount}
        disabled={!isConnected}
        onPray={handlePray}
      />
      <PrayerButton
        side="down"
        count={count.downCount}
        disabled={!isConnected}
        onPray={handlePray}
      />
    </div>
  );
}
```

### 5a.5 게이지 바

#### components/gauge/GaugeBar.tsx
```typescript
import { motion } from 'framer-motion';
import { useAtomValue } from 'jotai';
import { localCountAtom } from '@/stores/prayerStore';
import { useTranslation } from 'react-i18next';

export function GaugeBar() {
  const { t } = useTranslation();
  const count = useAtomValue(localCountAtom);

  const upPercentage = count.upRatio * 100;
  const downPercentage = count.downRatio * 100;

  return (
    <div className="w-full max-w-2xl mx-auto px-4">
      {/* 퍼센트 레이블 */}
      <div className="flex justify-between mb-2 text-sm font-medium">
        <span className="text-green-500">
          {upPercentage.toFixed(1)}%
        </span>
        <span className="text-red-500">
          {downPercentage.toFixed(1)}%
        </span>
      </div>

      {/* 게이지 바 */}
      <div className="h-4 rounded-full overflow-hidden bg-gray-200 dark:bg-gray-700 flex">
        {/* 상승 영역 */}
        <motion.div
          className="h-full bg-gradient-to-r from-green-400 to-green-500"
          initial={{ width: '50%' }}
          animate={{ width: `${upPercentage}%` }}
          transition={{ duration: 0.3, ease: 'easeOut' }}
        />

        {/* 하락 영역 */}
        <motion.div
          className="h-full bg-gradient-to-r from-red-400 to-red-500 flex-1"
          initial={{ width: '50%' }}
          animate={{ width: `${downPercentage}%` }}
          transition={{ duration: 0.3, ease: 'easeOut' }}
        />
      </div>

      {/* RPM 표시 */}
      <div className="flex justify-between mt-2 text-xs text-gray-500 dark:text-gray-400">
        <span>
          {count.upRpm.toFixed(0)} {t('prayer.rpm')}
        </span>
        <span>
          {count.downRpm.toFixed(0)} {t('prayer.rpm')}
        </span>
      </div>
    </div>
  );
}
```

#### components/gauge/RpmIndicator.tsx
```typescript
import { motion } from 'framer-motion';
import { useAtomValue } from 'jotai';
import { localCountAtom } from '@/stores/prayerStore';

export function RpmIndicator() {
  const count = useAtomValue(localCountAtom);

  const totalRpm = count.upRpm + count.downRpm;

  // RPM에 따른 색상
  const getIntensityColor = () => {
    if (totalRpm > 100) return 'text-red-500';
    if (totalRpm > 50) return 'text-orange-500';
    if (totalRpm > 20) return 'text-yellow-500';
    return 'text-gray-500';
  };

  return (
    <div className="text-center">
      <motion.div
        className={`text-4xl font-bold ${getIntensityColor()}`}
        key={Math.floor(totalRpm / 10)}
        initial={{ scale: 1.2 }}
        animate={{ scale: 1 }}
        transition={{ duration: 0.2 }}
      >
        {totalRpm.toFixed(0)}
      </motion.div>
      <div className="text-sm text-gray-500 dark:text-gray-400">
        RPM
      </div>
    </div>
  );
}
```

### 5a.6 카운터 디스플레이

#### components/counter/AnimatedNumber.tsx
```typescript
import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface AnimatedNumberProps {
  value: number;
  className?: string;
}

export function AnimatedNumber({ value, className }: AnimatedNumberProps) {
  const [displayValue, setDisplayValue] = useState(value);
  const [direction, setDirection] = useState<'up' | 'down'>('up');

  useEffect(() => {
    if (value !== displayValue) {
      setDirection(value > displayValue ? 'up' : 'down');
      setDisplayValue(value);
    }
  }, [value, displayValue]);

  return (
    <div className={`relative overflow-hidden ${className}`}>
      <AnimatePresence mode="popLayout">
        <motion.span
          key={displayValue}
          initial={{
            y: direction === 'up' ? 20 : -20,
            opacity: 0,
          }}
          animate={{
            y: 0,
            opacity: 1,
          }}
          exit={{
            y: direction === 'up' ? -20 : 20,
            opacity: 0,
          }}
          transition={{
            duration: 0.2,
            ease: 'easeOut',
          }}
          className="inline-block"
        >
          {displayValue.toLocaleString()}
        </motion.span>
      </AnimatePresence>
    </div>
  );
}
```

#### components/counter/CounterDisplay.tsx
```typescript
import { useAtomValue } from 'jotai';
import { useTranslation } from 'react-i18next';
import { localCountAtom } from '@/stores/prayerStore';
import { AnimatedNumber } from './AnimatedNumber';

export function CounterDisplay() {
  const { t } = useTranslation();
  const count = useAtomValue(localCountAtom);

  const total = count.upCount + count.downCount;

  return (
    <div className="text-center space-y-4">
      <div>
        <div className="text-sm text-gray-500 dark:text-gray-400 uppercase tracking-wider">
          {t('prayer.total')}
        </div>
        <AnimatedNumber
          value={total}
          className="text-5xl font-bold text-gray-900 dark:text-white"
        />
      </div>

      <div className="flex justify-center gap-8">
        <div className="text-center">
          <div className="text-xs text-green-500 uppercase">UP</div>
          <AnimatedNumber
            value={count.upCount}
            className="text-2xl font-bold text-green-500"
          />
        </div>
        <div className="text-center">
          <div className="text-xs text-red-500 uppercase">DOWN</div>
          <AnimatedNumber
            value={count.downCount}
            className="text-2xl font-bold text-red-500"
          />
        </div>
      </div>
    </div>
  );
}
```

### 5a.7 메인 페이지 조합

#### pages/PrayerPage.tsx (또는 App.tsx에 직접)
```typescript
import { useCallback, useState } from 'react';
import { usePrayerSocket } from '@/hooks/usePrayerSocket';
import { PrayerButtonPair } from '@/components/prayer/PrayerButtonPair';
import { ParticleContainer } from '@/components/prayer/ParticleContainer';
import { GaugeBar } from '@/components/gauge/GaugeBar';
import { RpmIndicator } from '@/components/gauge/RpmIndicator';
import { CounterDisplay } from '@/components/counter/CounterDisplay';
import type { Ticker } from '@/types/ticker';
import type { Liquidation } from '@/types/liquidation';

export function PrayerPage() {
  const [ticker, setTicker] = useState<Ticker | null>(null);
  const [liquidations, setLiquidations] = useState<Liquidation[]>([]);

  const handleTicker = useCallback((newTicker: Ticker) => {
    setTicker(newTicker);
  }, []);

  const handleLiquidation = useCallback((liquidation: Liquidation) => {
    setLiquidations((prev) => [liquidation, ...prev].slice(0, 50)); // 최대 50개 유지
  }, []);

  // WebSocket 연결
  usePrayerSocket({
    onTicker: handleTicker,
    onLiquidation: handleLiquidation,
  });

  return (
    <div className="min-h-screen pt-20 pb-8 flex flex-col items-center justify-center gap-8">
      {/* 파티클 컨테이너 */}
      <ParticleContainer />

      {/* 카운터 */}
      <CounterDisplay />

      {/* RPM 인디케이터 */}
      <RpmIndicator />

      {/* 게이지 바 */}
      <GaugeBar />

      {/* 기도 버튼 */}
      <PrayerButtonPair />

      {/* 티커 표시 (Phase 5b에서 구현) */}
      {/* 청산 피드 (Phase 5b에서 구현) */}
    </div>
  );
}
```

---

## 컴포넌트 인터랙션 다이어그램

```
User Click
    │
    ▼
PrayerButton ──► useParticles.spawnParticle() ──► particlesAtom ──► ParticleContainer
    │
    ▼
usePrayerSocket.pray() ──► pendingPrayersAtom (낙관적 업데이트)
    │
    ▼
StompClient.send() ──► Backend
    │
    ▼
Backend Response ──► prayerCountAtom (실제 값)
    │
    ▼
localCountAtom (서버값 + 펜딩) ──► GaugeBar, CounterDisplay
```

---

## 체크리스트

- [ ] 파티클 시스템
  - [ ] particleStore (상태 관리)
  - [ ] useParticles 훅
  - [ ] NumberParticle 컴포넌트
  - [ ] ParticleContainer 컴포넌트
- [ ] 기도 버튼
  - [ ] PrayerButton (그라디언트, 애니메이션)
  - [ ] PrayerButtonPair (배치)
  - [ ] 클릭 파티클 생성
- [ ] 게이지 바
  - [ ] GaugeBar (비율 표시)
  - [ ] RpmIndicator (RPM 표시)
- [ ] 카운터 디스플레이
  - [ ] AnimatedNumber (숫자 애니메이션)
  - [ ] CounterDisplay (총계, 개별)
- [ ] 페이지 통합
  - [ ] PrayerPage 조합
  - [ ] 반응형 레이아웃
- [ ] 테스트
  - [ ] 버튼 클릭 테스트
  - [ ] 파티클 생성 테스트
  - [ ] 낙관적 업데이트 테스트

---

## 검증 명령어

```bash
# 개발 서버 실행
cd frontend && pnpm dev

# 컴포넌트 테스트
pnpm test -- --filter="Prayer"
pnpm test -- --filter="Gauge"
pnpm test -- --filter="Particle"

# 스토리북 (선택사항)
pnpm storybook
```

---

## 다음 Phase
→ [Phase 5b: 청산 피드 & 효과](phase5b-ui-liquidation-feed.md)
