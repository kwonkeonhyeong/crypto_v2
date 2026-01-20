package com.crypto.prayer.domain.model;

public record PrayerCount(
    long upCount,
    long downCount
) {
    public static PrayerCount zero() {
        return new PrayerCount(0L, 0L);
    }

    public PrayerCount incrementUp(long delta) {
        return new PrayerCount(upCount + delta, downCount);
    }

    public PrayerCount incrementDown(long delta) {
        return new PrayerCount(upCount, downCount + delta);
    }

    public PrayerCount increment(Side side, long delta) {
        return switch (side) {
            case UP -> incrementUp(delta);
            case DOWN -> incrementDown(delta);
        };
    }

    public long total() {
        return upCount + downCount;
    }

    public double upRatio() {
        long total = total();
        return total == 0 ? 0.5 : (double) upCount / total;
    }

    public double downRatio() {
        return 1.0 - upRatio();
    }

    public PrayerCount merge(PrayerCount other) {
        return new PrayerCount(
            this.upCount + other.upCount,
            this.downCount + other.downCount
        );
    }
}
