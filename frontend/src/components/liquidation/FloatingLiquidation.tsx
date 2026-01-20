import { motion } from 'framer-motion';
import { useMemo } from 'react';
import type { Liquidation } from '@/types/liquidation';

interface FloatingLiquidationProps {
  liquidation: Liquidation;
  fadeOutDuration: number;
  onComplete: () => void;
}

// Coin icon mapping for major cryptocurrencies
const COIN_ICONS: Record<string, string> = {
  BTCUSDT: '₿',
  ETHUSDT: 'Ξ',
  SOLUSDT: '◎',
  DOGEUSDT: 'Ð',
  XRPUSDT: '✕',
  BNBUSDT: 'B',
  ADAUSDT: '₳',
  DOTUSDT: '●',
  MATICUSDT: 'M',
  LINKUSDT: '⬡',
  AVAXUSDT: 'A',
  LTCUSDT: 'Ł',
};

const DEFAULT_ICON = '●';

function formatUsdValue(value: number): string {
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
  if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
  return `$${value.toFixed(0)}`;
}

export function FloatingLiquidation({
  liquidation,
  fadeOutDuration,
  onComplete,
}: FloatingLiquidationProps) {
  const isLong = liquidation.side === 'LONG';
  const icon = COIN_ICONS[liquidation.symbol] || DEFAULT_ICON;
  const symbolName = liquidation.symbol.replace('USDT', '');

  // Random start position (from left or right side)
  const startPosition = useMemo(
    () => ({
      x:
        Math.random() > 0.5
          ? -200
          : typeof window !== 'undefined'
            ? window.innerWidth + 200
            : 1200,
      y:
        typeof window !== 'undefined'
          ? Math.random() * (window.innerHeight * 0.7) + 100
          : 300,
    }),
    []
  );

  // End position (opposite side)
  const endPosition = useMemo(
    () => ({
      x:
        startPosition.x < 0
          ? typeof window !== 'undefined'
            ? window.innerWidth + 200
            : 1200
          : -200,
      y: startPosition.y + (Math.random() - 0.5) * 200,
    }),
    [startPosition]
  );

  const durationInSeconds = fadeOutDuration / 1000;

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
        duration: durationInSeconds,
        ease: 'linear',
        opacity: {
          times: [0, 0.1, 0.8, 1],
        },
      }}
      onAnimationComplete={onComplete}
    >
      {/* Coin icon */}
      <span className="text-2xl">{icon}</span>

      {/* Liquidation info tag */}
      <div
        className={`
          px-3 py-1.5 rounded-full font-bold text-white text-sm
          ${isLong ? 'bg-red-500' : 'bg-green-500'}
          ${liquidation.isLarge ? 'text-lg shadow-lg' : ''}
        `}
      >
        <span className="opacity-75 mr-1">{isLong ? 'LONG' : 'SHORT'}</span>
        <span>{formatUsdValue(liquidation.usdValue)}</span>
      </div>

      {/* Symbol name */}
      <span className="text-xs text-gray-400 dark:text-gray-500">
        {symbolName}
      </span>
    </motion.div>
  );
}
