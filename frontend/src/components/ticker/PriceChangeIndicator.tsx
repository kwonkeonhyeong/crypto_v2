import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';

interface PriceChangeIndicatorProps {
  change: number;
}

function formatChange(value: number): string {
  const sign = value >= 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

export function PriceChangeIndicator({ change }: PriceChangeIndicatorProps) {
  const { t } = useTranslation();
  const isPositive = change >= 0;

  return (
    <motion.div
      className={`
        inline-flex items-center gap-1 px-3 py-1 rounded-full text-sm font-medium
        ${
          isPositive
            ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
            : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
        }
      `}
      initial={{ scale: 0.9 }}
      animate={{ scale: 1 }}
      key={Math.floor(change * 10)}
    >
      {/* Arrow icon */}
      <span className="text-lg">{isPositive ? '\u2191' : '\u2193'}</span>

      {/* Change percentage */}
      <span>{formatChange(change)}</span>

      {/* Label */}
      <span className="text-xs opacity-70">{t('ticker.change24h')}</span>
    </motion.div>
  );
}
