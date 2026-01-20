package com.crypto.prayer.domain.model;

public record PrayerStats(
    PrayerCount count,
    double upRpm,
    double downRpm,
    long timestamp
) {
    public static PrayerStats create(PrayerCount count, double upRpm, double downRpm) {
        return new PrayerStats(count, upRpm, downRpm, System.currentTimeMillis());
    }

    public double totalRpm() {
        return upRpm + downRpm;
    }
}
