# Phase 5b: UI - 청산 피드 & 효과

## 목표
청산 정보를 시각적으로 표시하는 피드와 대형 청산 효과를 구현한다.

## 선행 의존성
- Phase 5a: 기도 버튼 & 게이지 완료
- Phase 3: 바이낸스 연동 완료 (청산 데이터)

## 범위
- LiquidationFeed 컴포넌트 (전체 배경)
- 폭포 애니메이션 (Framer Motion)
- 떠다니는 텍스트: 코인아이콘 + 색상태그 + 금액
- 동적 fade-out (요청량에 따라 속도 조절)
- 대형 청산 효과 ($100K 이상) - 화면 플래시/흔들림
- BTC 시세 표시

---

## 디렉토리 구조

```
frontend/src/
├── components/
│   ├── liquidation/
│   │   ├── LiquidationFeed.tsx
│   │   ├── LiquidationItem.tsx
│   │   ├── FloatingLiquidation.tsx
│   │   └── LargeLiquidationEffect.tsx
│   ├── ticker/
│   │   ├── TickerDisplay.tsx
│   │   └── PriceChangeIndicator.tsx
│   └── effects/
│       ├── ScreenFlash.tsx
│       └── ScreenShake.tsx
├── hooks/
│   ├── useLiquidations.ts
│   └── useLargeLiquidationEffect.ts
└── stores/
    ├── liquidationStore.ts
    └── tickerStore.ts
```

---

## 상세 구현 단계

### 5b.1 청산 스토어

#### stores/liquidationStore.ts
```typescript
import { atom } from 'jotai';
import type { Liquidation } from '@/types/liquidation';

// 청산 목록 (최대 100개)
export const liquidationsAtom = atom<Liquidation[]>([]);

// 최근 대형 청산
export const lastLargeLiquidationAtom = atom<Liquidation | null>(null);

// 대형 청산 효과 활성화
export const largeLiquidationEffectAtom = atom(false);

// 청산 추가
export const addLiquidationAtom = atom(
  null,
  (get, set, liquidation: Liquidation) => {
    // 목록에 추가 (최대 100개)
    set(liquidationsAtom, (prev) => [liquidation, ...prev].slice(0, 100));

    // 대형 청산 처리
    if (liquidation.isLarge) {
      set(lastLargeLiquidationAtom, liquidation);
      set(largeLiquidationEffectAtom, true);

      // 2초 후 효과 종료
      setTimeout(() => {
        set(largeLiquidationEffectAtom, false);
      }, 2000);
    }
  }
);

// 동적 fade-out 시간 계산 (청산 빈도에 따라)
export const fadeOutDurationAtom = atom((get) => {
  const liquidations = get(liquidationsAtom);

  // 최근 10초 내 청산 수
  const now = Date.now();
  const recentCount = liquidations.filter(
    (l) => now - l.timestamp < 10000
  ).length;

  // 청산 빈도에 따른 fade-out 시간 (3초 ~ 10초)
  if (recentCount > 20) return 3000;  // 매우 바쁨
  if (recentCount > 10) return 5000;  // 바쁨
  if (recentCount > 5) return 7000;   // 보통
  return 10000;                        // 한가함
});
```

### 5b.2 티커 스토어

#### stores/tickerStore.ts
```typescript
import { atom } from 'jotai';
import type { Ticker } from '@/types/ticker';

export const tickerAtom = atom<Ticker | null>(null);

// 가격 변동 방향 (이전 가격과 비교)
export const priceDirectionAtom = atom<'up' | 'down' | 'neutral'>((get) => {
  // 이전 가격과 비교해서 방향 결정
  return 'neutral';
});
```

### 5b.3 떠다니는 청산 컴포넌트

