package com.crypto.prayer.domain.model;

import java.time.Instant;

public record Liquidation(
    String symbol,
    LiquidationSide side,
    double quantity,
    double price,
    double usdValue,
    Instant timestamp
) {
    private static final double LARGE_THRESHOLD = 100_000.0;

    public enum LiquidationSide {
        LONG, SHORT
    }

    public static Liquidation of(
            String symbol,
            String side,
            double quantity,
            double price) {
        LiquidationSide liqSide = "SELL".equalsIgnoreCase(side)
            ? LiquidationSide.LONG   // 매도 청산 = 롱 포지션 청산
            : LiquidationSide.SHORT; // 매수 청산 = 숏 포지션 청산

        return new Liquidation(
            symbol,
            liqSide,
            quantity,
            price,
            quantity * price,
            Instant.now()
        );
    }

    public boolean isLarge() {
        return usdValue >= LARGE_THRESHOLD;
    }

    public String formattedValue() {
        if (usdValue >= 1_000_000) {
            return String.format("$%.2fM", usdValue / 1_000_000);
        } else if (usdValue >= 1_000) {
            return String.format("$%.1fK", usdValue / 1_000);
        }
        return String.format("$%.0f", usdValue);
    }
}
