import { motion } from 'framer-motion';
import type { Liquidation } from '@/types/liquidation';
import { LIQUIDATION_TIERS } from './FloatingLiquidation';

interface LiquidationListItemProps {
  liquidation: Liquidation;
  isNew?: boolean;
}

// Coin icon mapping
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

function formatRelativeTime(timestamp: number): string {
  const now = Date.now();
  const diff = now - timestamp;

  if (diff < 5000) return '방금';
  if (diff < 60000) return `${Math.floor(diff / 1000)}초 전`;
  if (diff < 3600000) return `${Math.floor(diff / 60000)}분 전`;
  return `${Math.floor(diff / 3600000)}시간 전`;
}

function getAmountStyle(usdValue: number): {
  textColor: string;
  bgColor: string;
  fontWeight: string;
} {
  if (usdValue >= LIQUIDATION_TIERS.HUGE) {
    return {
      textColor: 'text-yellow-400',
      bgColor: 'bg-yellow-500/20',
      fontWeight: 'font-black',
    };
  }
  if (usdValue >= LIQUIDATION_TIERS.LARGE) {
    return {
      textColor: 'text-orange-400',
      bgColor: 'bg-orange-500/10',
      fontWeight: 'font-bold',
    };
  }
  if (usdValue >= LIQUIDATION_TIERS.MEDIUM) {
    return {
      textColor: 'text-white',
      bgColor: '',
      fontWeight: 'font-semibold',
    };
  }
  return {
    textColor: 'text-gray-300',
    bgColor: '',
    fontWeight: 'font-medium',
  };
}

export function LiquidationListItem({
  liquidation,
  isNew = false,
}: LiquidationListItemProps) {
  const isLong = liquidation.side === 'LONG';
  const symbolName = liquidation.symbol.replace('USDT', '');
  const icon = COIN_ICONS[liquidation.symbol] || DEFAULT_ICON;
  const amountStyle = getAmountStyle(liquidation.usdValue);

  return (
    <motion.div
      initial={isNew ? { opacity: 0, x: 50 } : false}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.3 }}
      className={`
        flex items-center justify-between py-2.5 px-3 rounded-lg
        border-b border-gray-700/50 last:border-0
        hover:bg-gray-800/50 transition-colors
        ${amountStyle.bgColor}
      `}
    >
      <div className="flex items-center gap-2.5">
        {/* Coin icon */}
        <span className="text-lg w-6 text-center">{icon}</span>

        {/* Symbol */}
        <span className="font-medium text-sm text-white min-w-[40px]">
          {symbolName}
        </span>

        {/* Direction tag */}
        <span
          className={`
            text-xs px-2 py-0.5 rounded font-medium
            ${
              isLong
                ? 'bg-red-500/30 text-red-400'
                : 'bg-green-500/30 text-green-400'
            }
          `}
        >
          {isLong ? 'LONG' : 'SHORT'}
        </span>
      </div>

      <div className="flex items-center gap-3">
        {/* Amount */}
        <span className={`text-sm ${amountStyle.textColor} ${amountStyle.fontWeight}`}>
          {formatUsdValue(liquidation.usdValue)}
        </span>

        {/* Relative time */}
        <span className="text-xs text-gray-500 min-w-[45px] text-right">
          {formatRelativeTime(liquidation.timestamp)}
        </span>
      </div>
    </motion.div>
  );
}