#### components/liquidation/FloatingLiquidation.tsx
```typescript
import { motion } from 'framer-motion';
import { useMemo } from 'react';
import type { Liquidation } from '@/types/liquidation';

interface FloatingLiquidationProps {
  liquidation: Liquidation;
  fadeOutDuration: number;
  onComplete: () => void;
}

// 코인 아이콘 매핑
const COIN_ICONS: Record<string, string> = {
  BTCUSDT: '₿',
  ETHUSDT: 'Ξ',
  SOLUSDT: '◎',
  DOGEUSDT: 'Ð',
  XRPUSDT: '✕',
  default: '●',
};

export function FloatingLiquidation({
  liquidation,
  fadeOutDuration,
  onComplete,
}: FloatingLiquidationProps) {
  const isLong = liquidation.side === 'LONG';

  // 랜덤 시작 위치 (좌측 또는 우측에서)
  const startPosition = useMemo(() => ({
    x: Math.random() > 0.5 ? -200 : window.innerWidth + 200,
    y: Math.random() * (window.innerHeight * 0.7) + 100,
  }), []);

  // 종료 위치 (반대편으로)
  const endPosition = useMemo(() => ({
    x: startPosition.x < 0 ? window.innerWidth + 200 : -200,
    y: startPosition.y + (Math.random() - 0.5) * 200,
  }), [startPosition]);

  const icon = COIN_ICONS[liquidation.symbol] || COIN_ICONS.default;

  const formatValue = (value: number) => {
    if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
    if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
    return `$${value.toFixed(0)}`;
  };

  return (
    <motion.div
      className="fixed pointer-events-none z-40 flex items-center gap-2"
      initial={{
        x: startPosition.x,
        y: startPosition.y,
        opacity: 0,
        scale: 0.5,
      }}
      animate={{
        x: endPosition.x,
        y: endPosition.y,
        opacity: [0, 1, 1, 0],
        scale: [0.5, 1, 1, 0.8],
      }}
      transition={{
        duration: fadeOutDuration / 1000,
        ease: 'linear',
        opacity: {
          times: [0, 0.1, 0.8, 1],
        },
      }}
      onAnimationComplete={onComplete}
    >
      {/* 코인 아이콘 */}
      <span className="text-2xl">{icon}</span>

      {/* 청산 정보 */}
      <div
        className={`
          px-3 py-1.5 rounded-full font-bold text-white text-sm
          ${isLong ? 'bg-red-500' : 'bg-green-500'}
          ${liquidation.isLarge ? 'text-lg shadow-lg' : ''}
        `}
      >
        <span className="opacity-75 mr-1">
          {isLong ? 'LONG' : 'SHORT'}
        </span>
        <span>{formatValue(liquidation.usdValue)}</span>
      </div>

      {/* 심볼 */}
      <span className="text-xs text-gray-400 dark:text-gray-500">
        {liquidation.symbol.replace('USDT', '')}
      </span>
    </motion.div>
  );
}
```

### 5b.4 청산 피드 컨테이너

#### components/liquidation/LiquidationFeed.tsx
```typescript
import { useState, useCallback, useEffect } from 'react';
import { useAtomValue } from 'jotai';
import { liquidationsAtom, fadeOutDurationAtom } from '@/stores/liquidationStore';
import { FloatingLiquidation } from './FloatingLiquidation';
import type { Liquidation } from '@/types/liquidation';

interface ActiveLiquidation extends Liquidation {
  animationId: string;
}

export function LiquidationFeed() {
  const liquidations = useAtomValue(liquidationsAtom);
  const fadeOutDuration = useAtomValue(fadeOutDurationAtom);
  const [activeLiquidations, setActiveLiquidations] = useState<ActiveLiquidation[]>([]);

  // 새 청산이 들어오면 활성 목록에 추가
  useEffect(() => {
    if (liquidations.length === 0) return;

    const latest = liquidations[0];

    // 이미 추가된 청산인지 확인
    setActiveLiquidations((prev) => {
      if (prev.some((l) => l.id === latest.id)) return prev;

      const newLiq: ActiveLiquidation = {
        ...latest,
        animationId: `${latest.id}-${Date.now()}`,
      };

      // 최대 20개의 활성 청산만 유지
      return [newLiq, ...prev].slice(0, 20);
    });
  }, [liquidations]);

  const handleComplete = useCallback((animationId: string) => {
    setActiveLiquidations((prev) =>
      prev.filter((l) => l.animationId !== animationId)
    );
  }, []);

  return (
    <div className="fixed inset-0 pointer-events-none overflow-hidden z-30">
      {activeLiquidations.map((liq) => (
        <FloatingLiquidation
          key={liq.animationId}
          liquidation={liq}
          fadeOutDuration={fadeOutDuration}
          onComplete={() => handleComplete(liq.animationId)}
        />
      ))}
    </div>
  );
}
```

