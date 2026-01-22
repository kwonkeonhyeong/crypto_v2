import type { Liquidation } from '@/types/liquidation';

interface LiquidationItemProps {
  liquidation: Liquidation;
}

function formatUsdValue(value: number): string {
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
  if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
  return `$${value.toFixed(0)}`;
}

function formatTime(timestamp: number): string {
  const date = new Date(timestamp);
  return date.toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function LiquidationItem({ liquidation }: LiquidationItemProps) {
  const isLong = liquidation.side === 'LONG';
  const symbolName = liquidation.symbol.replace('USDT', '');

  return (
    <div
      className={`
        flex items-center justify-between py-2 border-b border-gray-200 dark:border-gray-700
        last:border-0
        ${liquidation.isLarge ? 'bg-yellow-50 dark:bg-yellow-900/20 -mx-2 px-2 rounded' : ''}
      `}
    >
      <div className="flex items-center gap-2">
        {/* Color indicator */}
        <div
          className={`w-2 h-2 rounded-full ${isLong ? 'bg-red-500' : 'bg-green-500'}`}
        />

        {/* Symbol */}
        <span className="font-medium text-sm text-gray-900 dark:text-white">
          {symbolName}
        </span>

        {/* Direction tag */}
        <span
          className={`
            text-xs px-1.5 py-0.5 rounded
            ${
              isLong
                ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
                : 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
            }
          `}
        >
          {isLong ? 'L' : 'S'}
        </span>
      </div>

      <div className="text-right">
        {/* Amount */}
        <div className="text-sm font-bold text-gray-900 dark:text-white">
          {formatUsdValue(liquidation.usdValue)}
        </div>

        {/* Time */}
        <div className="text-xs text-gray-500 dark:text-gray-400">
          {formatTime(liquidation.timestamp)}
        </div>
      </div>
    </div>
  );
}
