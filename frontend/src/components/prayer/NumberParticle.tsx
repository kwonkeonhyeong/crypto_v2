import { motion } from 'framer-motion';
import type { Particle } from '@/stores/particleStore';

interface NumberParticleProps {
  particle: Particle;
}

export function NumberParticle({ particle }: NumberParticleProps) {
  const isUp = particle.side === 'up';

  return (
    <motion.div
      style={{
        position: 'fixed',
        left: particle.x,
        top: particle.y,
        zIndex: 100,
        pointerEvents: 'none',
        fontWeight: 'bold',
        fontSize: '1.5rem',
        color: isUp ? '#22c55e' : '#ef4444',
        textShadow: '0 2px 4px rgba(0, 0, 0, 0.3)',
      }}
      initial={{
        opacity: 1,
        scale: 0.5,
        y: 0,
      }}
      animate={{
        opacity: 0,
        scale: 1.5,
        y: -80,
      }}
      transition={{
        duration: 0.8,
        ease: 'easeOut',
      }}
    >
      +{particle.value}
    </motion.div>
  );
}
