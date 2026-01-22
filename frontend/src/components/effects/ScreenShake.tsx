import { motion } from 'framer-motion';
import type { ReactNode } from 'react';

interface ScreenShakeProps {
  active: boolean;
  children: ReactNode;
}

export function ScreenShake({ active, children }: ScreenShakeProps) {
  return (
    <motion.div
      animate={
        active
          ? {
              x: [0, -5, 5, -5, 5, 0],
              y: [0, 3, -3, 3, -3, 0],
            }
          : { x: 0, y: 0 }
      }
      transition={{
        duration: 0.5,
        ease: 'easeInOut',
      }}
    >
      {children}
    </motion.div>
  );
}
