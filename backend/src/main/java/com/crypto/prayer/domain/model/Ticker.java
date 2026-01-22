package com.crypto.prayer.domain.model;

import java.time.Instant;

public record Ticker(
    String symbol,
    double price,
    double priceChange24h,
    double high24h,
    double low24h,
    double volume24h,
    Instant timestamp
) {
    public static Ticker of(
            String symbol,
            double price,
            double priceChangePercent) {
        return new Ticker(
            symbol,
            price,
            priceChangePercent,
            0, 0, 0,
            Instant.now()
        );
    }

    public boolean isPositive() {
        return priceChange24h >= 0;
    }

    public String formattedPrice() {
        if (price >= 1000) {
            return String.format("$%.2f", price);
        }
        return String.format("$%.4f", price);
    }

    public String formattedChange() {
        String sign = priceChange24h >= 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, priceChange24h);
    }
}
