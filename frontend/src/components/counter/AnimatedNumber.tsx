import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface AnimatedNumberProps {
  value: number;
  className?: string;
}

export function AnimatedNumber({ value, className = '' }: AnimatedNumberProps) {
  const [displayValue, setDisplayValue] = useState(value);
  const [direction, setDirection] = useState<'up' | 'down'>('up');

  useEffect(() => {
    if (value !== displayValue) {
      setDirection(value > displayValue ? 'up' : 'down');
      setDisplayValue(value);
    }
  }, [value, displayValue]);

  return (
    <div className={`relative overflow-hidden ${className}`}>
      <AnimatePresence mode="popLayout">
        <motion.span
          key={displayValue}
          initial={{
            y: direction === 'up' ? 20 : -20,
            opacity: 0,
          }}
          animate={{
            y: 0,
            opacity: 1,
          }}
          exit={{
            y: direction === 'up' ? -20 : 20,
            opacity: 0,
          }}
          transition={{
            duration: 0.2,
            ease: 'easeOut',
          }}
          className="inline-block"
        >
          {displayValue.toLocaleString()}
        </motion.span>
      </AnimatePresence>
    </div>
  );
}