### 5b.5 대형 청산 효과

#### components/effects/ScreenFlash.tsx
```typescript
import { motion, AnimatePresence } from 'framer-motion';

interface ScreenFlashProps {
  active: boolean;
  color?: 'red' | 'green';
}

export function ScreenFlash({ active, color = 'red' }: ScreenFlashProps) {
  const bgColor = color === 'red'
    ? 'bg-red-500/30'
    : 'bg-green-500/30';

  return (
    <AnimatePresence>
      {active && (
        <motion.div
          className={`fixed inset-0 ${bgColor} pointer-events-none z-50`}
          initial={{ opacity: 0 }}
          animate={{
            opacity: [0, 0.5, 0, 0.3, 0],
          }}
          exit={{ opacity: 0 }}
          transition={{
            duration: 0.5,
            times: [0, 0.1, 0.3, 0.4, 0.5],
          }}
        />
      )}
    </AnimatePresence>
  );
}
```

#### components/effects/ScreenShake.tsx
```typescript
import { motion } from 'framer-motion';
import { ReactNode } from 'react';

interface ScreenShakeProps {
  active: boolean;
  children: ReactNode;
}

export function ScreenShake({ active, children }: ScreenShakeProps) {
  return (
    <motion.div
      animate={
        active
          ? {
              x: [0, -5, 5, -5, 5, 0],
              y: [0, 3, -3, 3, -3, 0],
            }
          : {}
      }
      transition={{
        duration: 0.5,
        ease: 'easeInOut',
      }}
    >
      {children}
    </motion.div>
  );
}
```

#### components/liquidation/LargeLiquidationEffect.tsx
```typescript
import { useAtomValue } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  largeLiquidationEffectAtom,
  lastLargeLiquidationAtom,
} from '@/stores/liquidationStore';
import { ScreenFlash } from '../effects/ScreenFlash';
import { useTranslation } from 'react-i18next';

export function LargeLiquidationEffect() {
  const { t } = useTranslation();
  const isActive = useAtomValue(largeLiquidationEffectAtom);
  const liquidation = useAtomValue(lastLargeLiquidationAtom);

  if (!liquidation) return null;

  const isLong = liquidation.side === 'LONG';
  const color = isLong ? 'red' : 'green';

  const formatValue = (value: number) => {
    if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
    return `$${(value / 1_000).toFixed(0)}K`;
  };

  return (
    <>
      {/* 화면 플래시 */}
      <ScreenFlash active={isActive} color={color} />

      {/* 중앙 알림 */}
      <AnimatePresence>
        {isActive && (
          <motion.div
            className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none"
            initial={{ opacity: 0, scale: 0.5 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.5 }}
            transition={{ duration: 0.3 }}
          >
            <div
              className={`
                px-8 py-6 rounded-2xl
                ${isLong ? 'bg-red-500' : 'bg-green-500'}
                text-white text-center shadow-2xl
              `}
            >
              <motion.div
                className="text-4xl font-bold"
                animate={{
                  scale: [1, 1.1, 1],
                }}
                transition={{
                  duration: 0.5,
                  repeat: 2,
                }}
              >
                {t('liquidation.large')}
              </motion.div>

              <div className="mt-2 text-2xl">
                {liquidation.symbol.replace('USDT', '')}
              </div>

              <div className="mt-1 text-3xl font-bold">
                {isLong ? 'LONG' : 'SHORT'} {formatValue(liquidation.usdValue)}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
```

### 5b.6 티커 디스플레이

#### components/ticker/TickerDisplay.tsx
```typescript
import { useAtomValue } from 'jotai';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { tickerAtom } from '@/stores/tickerStore';
import { PriceChangeIndicator } from './PriceChangeIndicator';

export function TickerDisplay() {
  const { t } = useTranslation();
  const ticker = useAtomValue(tickerAtom);

  if (!ticker) {
    return (
      <div className="text-center text-gray-500 dark:text-gray-400">
        Loading...
      </div>
    );
  }

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(price);
  };

  return (
    <div className="text-center space-y-2">
      {/* BTC 가격 */}
      <div className="flex items-center justify-center gap-2">
        <span className="text-2xl">₿</span>
        <motion.span
          key={Math.floor(ticker.price)}
          initial={{ opacity: 0.5 }}
          animate={{ opacity: 1 }}
          className="text-4xl md:text-5xl font-bold text-gray-900 dark:text-white"
        >
          {formatPrice(ticker.price)}
        </motion.span>
      </div>

      {/* 24시간 변동률 */}
      <PriceChangeIndicator change={ticker.priceChange24h} />
    </div>
  );
}
```

