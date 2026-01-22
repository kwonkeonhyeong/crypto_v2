import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';
import {
  themePreferenceAtom,
  systemThemeAtom,
  resolvedThemeAtom,
  isDarkModeAtom,
} from '../stores/themeStore';

describe('themeStore', () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
    // Clear localStorage
    localStorage.clear();
  });

  it('should default to system preference', () => {
    const preference = store.get(themePreferenceAtom);
    expect(preference).toBe('system');
  });

  it('should resolve to system theme when preference is system', () => {
    store.set(systemThemeAtom, 'dark');
    const resolved = store.get(resolvedThemeAtom);
    expect(resolved).toBe('dark');
  });

  it('should use preference when not system', () => {
    store.set(themePreferenceAtom, 'light');
    store.set(systemThemeAtom, 'dark');

    const resolved = store.get(resolvedThemeAtom);
    expect(resolved).toBe('light');
  });

  it('should return correct isDarkMode', () => {
    store.set(themePreferenceAtom, 'dark');
    expect(store.get(isDarkModeAtom)).toBe(true);

    store.set(themePreferenceAtom, 'light');
    expect(store.get(isDarkModeAtom)).toBe(false);
  });

  it('should handle system theme changes when preference is system', () => {
    store.set(themePreferenceAtom, 'system');

    store.set(systemThemeAtom, 'light');
    expect(store.get(resolvedThemeAtom)).toBe('light');
    expect(store.get(isDarkModeAtom)).toBe(false);

    store.set(systemThemeAtom, 'dark');
    expect(store.get(resolvedThemeAtom)).toBe('dark');
    expect(store.get(isDarkModeAtom)).toBe(true);
  });
});
