import { useCallback } from 'react';
import { useAtomValue } from 'jotai';
import { soundEnabledAtom } from '@/stores/soundStore';
import { soundManager } from '@/lib/soundManager';

/**
 * Hook for playing sound effects
 * Respects the global sound enabled setting
 */
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
