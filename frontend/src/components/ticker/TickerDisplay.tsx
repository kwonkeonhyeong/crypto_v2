import { useAtomValue } from 'jotai';
import { motion } from 'framer-motion';
import { tickerAtom } from '@/stores/tickerStore';
import { PriceChangeIndicator } from './PriceChangeIndicator';

function formatPrice(price: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(price);
}

export function TickerDisplay() {
  const ticker = useAtomValue(tickerAtom);

  if (!ticker) {
    return (
      <div className="text-center text-gray-500 dark:text-gray-400">
        <div className="animate-pulse">
          <div className="h-10 w-48 bg-gray-200 dark:bg-gray-700 rounded mx-auto mb-2" />
          <div className="h-6 w-32 bg-gray-200 dark:bg-gray-700 rounded mx-auto" />
        </div>
      </div>
    );
  }

  return (
    <div className="text-center space-y-2">
      {/* BTC price */}
      <div className="flex items-center justify-center gap-2">
        <span className="text-2xl">{'\u20BF'}</span>
        <motion.span
          key={Math.floor(ticker.price)}
          initial={{ opacity: 0.5 }}
          animate={{ opacity: 1 }}
          className="text-4xl md:text-5xl font-bold text-gray-900 dark:text-white"
        >
          {formatPrice(ticker.price)}
        </motion.span>
      </div>

      {/* 24h change indicator */}
      <PriceChangeIndicator change={ticker.priceChange24h} />
    </div>
  );
}
