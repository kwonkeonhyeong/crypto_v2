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

// Amount tier thresholds
export const LIQUIDATION_TIERS = {
  HUGE: 100_000,
  LARGE: 50_000,
  MEDIUM: 10_000,
} as const;

// Amount tier-based style configuration
interface TierStyle {
  textSize: string;
  iconSize: string;
  shadow: string;
  glow: boolean;
  bgOpacity: string;
}

function getLiquidationTierStyle(usdValue: number): TierStyle {
  if (usdValue >= LIQUIDATION_TIERS.HUGE) {
    return {
      textSize: 'text-xl',
      iconSize: 'text-3xl',
      shadow: 'shadow-2xl',
      glow: true,
      bgOpacity: 'bg-black/50',
    };
  }
  if (usdValue >= LIQUIDATION_TIERS.LARGE) {
    return {
      textSize: 'text-lg',
      iconSize: 'text-2xl',
      shadow: 'shadow-xl',
      glow: false,
      bgOpacity: 'bg-black/40',
    };
  }
  if (usdValue >= LIQUIDATION_TIERS.MEDIUM) {
    return {
      textSize: 'text-base',
      iconSize: 'text-2xl',
      shadow: 'shadow-lg',
      glow: false,
      bgOpacity: 'bg-black/30',
    };
  }
  return {
    textSize: 'text-sm',
    iconSize: 'text-xl',
    shadow: 'shadow-md',
    glow: false,
    bgOpacity: 'bg-black/20',
  };
}

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
  const tierStyle = getLiquidationTierStyle(liquidation.usdValue);

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
      {/* Container with backdrop for better visibility */}
      <div
        className={`
          flex items-center gap-2 px-3 py-2 rounded-xl
          ${tierStyle.bgOpacity} backdrop-blur-sm ${tierStyle.shadow}
          ${tierStyle.glow ? 'ring-2 ring-white/30 animate-pulse' : ''}
        `}
      >
        {/* Coin icon */}
        <span className={tierStyle.iconSize}>{icon}</span>

        {/* Liquidation info tag */}
        <div
          className={`
            px-3 py-1.5 rounded-full font-bold text-white ${tierStyle.textSize}
            ${isLong ? 'bg-red-500' : 'bg-green-500'}
            ${tierStyle.glow ? 'shadow-lg shadow-current' : ''}
          `}
        >
          <span className="opacity-80 mr-1.5">{isLong ? 'LONG' : 'SHORT'}</span>
          <span>{formatUsdValue(liquidation.usdValue)}</span>
        </div>

        {/* Symbol name */}
        <span
          className={`
            text-white font-medium drop-shadow-lg
            ${tierStyle.glow ? 'text-base' : 'text-sm'}
          `}
        >
          {symbolName}
        </span>
      </div>
    </motion.div>
  );
}
