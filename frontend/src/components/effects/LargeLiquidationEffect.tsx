import { useAtomValue } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import {
  largeLiquidationEffectAtom,
  lastLargeLiquidationAtom,
} from '@/stores/liquidationStore';
import { ScreenFlash } from './ScreenFlash';

function formatUsdValue(value: number): string {
  if (value >= 1_000_000) return `$${(value / 1_000_000).toFixed(2)}M`;
  return `$${(value / 1_000).toFixed(0)}K`;
}

export function LargeLiquidationEffect() {
  const { t } = useTranslation();
  const isActive = useAtomValue(largeLiquidationEffectAtom);
  const liquidation = useAtomValue(lastLargeLiquidationAtom);

  if (!liquidation) return null;

  const isLong = liquidation.side === 'LONG';
  const color = isLong ? 'red' : 'green';
  const symbolName = liquidation.symbol.replace('USDT', '');

  return (
    <>
      {/* Screen flash overlay */}
      <ScreenFlash active={isActive} color={color} />

      {/* Center notification */}
      <AnimatePresence>
        {isActive && (
          <motion.div
            className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none"
            initial={{ opacity: 0, scale: 0.5 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.5 }}
            transition={{ duration: 0.3 }}
          >
            <div
              className={`
                px-8 py-6 rounded-2xl
                ${isLong ? 'bg-red-500' : 'bg-green-500'}
                text-white text-center shadow-2xl
              `}
            >
              <motion.div
                className="text-4xl font-bold"
                animate={{
                  scale: [1, 1.1, 1],
                }}
                transition={{
                  duration: 0.5,
                  repeat: 2,
                }}
              >
                {t('liquidation.large')}
              </motion.div>

              <div className="mt-2 text-2xl">{symbolName}</div>

              <div className="mt-1 text-3xl font-bold">
                {isLong ? 'LONG' : 'SHORT'} {formatUsdValue(liquidation.usdValue)}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
