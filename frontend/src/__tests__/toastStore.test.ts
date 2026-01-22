import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createStore } from 'jotai';
import { toastsAtom, addToastAtom, removeToastAtom } from '../stores/toastStore';

describe('toastStore', () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should start with empty toasts', () => {
    const toasts = store.get(toastsAtom);
    expect(toasts).toHaveLength(0);
  });

  it('should add toast with auto-generated id', () => {
    store.set(addToastAtom, { message: 'Test message', type: 'info' });

    const toasts = store.get(toastsAtom);
    expect(toasts).toHaveLength(1);
    expect(toasts[0].message).toBe('Test message');
    expect(toasts[0].type).toBe('info');
    expect(toasts[0].id).toBeDefined();
  });

  it('should auto-remove toast after duration', () => {
    store.set(addToastAtom, { message: 'Test', type: 'success', duration: 1000 });

    expect(store.get(toastsAtom)).toHaveLength(1);

    vi.advanceTimersByTime(1000);

    expect(store.get(toastsAtom)).toHaveLength(0);
  });

  it('should use default duration of 3000ms', () => {
    store.set(addToastAtom, { message: 'Test', type: 'error' });

    expect(store.get(toastsAtom)).toHaveLength(1);

    vi.advanceTimersByTime(2999);
    expect(store.get(toastsAtom)).toHaveLength(1);

    vi.advanceTimersByTime(1);
    expect(store.get(toastsAtom)).toHaveLength(0);
  });

  it('should manually remove toast', () => {
    store.set(addToastAtom, { message: 'Test', type: 'warning' });

    const toasts = store.get(toastsAtom);
    const toastId = toasts[0].id;

    store.set(removeToastAtom, toastId);

    expect(store.get(toastsAtom)).toHaveLength(0);
  });

  it('should handle multiple toasts', () => {
    store.set(addToastAtom, { message: 'First', type: 'info' });
    store.set(addToastAtom, { message: 'Second', type: 'success' });
    store.set(addToastAtom, { message: 'Third', type: 'error' });

    const toasts = store.get(toastsAtom);
    expect(toasts).toHaveLength(3);
    expect(toasts.map((t) => t.message)).toEqual(['First', 'Second', 'Third']);
  });
});
