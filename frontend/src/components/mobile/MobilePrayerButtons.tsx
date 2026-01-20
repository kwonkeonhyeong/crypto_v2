import { useCallback } from 'react';
import { motion } from 'framer-motion';
import { useAtomValue } from 'jotai';
import { clsx } from 'clsx';
import { useTranslation } from 'react-i18next';
import { localCountAtom } from '@/stores/prayerStore';
import { isConnectedAtom } from '@/stores/websocketStore';
import { useParticles } from '@/hooks/useParticles';
import { useSoundEffect } from '@/hooks/useSoundEffect';
import type { Side } from '@/types/prayer';

interface MobilePrayerButtonsProps {
  onPray: (side: Side) => void;
}

/**
 * Mobile-optimized prayer buttons
 * Fixed at bottom 1/3 of screen with touch optimization
 */
export function MobilePrayerButtons({ onPray }: MobilePrayerButtonsProps) {
  const count = useAtomValue(localCountAtom);
  const isConnected = useAtomValue(isConnectedAtom);
  const { spawnParticle } = useParticles();
  const { playClick } = useSoundEffect();

  const handlePray = useCallback(
    (side: Side, event: React.TouchEvent | React.MouseEvent) => {
      if (!isConnected) return;

      // Get touch/click position
      const touch = 'touches' in event ? event.touches[0] : event;
      const x = touch.clientX;
      const y = touch.clientY;

      spawnParticle(side, x, y);
      playClick();
      onPray(side);
    },
    [isConnected, onPray, spawnParticle, playClick]
  );

  return (
    <div
      className="
        fixed bottom-0 left-0 right-0
        h-1/3 min-h-[180px]
        bg-gradient-to-t from-black/30 to-transparent
        pb-safe
        z-40
      "
    >
      <div className="h-full grid grid-cols-2 gap-3 p-4">
        <MobileButton
          side="up"
          count={count.upCount}
          disabled={!isConnected}
          onPress={handlePray}
        />
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
  const { t } = useTranslation();
  const isUp = side === 'up';

  return (
    <motion.button
      className={clsx(
        'w-full h-full rounded-2xl',
        'flex flex-col items-center justify-center gap-1',
        'text-white font-bold',
        'active:scale-95 transition-transform',
        'touch-manipulation select-none',
        {
          'bg-gradient-to-br from-red-400 to-red-600': isUp && !disabled,
          'bg-gradient-to-br from-blue-400 to-blue-600': !isUp && !disabled,
          'bg-gray-400 opacity-50': disabled,
        }
      )}
      disabled={disabled}
      onTouchStart={(e) => {
        e.preventDefault();
        onPress(side, e);
      }}
      onClick={(e) => onPress(side, e)}
      whileTap={{ scale: 0.95 }}
    >
      <span className="text-4xl">{isUp ? '\u{1F680}' : '\u{1F4C9}'}</span>
      <span className="text-lg">{t(`prayer.${side}`)}</span>
      <span className="text-2xl">{count.toLocaleString()}</span>
    </motion.button>
  );
}
