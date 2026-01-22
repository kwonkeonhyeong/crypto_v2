import { motion, AnimatePresence } from 'framer-motion';

interface ScreenFlashProps {
  active: boolean;
  color?: 'red' | 'green';
}

export function ScreenFlash({ active, color = 'red' }: ScreenFlashProps) {
  const bgColor = color === 'red' ? 'bg-red-500/30' : 'bg-green-500/30';

  return (
    <AnimatePresence>
      {active && (
        <motion.div
          className={`fixed inset-0 ${bgColor} pointer-events-none z-50`}
          initial={{ opacity: 0 }}
          animate={{
            opacity: [0, 0.5, 0, 0.3, 0],
          }}
          exit={{ opacity: 0 }}
          transition={{
            duration: 0.5,
            times: [0, 0.1, 0.3, 0.4, 0.5],
          }}
        />
      )}
    </AnimatePresence>
  );
}
