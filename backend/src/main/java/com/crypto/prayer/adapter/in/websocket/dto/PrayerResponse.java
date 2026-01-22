package com.crypto.prayer.adapter.in.websocket.dto;

public record PrayerResponse(
    String type,
    long upCount,
    long downCount,
    double upRpm,
    double downRpm,
    double upRatio,
    double downRatio,
    long timestamp
) {
    private static final String TYPE_PRAYER = "PRAYER";

    public static PrayerResponse from(
            long upCount,
            long downCount,
            double upRpm,
            double downRpm) {
        long total = upCount + downCount;
        double upRatio = total == 0 ? 0.5 : (double) upCount / total;

        return new PrayerResponse(
            TYPE_PRAYER,
            upCount,
            downCount,
            upRpm,
            downRpm,
            upRatio,
            1.0 - upRatio,
            System.currentTimeMillis()
        );
    }
}
