import { useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
  themePreferenceAtom,
  resolvedThemeAtom,
  isDarkModeAtom,
  systemThemeAtom,
  type Theme,
} from '@/stores/themeStore';

export function useTheme() {
  const [preference, setPreference] = useAtom(themePreferenceAtom);
  const resolvedTheme = useAtomValue(resolvedThemeAtom);
  const isDarkMode = useAtomValue(isDarkModeAtom);
  const setSystemTheme = useSetAtom(systemThemeAtom);

  // Apply theme class to document
  useEffect(() => {
    const root = document.documentElement;

    if (isDarkMode) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [isDarkMode]);

  // Detect system theme changes
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

    // Set initial system theme
    setSystemTheme(mediaQuery.matches ? 'dark' : 'light');

    const handler = (e: MediaQueryListEvent) => {
      setSystemTheme(e.matches ? 'dark' : 'light');
    };

    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, [setSystemTheme]);

  const setTheme = (theme: Theme) => {
    setPreference(theme);
  };

  const toggleTheme = () => {
    if (preference === 'system') {
      setPreference(isDarkMode ? 'light' : 'dark');
    } else {
      setPreference(preference === 'dark' ? 'light' : 'dark');
    }
  };

  return {
    theme: resolvedTheme,
    preference,
    isDarkMode,
    setTheme,
    toggleTheme,
  };
}
