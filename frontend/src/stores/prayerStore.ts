import { atom } from 'jotai';
import type { PrayerCount, PendingPrayer } from '@/types/prayer';

// Server-synced prayer count
export const prayerCountAtom = atom<PrayerCount>({
  upCount: 0,
  downCount: 0,
  upRpm: 0,
  downRpm: 0,
  upRatio: 0.5,
  downRatio: 0.5,
  timestamp: Date.now(),
});

// Pending prayers for optimistic updates (batched)
export const pendingPrayersAtom = atom<PendingPrayer[]>([]);

// Local count = server count + pending (optimistic)
export const localCountAtom = atom((get) => {
  const serverCount = get(prayerCountAtom);
  const pending = get(pendingPrayersAtom);

  const pendingUp = pending
    .filter((p) => p.side === 'up')
    .reduce((sum, p) => sum + p.count, 0);
  const pendingDown = pending
    .filter((p) => p.side === 'down')
    .reduce((sum, p) => sum + p.count, 0);

  const upCount = serverCount.upCount + pendingUp;
  const downCount = serverCount.downCount + pendingDown;
  const total = upCount + downCount;

  return {
    ...serverCount,
    upCount,
    downCount,
    upRatio: total > 0 ? upCount / total : 0.5,
    downRatio: total > 0 ? downCount / total : 0.5,
  };
});
