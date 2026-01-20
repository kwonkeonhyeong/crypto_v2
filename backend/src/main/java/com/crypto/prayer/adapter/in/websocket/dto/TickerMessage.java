package com.crypto.prayer.adapter.in.websocket.dto;

public record TickerMessage(
    String type,
    String symbol,
    double price,
    double priceChange24h,
    long timestamp
) {
    private static final String TYPE_TICKER = "TICKER";

    public static TickerMessage of(String symbol, double price, double priceChange24h) {
        return new TickerMessage(
            TYPE_TICKER,
            symbol,
            price,
            priceChange24h,
            System.currentTimeMillis()
        );
    }
}
