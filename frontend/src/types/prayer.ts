export type Side = 'up' | 'down';

export interface PrayerCount {
  upCount: number;
  downCount: number;
  upRpm: number;
  downRpm: number;
  upRatio: number;
  downRatio: number;
  timestamp: number;
}

export interface PrayerRequest {
  side: Side;
  count: number;
}

export interface PendingPrayer {
  side: Side;
  count: number;
  timestamp: number;
}
