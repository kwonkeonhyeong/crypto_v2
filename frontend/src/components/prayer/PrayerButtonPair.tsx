import { useCallback } from 'react';
import { useAtomValue } from 'jotai';
import { localCountAtom } from '@/stores/prayerStore';
import { isConnectedAtom } from '@/stores/websocketStore';
import { useParticles } from '@/hooks/useParticles';
import { useSoundEffect } from '@/hooks/useSoundEffect';
import { PrayerButton } from './PrayerButton';
import type { Side } from '@/types/prayer';

interface PrayerButtonPairProps {
  onPray: (side: Side) => void;
}

export function PrayerButtonPair({ onPray }: PrayerButtonPairProps) {
  const count = useAtomValue(localCountAtom);
  const isConnected = useAtomValue(isConnectedAtom);
  const { spawnParticle } = useParticles();
  const { playClick } = useSoundEffect();

  const handlePray = useCallback(
    (side: Side, event: React.MouseEvent) => {
      // Spawn particle at click position
      const x = event.clientX;
      const y = event.clientY;

      spawnParticle(side, x, y);
      playClick();
      onPray(side);
    },
    [onPray, spawnParticle, playClick]
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
