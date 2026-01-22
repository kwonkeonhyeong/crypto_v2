import { motion } from 'framer-motion';
import { useAtomValue } from 'jotai';
import { localCountAtom } from '@/stores/prayerStore';

export function RpmIndicator() {
  const count = useAtomValue(localCountAtom);

  const totalRpm = count.upRpm + count.downRpm;

  // Color based on RPM intensity
  const getIntensityColor = () => {
    if (totalRpm > 100) return 'text-red-500';
    if (totalRpm > 50) return 'text-orange-500';
    if (totalRpm > 20) return 'text-yellow-500';
    return 'text-gray-500 dark:text-gray-400';
  };

  return (
    <div className="text-center">
      <motion.div
        className={`text-4xl font-bold ${getIntensityColor()}`}
        key={Math.floor(totalRpm / 10)}
        initial={{ scale: 1.2 }}
        animate={{ scale: 1 }}
        transition={{ duration: 0.2 }}
      >
        {totalRpm.toFixed(0)}
      </motion.div>
      <div className="text-sm text-gray-500 dark:text-gray-400">RPM</div>
    </div>
  );
}
