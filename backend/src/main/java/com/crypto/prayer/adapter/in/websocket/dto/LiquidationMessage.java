package com.crypto.prayer.adapter.in.websocket.dto;

public record LiquidationMessage(
    String type,
    String symbol,
    String side,
    double quantity,
    double price,
    double usdValue,
    boolean isLarge,
    long timestamp
) {
    private static final String TYPE_LIQUIDATION = "LIQUIDATION";
    private static final double LARGE_THRESHOLD = 100_000.0;

    public static LiquidationMessage of(
            String symbol,
            String side,
            double quantity,
            double price) {
        double usdValue = quantity * price;
        return new LiquidationMessage(
            TYPE_LIQUIDATION,
            symbol,
            side,
            quantity,
            price,
            usdValue,
            usdValue >= LARGE_THRESHOLD,
            System.currentTimeMillis()
        );
    }
}
