import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

export type Theme = 'light' | 'dark' | 'system';

// User's theme preference (persisted in localStorage)
export const themePreferenceAtom = atomWithStorage<Theme>('theme', 'system');

// System theme detection atom (non-persisted)
export const systemThemeAtom = atom<'light' | 'dark'>('dark');

// Resolved theme (what's actually applied)
export const resolvedThemeAtom = atom<'light' | 'dark'>((get) => {
  const preference = get(themePreferenceAtom);

  if (preference !== 'system') {
    return preference;
  }

  return get(systemThemeAtom);
});

// Convenience atom for dark mode check
export const isDarkModeAtom = atom<boolean>((get) => get(resolvedThemeAtom) === 'dark');
