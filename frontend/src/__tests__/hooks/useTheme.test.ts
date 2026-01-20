import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { createElement } from 'react';
import { useTheme } from '../../hooks/useTheme';
import {
  themePreferenceAtom,
  systemThemeAtom,
} from '../../stores/themeStore';

// Mock matchMedia
const createMatchMedia = (matches: boolean) => {
  const listeners: ((e: MediaQueryListEvent) => void)[] = [];
  return vi.fn().mockImplementation(() => ({
    matches,
    media: '(prefers-color-scheme: dark)',
    addEventListener: (_event: string, listener: (e: MediaQueryListEvent) => void) => {
      listeners.push(listener);
    },
    removeEventListener: (_event: string, listener: (e: MediaQueryListEvent) => void) => {
      const index = listeners.indexOf(listener);
      if (index > -1) listeners.splice(index, 1);
    },
  }));
};

describe('useTheme', () => {
  let store: ReturnType<typeof createStore>;

  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(Provider, { store }, children);

  beforeEach(() => {
    store = createStore();
    localStorage.clear();
    // Default to light system theme
    window.matchMedia = createMatchMedia(false);
    // Clear document classList
    document.documentElement.classList.remove('dark');
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('기본_테마_설정은_system이다', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });

    expect(result.current.preference).toBe('system');
  });

  it('시스템이_다크모드일_때_resolvedTheme은_dark이다', () => {
    window.matchMedia = createMatchMedia(true);
    store.set(systemThemeAtom, 'dark');

    const { result } = renderHook(() => useTheme(), { wrapper });

    expect(result.current.theme).toBe('dark');
    expect(result.current.isDarkMode).toBe(true);
  });

  it('시스템이_라이트모드일_때_resolvedTheme은_light이다', () => {
    window.matchMedia = createMatchMedia(false);
    store.set(systemThemeAtom, 'light');

    const { result } = renderHook(() => useTheme(), { wrapper });

    expect(result.current.theme).toBe('light');
    expect(result.current.isDarkMode).toBe(false);
  });

  it('setTheme으로_테마를_dark로_설정할_수_있다', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.setTheme('dark');
    });

    expect(result.current.preference).toBe('dark');
    expect(result.current.theme).toBe('dark');
    expect(result.current.isDarkMode).toBe(true);
  });

  it('setTheme으로_테마를_light로_설정할_수_있다', () => {
    store.set(themePreferenceAtom, 'dark');

    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.setTheme('light');
    });

    expect(result.current.preference).toBe('light');
    expect(result.current.theme).toBe('light');
    expect(result.current.isDarkMode).toBe(false);
  });

  it('toggleTheme으로_system에서_반대_테마로_전환된다', () => {
    window.matchMedia = createMatchMedia(true);
    store.set(systemThemeAtom, 'dark');

    const { result } = renderHook(() => useTheme(), { wrapper });

    expect(result.current.preference).toBe('system');
    expect(result.current.isDarkMode).toBe(true);

    act(() => {
      result.current.toggleTheme();
    });

    // When system is dark and we toggle, it should go to light
    expect(result.current.preference).toBe('light');
    expect(result.current.isDarkMode).toBe(false);
  });

  it('toggleTheme으로_dark에서_light로_전환된다', () => {
    store.set(themePreferenceAtom, 'dark');

    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.toggleTheme();
    });

    expect(result.current.preference).toBe('light');
  });

  it('toggleTheme으로_light에서_dark로_전환된다', () => {
    store.set(themePreferenceAtom, 'light');

    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.toggleTheme();
    });

    expect(result.current.preference).toBe('dark');
  });

  it('preference가_system이고_시스템이_dark일_때_document에_dark_클래스가_추가된다', () => {
    window.matchMedia = createMatchMedia(true);
    store.set(systemThemeAtom, 'dark');

    renderHook(() => useTheme(), { wrapper });

    // useEffect가 실행될 때까지 기다림
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('preference가_light일_때_document에서_dark_클래스가_제거된다', () => {
    document.documentElement.classList.add('dark');
    store.set(themePreferenceAtom, 'light');

    renderHook(() => useTheme(), { wrapper });

    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });
});
