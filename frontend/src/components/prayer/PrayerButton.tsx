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

      // Button pulse animation
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
          'bg-gradient-to-br from-red-400 to-red-600 hover:from-red-500 hover:to-red-700':
            isUp && !disabled,
          'bg-gradient-to-br from-blue-400 to-blue-600 hover:from-blue-500 hover:to-blue-700':
            !isUp && !disabled,
          'bg-gray-400 cursor-not-allowed opacity-50': disabled,
        }
      )}
      whileHover={!disabled ? { scale: 1.02 } : undefined}
      whileTap={!disabled ? { scale: 0.98 } : undefined}
    >
      {/* Icon */}
      <div className="text-4xl md:text-5xl">{isUp ? '\u{1F680}' : '\u{1F4C9}'}</div>

      {/* Label */}
      <div className="text-lg md:text-xl font-semibold">{t(`prayer.${side}`)}</div>

      {/* Count */}
      <div className="text-2xl md:text-3xl font-bold">{count.toLocaleString()}</div>

      {/* Glow effect */}
      <div
        className={clsx(
          'absolute inset-0 rounded-2xl opacity-0 transition-opacity',
          'hover:opacity-30',
          {
            'bg-red-300': isUp,
            'bg-blue-300': !isUp,
          }
        )}
      />
    </motion.button>
  );
}
