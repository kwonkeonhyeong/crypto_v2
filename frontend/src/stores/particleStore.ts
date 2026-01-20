import { atom } from 'jotai';

export interface Particle {
  id: string;
  x: number;
  y: number;
  value: number;
  side: 'up' | 'down';
  createdAt: number;
}

// Particle list atom
export const particlesAtom = atom<Particle[]>([]);

// Add particle atom
export const addParticleAtom = atom(
  null,
  (_get, set, particle: Omit<Particle, 'id' | 'createdAt'>) => {
    const id = `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
    const newParticle: Particle = {
      ...particle,
      id,
      createdAt: Date.now(),
    };

    set(particlesAtom, (prev) => [...prev, newParticle]);

    // Auto-remove after 1 second
    setTimeout(() => {
      set(particlesAtom, (prev) => prev.filter((p) => p.id !== id));
    }, 1000);
  }
);

// Remove particle atom
export const removeParticleAtom = atom(null, (_get, set, id: string) => {
  set(particlesAtom, (prev) => prev.filter((p) => p.id !== id));
});
