import { useCallback } from 'react';
import { useSetAtom, useAtomValue } from 'jotai';
import { usePrayerSocket } from '@/hooks/usePrayerSocket';
import { useSoundEffect } from '@/hooks/useSoundEffect';
import { useIsMobile } from '@/hooks/useIsMobile';
import { PrayerButtonPair, ParticleContainer } from '@/components/prayer';
import { GaugeBar, RpmIndicator } from '@/components/gauge';
import { CounterDisplay } from '@/components/counter';
import { LiquidationFeed } from '@/components/liquidation';
import { TickerDisplay } from '@/components/ticker';
import { ScreenShake, LargeLiquidationEffect } from '@/components/effects';
import { MobileLayout } from '@/components/mobile';
import { tickerAtom } from '@/stores/tickerStore';
import {
  addLiquidationAtom,
  largeLiquidationEffectAtom,
} from '@/stores/liquidationStore';
import type { Ticker } from '@/types/ticker';
import type { Liquidation } from '@/types/liquidation';

// Large liquidation threshold (USD)
const LARGE_LIQUIDATION_THRESHOLD = 100000;

export function PrayerPage() {
  const setTicker = useSetAtom(tickerAtom);
  const addLiquidation = useSetAtom(addLiquidationAtom);
  const isLargeEffect = useAtomValue(largeLiquidationEffectAtom);
  const isMobile = useIsMobile();

  const { playLiquidation, playLargeLiquidation } = useSoundEffect();

  const handleTicker = useCallback(
    (newTicker: Ticker) => {
      setTicker(newTicker);
    },
    [setTicker]
  );

  const handleLiquidation = useCallback(
    (liquidation: Liquidation) => {
      addLiquidation(liquidation);

      // Play sound based on liquidation size
      if (liquidation.isLarge || liquidation.usdValue >= LARGE_LIQUIDATION_THRESHOLD) {
        playLargeLiquidation();
      } else {
        playLiquidation();
      }
    },
    [addLiquidation, playLiquidation, playLargeLiquidation]
  );

  // WebSocket connection
  const { pray } = usePrayerSocket({
    onTicker: handleTicker,
    onLiquidation: handleLiquidation,
  });

  const content = (
    <ScreenShake active={isLargeEffect}>
      <div
        className={`
          min-h-screen pt-20 flex flex-col items-center justify-center gap-8
          ${isMobile ? 'pb-[33vh]' : 'pb-8'}
        `}
      >
        {/* Background liquidation feed (floating items) */}
        <LiquidationFeed />

        {/* Large liquidation effect (flash + notification) */}
        <LargeLiquidationEffect />

        {/* Particle container */}
        <ParticleContainer />

        {/* BTC Ticker display */}
        <TickerDisplay />

        {/* Counter display */}
        <CounterDisplay />

        {/* RPM indicator */}
        <RpmIndicator />

        {/* Gauge bar */}
        <GaugeBar />

        {/* Prayer buttons - only show on desktop */}
        {!isMobile && <PrayerButtonPair onPray={pray} />}
      </div>
    </ScreenShake>
  );

  return <MobileLayout onPray={pray}>{content}</MobileLayout>;
}
