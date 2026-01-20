import { motion } from 'framer-motion';
import { useAtomValue } from 'jotai';
import { localCountAtom } from '@/stores/prayerStore';
import { useTranslation } from 'react-i18next';

export function GaugeBar() {
  const { t } = useTranslation();
  const count = useAtomValue(localCountAtom);

  const upPercentage = count.upRatio * 100;
  const downPercentage = count.downRatio * 100;

  return (
    <div className="w-full max-w-2xl mx-auto px-4">
      {/* Percentage labels */}
      <div className="flex justify-between mb-2 text-sm font-medium">
        <span className="text-red-500">{upPercentage.toFixed(1)}%</span>
        <span className="text-blue-500">{downPercentage.toFixed(1)}%</span>
      </div>

      {/* Gauge bar */}
      <div className="h-4 rounded-full overflow-hidden bg-gray-200 dark:bg-gray-700 flex">
        {/* Up area (left) */}
        <motion.div
          className="h-full bg-gradient-to-r from-red-400 to-red-500"
          initial={{ width: '50%' }}
          animate={{ width: `${upPercentage}%` }}
          transition={{ duration: 0.3, ease: 'easeOut' }}
        />

        {/* Down area (right) */}
        <motion.div
          className="h-full bg-gradient-to-r from-blue-400 to-blue-500 flex-1"
          initial={{ width: '50%' }}
          animate={{ width: `${downPercentage}%` }}
          transition={{ duration: 0.3, ease: 'easeOut' }}
        />
      </div>

      {/* RPM display */}
      <div className="flex justify-between mt-2 text-xs text-gray-500 dark:text-gray-400">
        <span>
          {count.upRpm.toFixed(0)} {t('prayer.rpm')}
        </span>
        <span>
          {count.downRpm.toFixed(0)} {t('prayer.rpm')}
        </span>
      </div>
    </div>
  );
}
