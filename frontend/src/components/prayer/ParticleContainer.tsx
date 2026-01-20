import { useAtomValue } from 'jotai';
import { particlesAtom } from '@/stores/particleStore';
import { NumberParticle } from './NumberParticle';

export function ParticleContainer() {
  const particles = useAtomValue(particlesAtom);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        pointerEvents: 'none',
        zIndex: 50,
      }}
    >
      {particles.map((particle) => (
        <NumberParticle key={particle.id} particle={particle} />
      ))}
    </div>
  );
}