#### components/ticker/PriceChangeIndicator.tsx
```typescript
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';

interface PriceChangeIndicatorProps {
  change: number;
}

export function PriceChangeIndicator({ change }: PriceChangeIndicatorProps) {
  const { t } = useTranslation();
  const isPositive = change >= 0;

  const formatChange = (value: number) => {
    const sign = value >= 0 ? '+' : '';
    return `${sign}${value.toFixed(2)}%`;
  };

  return (
    <motion.div
      className={`
        inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-medium
        ${isPositive
          ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
          : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
        }
      `}
      initial={{ scale: 0.9 }}
      animate={{ scale: 1 }}
      key={Math.floor(change * 10)}
    >
      {/* 화살표 아이콘 */}
      <span className="text-lg">
        {isPositive ? '↑' : '↓'}
      </span>

      {/* 변동률 */}
      <span>{formatChange(change)}</span>

      {/* 라벨 */}
      <span className="text-xs opacity-70">
        {t('ticker.change24h')}
      </span>
    </motion.div>
  );
}
```

### 5b.7 청산 목록 (사이드바용, 선택사항)

#### components/liquidation/LiquidationList.tsx
```typescript
import { useAtomValue } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import { liquidationsAtom } from '@/stores/liquidationStore';
import { LiquidationItem } from './LiquidationItem';

export function LiquidationList() {
  const liquidations = useAtomValue(liquidationsAtom);

  // 최근 10개만 표시
  const recentLiquidations = liquidations.slice(0, 10);

  return (
    <div className="bg-gray-100 dark:bg-gray-800 rounded-lg p-4 max-h-80 overflow-y-auto">
      <h3 className="text-sm font-semibold text-gray-500 dark:text-gray-400 mb-3">
        Recent Liquidations
      </h3>

      <AnimatePresence initial={false}>
        {recentLiquidations.map((liq) => (
          <motion.div
            key={liq.id}
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.2 }}
          >
            <LiquidationItem liquidation={liq} />
          </motion.div>
        ))}
      </AnimatePresence>

      {liquidations.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-gray-400 text-center py-4">
          No liquidations yet
        </p>
      )}
    </div>
  );
}
```

#### components/liquidation/LiquidationItem.tsx
```typescript
import type { Liquidation } from '@/types/liquidation';

interface LiquidationItemProps {
  liquidation: Liquidation;
}

export function LiquidationItem({ liquidation }: LiquidationItemProps) {
  const isLong = liquidation.side === 'LONG';

  const formatValue = (value: number) => {
    if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
    if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
    return `$${value.toFixed(0)}`;
  };

  const formatTime = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  return (
    <div
      className={`
        flex items-center justify-between py-2 border-b border-gray-200 dark:border-gray-700
        last:border-0
        ${liquidation.isLarge ? 'bg-yellow-50 dark:bg-yellow-900/20 -mx-2 px-2 rounded' : ''}
      `}
    >
      <div className="flex items-center gap-2">
        {/* 색상 인디케이터 */}
        <div
          className={`w-2 h-2 rounded-full ${isLong ? 'bg-red-500' : 'bg-green-500'}`}
        />

        {/* 심볼 */}
        <span className="font-medium text-sm text-gray-900 dark:text-white">
          {liquidation.symbol.replace('USDT', '')}
        </span>

        {/* 방향 태그 */}
        <span
          className={`
            text-xs px-1.5 py-0.5 rounded
            ${isLong
              ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
              : 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
            }
          `}
        >
          {isLong ? 'L' : 'S'}
        </span>
      </div>

      <div className="text-right">
        {/* 금액 */}
        <div className="text-sm font-bold text-gray-900 dark:text-white">
          {formatValue(liquidation.usdValue)}
        </div>

        {/* 시간 */}
        <div className="text-xs text-gray-500 dark:text-gray-400">
          {formatTime(liquidation.timestamp)}
        </div>
      </div>
    </div>
  );
}
```

