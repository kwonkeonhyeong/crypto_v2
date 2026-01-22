import { useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { useTranslation } from 'react-i18next';
import { liquidationsAtom } from '@/stores/liquidationStore';
import { LiquidationListItem } from './LiquidationListItem';

// Maximum items to display in sidebar
const MAX_DISPLAY_ITEMS = 50;

function formatTotalValue(value: number): string {
  if (value >= 1_000_000_000) return `$${(value / 1_000_000_000).toFixed(2)}B`;
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
  if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
  return `$${value.toFixed(0)}`;
}

export function LiquidationSidebar() {
  const { t } = useTranslation();
  const liquidations = useAtomValue(liquidationsAtom);

  const displayedLiquidations = liquidations.slice(0, MAX_DISPLAY_ITEMS);

  // Calculate stats
  const stats = useMemo(() => {
    const total = liquidations.reduce((sum, l) => sum + l.usdValue, 0);
    const longCount = liquidations.filter((l) => l.side === 'LONG').length;
    const shortCount = liquidations.filter((l) => l.side === 'SHORT').length;
    return { total, longCount, shortCount };
  }, [liquidations]);

  return (
    <div
      className="
        fixed right-0 top-16 bottom-0 w-[360px]
        bg-gray-900/90 backdrop-blur-md border-l border-gray-800
        z-20 flex flex-col
      "
    >
      {/* Header */}
      <div className="p-4 border-b border-gray-800">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" />
            {t('liquidation.sidebar.title', '실시간 청산')}
          </h2>
          <span className="text-sm text-gray-400">
            {liquidations.length}
            {t('liquidation.sidebar.count', '건')}
          </span>
        </div>

        {/* Stats summary */}
        <div className="flex items-center justify-between text-xs">
          <div className="flex items-center gap-3">
            <span className="text-red-400">
              L: {stats.longCount}
            </span>
            <span className="text-green-400">
              S: {stats.shortCount}
            </span>
          </div>
          <span className="text-yellow-400 font-medium">
            {t('liquidation.sidebar.total', '총')} {formatTotalValue(stats.total)}
          </span>
        </div>
      </div>

      {/* List container */}
      <div className="flex-1 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-700 scrollbar-track-transparent">
        {displayedLiquidations.length === 0 ? (
          <div className="flex items-center justify-center h-32 text-gray-500 text-sm">
            {t('liquidation.sidebar.empty', '청산 데이터 대기 중...')}
          </div>
        ) : (
          <div className="p-2">
            {displayedLiquidations.map((liquidation, index) => (
              <LiquidationListItem
                key={liquidation.id}
                liquidation={liquidation}
                isNew={index === 0}
              />
            ))}
          </div>
        )}
      </div>

      {/* Footer */}
      {liquidations.length > MAX_DISPLAY_ITEMS && (
        <div className="p-3 border-t border-gray-800 text-center text-xs text-gray-500">
          {t('liquidation.sidebar.showing', '최근 {{count}}건 표시 중', {
            count: MAX_DISPLAY_ITEMS,
          })}
        </div>
      )}
    </div>
  );
}
