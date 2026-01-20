import { atomWithStorage } from 'jotai/utils';

// Sound effects enabled (persisted in localStorage)
export const soundEnabledAtom = atomWithStorage<boolean>('soundEnabled', true);

// BGM enabled (persisted in localStorage)
export const bgmEnabledAtom = atomWithStorage<boolean>('bgmEnabled', false);
