import { atom } from 'jotai';
import type { Ticker } from '@/types/ticker';

// Current ticker data
export const tickerAtom = atom<Ticker | null>(null);

// Previous ticker for comparison (to detect price direction)
export const previousTickerAtom = atom<Ticker | null>(null);

// Update ticker with history tracking
export const updateTickerAtom = atom(
  null,
  (get, set, newTicker: Ticker) => {
    const currentTicker = get(tickerAtom);
    if (currentTicker) {
      set(previousTickerAtom, currentTicker);
    }
    set(tickerAtom, newTicker);
  }
);

// Price direction derived atom
export type PriceDirection = 'up' | 'down' | 'neutral';

export const priceDirectionAtom = atom<PriceDirection>((get) => {
  const current = get(tickerAtom);
  const previous = get(previousTickerAtom);

  if (!current || !previous) return 'neutral';

  if (current.price > previous.price) return 'up';
  if (current.price < previous.price) return 'down';
  return 'neutral';
});

// Is ticker data available
export const hasTickerDataAtom = atom((get) => {
  const ticker = get(tickerAtom);
  return ticker !== null;
});