### 5b.8 페이지 통합 업데이트

#### 수정된 PrayerPage.tsx
```typescript
import { useCallback, useEffect } from 'react';
import { useSetAtom } from 'jotai';
import { usePrayerSocket } from '@/hooks/usePrayerSocket';
import { PrayerButtonPair } from '@/components/prayer/PrayerButtonPair';
import { ParticleContainer } from '@/components/prayer/ParticleContainer';
import { GaugeBar } from '@/components/gauge/GaugeBar';
import { RpmIndicator } from '@/components/gauge/RpmIndicator';
import { CounterDisplay } from '@/components/counter/CounterDisplay';
import { LiquidationFeed } from '@/components/liquidation/LiquidationFeed';
import { LargeLiquidationEffect } from '@/components/liquidation/LargeLiquidationEffect';
import { TickerDisplay } from '@/components/ticker/TickerDisplay';
import { ScreenShake } from '@/components/effects/ScreenShake';
import { tickerAtom } from '@/stores/tickerStore';
import { addLiquidationAtom, largeLiquidationEffectAtom } from '@/stores/liquidationStore';
import { useAtomValue } from 'jotai';

export function PrayerPage() {
  const setTicker = useSetAtom(tickerAtom);
  const addLiquidation = useSetAtom(addLiquidationAtom);
  const isLargeEffect = useAtomValue(largeLiquidationEffectAtom);

  // WebSocket 연결
  usePrayerSocket({
    onTicker: setTicker,
    onLiquidation: addLiquidation,
  });

  return (
    <ScreenShake active={isLargeEffect}>
      <div className="min-h-screen pt-20 pb-8 flex flex-col items-center justify-center gap-8">
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

        {/* RPM 인디케이터 */}
        <RpmIndicator />

        {/* 게이지 바 */}
        <GaugeBar />

        {/* 기도 버튼 */}
        <PrayerButtonPair />
      </div>
    </ScreenShake>
  );
}
```

---

## 동적 Fade-out 로직

| 최근 10초 내 청산 수 | Fade-out 시간 | 상태 |
|---------------------|--------------|------|
| > 20개 | 3초 | 매우 바쁨 |
| > 10개 | 5초 | 바쁨 |
| > 5개 | 7초 | 보통 |
| <= 5개 | 10초 | 한가함 |

---

## 체크리스트

- [ ] 청산 스토어
  - [ ] liquidationsAtom
  - [ ] addLiquidationAtom
  - [ ] fadeOutDurationAtom (동적)
  - [ ] largeLiquidationEffectAtom
- [ ] 티커 스토어
  - [ ] tickerAtom
- [ ] 떠다니는 청산
  - [ ] FloatingLiquidation (애니메이션)
  - [ ] LiquidationFeed (컨테이너)
  - [ ] 코인 아이콘 매핑
- [ ] 대형 청산 효과
  - [ ] ScreenFlash
  - [ ] ScreenShake
  - [ ] LargeLiquidationEffect
- [ ] 티커 디스플레이
  - [ ] TickerDisplay
  - [ ] PriceChangeIndicator
- [ ] 청산 목록 (선택)
  - [ ] LiquidationList
  - [ ] LiquidationItem
- [ ] 페이지 통합
- [ ] 테스트
  - [ ] 청산 애니메이션 테스트
  - [ ] 대형 청산 효과 테스트
  - [ ] 동적 fade-out 테스트

---

## 검증 명령어

```bash
# 개발 서버 실행
cd frontend && pnpm dev

# 컴포넌트 테스트
pnpm test -- --filter="Liquidation"
pnpm test -- --filter="Ticker"
pnpm test -- --filter="Effect"

# 시각적 테스트 (브라우저)
# 1. 콘솔에서 가짜 청산 이벤트 발생
# window.dispatchEvent(new CustomEvent('testLiquidation', { detail: {...} }))
```

---

## 다음 Phase
→ [Phase 5c: 사운드 & 모바일](phase5c-ui-sound-mobile.md)
