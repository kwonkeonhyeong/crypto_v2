import { useState, useCallback, useMemo } from 'react';
import { motion, useAnimation, PanInfo } from 'framer-motion';
import { useAtomValue } from 'jotai';
import { useTranslation } from 'react-i18next';
import { liquidationsAtom } from '@/stores/liquidationStore';
import { LiquidationListItem } from './LiquidationListItem';

// Panel height configurations
const COLLAPSED_HEIGHT = 48;
const EXPANDED_HEIGHT_VH = 50;
const MAX_DISPLAY_ITEMS = 30;

function formatUsdValue(value: number): string {
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
  if (value >= 1_000) return `$${(value / 1_000).toFixed(1)}K`;
  return `$${value.toFixed(0)}`;
}

export function LiquidationBottomPanel() {
  const { t } = useTranslation();
  const liquidations = useAtomValue(liquidationsAtom);
  const [isExpanded, setIsExpanded] = useState(false);
  const controls = useAnimation();

  const displayedLiquidations = liquidations.slice(0, MAX_DISPLAY_ITEMS);

  // Get latest liquidation for collapsed view summary
  const latestLiquidation = liquidations[0];
  const latestSummary = useMemo(() => {
    if (!latestLiquidation) return null;
    const symbol = latestLiquidation.symbol.replace('USDT', '');
    const side = latestLiquidation.side;
    const amount = formatUsdValue(latestLiquidation.usdValue);
    return { symbol, side, amount };
  }, [latestLiquidation]);

  // Calculate expanded height in pixels
  const getExpandedHeight = useCallback(() => {
    return typeof window !== 'undefined'
      ? window.innerHeight * (EXPANDED_HEIGHT_VH / 100)
      : 400;
  }, []);

  // Handle drag end
  const handleDragEnd = useCallback(
    (_: unknown, info: PanInfo) => {
      const velocity = info.velocity.y;
      const offset = info.offset.y;

      // Swipe up to expand (negative velocity/offset)
      if (velocity < -500 || (offset < -50 && velocity < 0)) {
        setIsExpanded(true);
        controls.start({ height: getExpandedHeight() });
      }
      // Swipe down to collapse (positive velocity/offset)
      else if (velocity > 500 || (offset > 50 && velocity > 0)) {
        setIsExpanded(false);
        controls.start({ height: COLLAPSED_HEIGHT });
      }
      // Return to current state if not a decisive swipe
      else {
        controls.start({ height: isExpanded ? getExpandedHeight() : COLLAPSED_HEIGHT });
      }
    },
    [controls, isExpanded, getExpandedHeight]
  );

  // Toggle panel
  const handleToggle = useCallback(() => {
    const newExpanded = !isExpanded;
    setIsExpanded(newExpanded);
    controls.start({ height: newExpanded ? getExpandedHeight() : COLLAPSED_HEIGHT });
  }, [controls, isExpanded, getExpandedHeight]);

  return (
    <motion.div
      className="
        fixed left-0 right-0 bottom-[33vh]
        bg-gray-900/95 backdrop-blur-md
        border-t border-gray-800 rounded-t-2xl
        z-30 flex flex-col
        touch-pan-y
      "
      initial={{ height: COLLAPSED_HEIGHT }}
      animate={controls}
      drag="y"
      dragConstraints={{ top: 0, bottom: 0 }}
      dragElastic={0.2}
      onDragEnd={handleDragEnd}
    >
      {/* Drag handle */}
      <div
        className="flex flex-col items-center py-2 cursor-pointer"
        onClick={handleToggle}
      >
        <div className="w-10 h-1 rounded-full bg-gray-600 mb-1" />

        {/* Collapsed view: summary */}
        {!isExpanded && (
          <div className="flex items-center justify-between w-full px-4">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" />
              <span className="text-sm text-white font-medium">
                {t('liquidation.panel.title', '청산')} {liquidations.length}
                {t('liquidation.panel.count', '건')}
              </span>
            </div>

            {latestSummary && (
              <span className="text-xs text-gray-400 truncate max-w-[180px]">
                {t('liquidation.panel.latest', '최근')}: {latestSummary.symbol}{' '}
                <span
                  className={
                    latestSummary.side === 'LONG'
                      ? 'text-red-400'
                      : 'text-green-400'
                  }
                >
                  {latestSummary.side}
                </span>{' '}
                {latestSummary.amount}
              </span>
            )}

            {/* Expand indicator */}
            <svg
              className="w-4 h-4 text-gray-500"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 15l7-7 7 7"
              />
            </svg>
          </div>
        )}

        {/* Expanded view: header */}
        {isExpanded && (
          <div className="flex items-center justify-between w-full px-4">
            <span className="text-sm text-white font-medium">
              {t('liquidation.panel.realtime', '실시간 청산')}
            </span>
            <span className="text-xs text-gray-400">
              {liquidations.length}{t('liquidation.panel.count', '건')}
            </span>
            {/* Collapse indicator */}
            <svg
              className="w-4 h-4 text-gray-500"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M19 9l-7 7-7-7"
              />
            </svg>
          </div>
        )}
      </div>

      {/* Expanded content: liquidation list */}
      {isExpanded && (
        <div className="flex-1 overflow-y-auto px-2 pb-2">
          {displayedLiquidations.length === 0 ? (
            <div className="flex items-center justify-center h-20 text-gray-500 text-sm">
              {t('liquidation.panel.empty', '청산 데이터 대기 중...')}
            </div>
          ) : (
            displayedLiquidations.map((liquidation, index) => (
              <LiquidationListItem
                key={liquidation.id}
                liquidation={liquidation}
                isNew={index === 0}
              />
            ))
          )}
        </div>
      )}
    </motion.div>
  );
}
