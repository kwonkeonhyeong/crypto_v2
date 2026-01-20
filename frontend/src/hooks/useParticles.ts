import { useCallback } from 'react';
import { useSetAtom } from 'jotai';
import { addParticleAtom } from '@/stores/particleStore';
import type { Side } from '@/types/prayer';

export function useParticles() {
  const addParticle = useSetAtom(addParticleAtom);

  const spawnParticle = useCallback(
    (side: Side, x: number, y: number) => {
      // Random offset to prevent overlap
      const offsetX = (Math.random() - 0.5) * 60;
      const offsetY = (Math.random() - 0.5) * 40;

      addParticle({
        x: x + offsetX,
        y: y + offsetY,
        value: 1,
        side,
      });
    },
    [addParticle]
  );

  return { spawnParticle };
}
