import { useAtomValue } from 'jotai';
import { useTranslation } from 'react-i18next';
import { localCountAtom } from '@/stores/prayerStore';
import { AnimatedNumber } from './AnimatedNumber';

export function CounterDisplay() {
  const { t } = useTranslation();
  const count = useAtomValue(localCountAtom);

  const total = count.upCount + count.downCount;

  return (
    <div className="text-center space-y-4">
      <div>
        <div className="text-sm text-gray-500 dark:text-gray-400 uppercase tracking-wider">
          {t('prayer.total')}
        </div>
        <AnimatedNumber
          value={total}
          className="text-5xl font-bold text-gray-900 dark:text-white"
        />
      </div>

      <div className="flex justify-center gap-8">
        <div className="text-center">
          <div className="text-xs text-red-500 uppercase">UP</div>
          <AnimatedNumber
            value={count.upCount}
            className="text-2xl font-bold text-red-500"
          />
        </div>
        <div className="text-center">
          <div className="text-xs text-blue-500 uppercase">DOWN</div>
          <AnimatedNumber
            value={count.downCount}
            className="text-2xl font-bold text-blue-500"
          />
        </div>
      </div>
    </div>
  );
}
