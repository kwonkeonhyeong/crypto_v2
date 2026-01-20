import { describe, it, expect } from 'vitest';
import { createStore } from 'jotai';
import { prayerCountAtom, pendingPrayersAtom, localCountAtom } from '../stores/prayerStore';

describe('prayerStore', () => {
  it('should have initial prayer count with 0.5 ratios', () => {
    const store = createStore();
    const count = store.get(prayerCountAtom);

    expect(count.upCount).toBe(0);
    expect(count.downCount).toBe(0);
    expect(count.upRatio).toBe(0.5);
    expect(count.downRatio).toBe(0.5);
  });

  it('should have empty pending prayers initially', () => {
    const store = createStore();
    const pending = store.get(pendingPrayersAtom);

    expect(pending).toHaveLength(0);
  });

  it('should calculate local count with optimistic updates', () => {
    const store = createStore();

    // Set server count
    store.set(prayerCountAtom, {
      upCount: 100,
      downCount: 100,
      upRpm: 10,
      downRpm: 10,
      upRatio: 0.5,
      downRatio: 0.5,
      timestamp: Date.now(),
    });

    // Add pending prayers
    store.set(pendingPrayersAtom, [
      { side: 'up', count: 5, timestamp: Date.now() },
      { side: 'down', count: 3, timestamp: Date.now() },
    ]);

    const localCount = store.get(localCountAtom);

    expect(localCount.upCount).toBe(105);
    expect(localCount.downCount).toBe(103);
  });

  it('should calculate correct ratios in local count', () => {
    const store = createStore();

    store.set(prayerCountAtom, {
      upCount: 60,
      downCount: 40,
      upRpm: 0,
      downRpm: 0,
      upRatio: 0.6,
      downRatio: 0.4,
      timestamp: Date.now(),
    });

    store.set(pendingPrayersAtom, []);

    const localCount = store.get(localCountAtom);

    expect(localCount.upRatio).toBe(0.6);
    expect(localCount.downRatio).toBe(0.4);
  });

  it('should handle zero total count with 0.5 ratios', () => {
    const store = createStore();

    store.set(prayerCountAtom, {
      upCount: 0,
      downCount: 0,
      upRpm: 0,
      downRpm: 0,
      upRatio: 0.5,
      downRatio: 0.5,
      timestamp: Date.now(),
    });

    const localCount = store.get(localCountAtom);

    expect(localCount.upRatio).toBe(0.5);
    expect(localCount.downRatio).toBe(0.5);
  });
});
