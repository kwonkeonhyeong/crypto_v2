import { useState, useCallback, useEffect, useRef } from 'react';
import { useAtomValue } from 'jotai';
import {
  liquidationsAtom,
  fadeOutDurationAtom,
} from '@/stores/liquidationStore';
import { FloatingLiquidation } from './FloatingLiquidation';
import type { Liquidation } from '@/types/liquidation';

interface ActiveLiquidation extends Liquidation {
  animationId: string;
}

const MAX_ACTIVE_LIQUIDATIONS = 20;

export function LiquidationFeed() {
  const liquidations = useAtomValue(liquidationsAtom);
  const fadeOutDuration = useAtomValue(fadeOutDurationAtom);
  const [activeLiquidations, setActiveLiquidations] = useState<
    ActiveLiquidation[]
  >([]);
  const processedIdsRef = useRef<Set<string>>(new Set());

  // Track new liquidations and add them to active list
  useEffect(() => {
    if (liquidations.length === 0) return;

    const latest = liquidations[0];

    // Skip if already processed
    if (processedIdsRef.current.has(latest.id)) return;

    // Mark as processed
    processedIdsRef.current.add(latest.id);

    // Clean up old processed IDs to prevent memory leak
    if (processedIdsRef.current.size > 200) {
      const idsArray = Array.from(processedIdsRef.current);
      processedIdsRef.current = new Set(idsArray.slice(-100));
    }

    // Add to active liquidations
    setActiveLiquidations((prev) => {
      const newLiq: ActiveLiquidation = {
        ...latest,
        animationId: `${latest.id}-${Date.now()}`,
      };

      return [newLiq, ...prev].slice(0, MAX_ACTIVE_LIQUIDATIONS);
    });
  }, [liquidations]);

  const handleAnimationComplete = useCallback((animationId: string) => {
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
          onComplete={() => handleAnimationComplete(liq.animationId)}
        />
      ))}
    </div>
  );
}
