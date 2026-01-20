import { atom } from 'jotai';
import type { Liquidation } from '@/types/liquidation';

// Maximum number of liquidations to keep in memory
const MAX_LIQUIDATIONS = 100;
const LARGE_EFFECT_DURATION = 2000;

// Liquidation list atom (newest first)
export const liquidationsAtom = atom<Liquidation[]>([]);

// Last large liquidation for effect display
export const lastLargeLiquidationAtom = atom<Liquidation | null>(null);

// Large liquidation effect active flag
export const largeLiquidationEffectAtom = atom(false);

// Add liquidation action atom
export const addLiquidationAtom = atom(
  null,
  (_get, set, liquidation: Liquidation) => {
    // Add to list (newest first, limit to MAX_LIQUIDATIONS)
    set(liquidationsAtom, (prev) =>
      [liquidation, ...prev].slice(0, MAX_LIQUIDATIONS)
    );

    // Handle large liquidation effect
    if (liquidation.isLarge) {
      set(lastLargeLiquidationAtom, liquidation);
      set(largeLiquidationEffectAtom, true);

      // Auto-disable effect after duration
      setTimeout(() => {
        set(largeLiquidationEffectAtom, false);
      }, LARGE_EFFECT_DURATION);
    }
  }
);

// Dynamic fade-out duration based on liquidation frequency
export const fadeOutDurationAtom = atom((get) => {
  const liquidations = get(liquidationsAtom);
  const now = Date.now();

  // Count liquidations in last 10 seconds
  const recentCount = liquidations.filter(
    (l) => now - l.timestamp < 10000
  ).length;

  // Return fade-out duration based on activity level
  if (recentCount > 20) return 3000; // Very busy
  if (recentCount > 10) return 5000; // Busy
  if (recentCount > 5) return 7000; // Normal
  return 10000; // Quiet
});

// Clear all liquidations
export const clearLiquidationsAtom = atom(null, (_get, set) => {
  set(liquidationsAtom, []);
  set(lastLargeLiquidationAtom, null);
  set(largeLiquidationEffectAtom, false);
});
