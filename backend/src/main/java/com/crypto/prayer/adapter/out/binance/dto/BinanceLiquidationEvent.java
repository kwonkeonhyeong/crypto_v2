package com.crypto.prayer.adapter.out.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 바이낸스 청산 이벤트
 * {
 *   "e": "forceOrder",
 *   "E": 1568014460893,
 *   "o": {
 *     "s": "BTCUSDT",
 *     "S": "SELL",
 *     "o": "LIMIT",
 *     "f": "IOC",
 *     "q": "0.014",
 *     "p": "9910",
 *     "ap": "9910",
 *     "X": "FILLED",
 *     "l": "0.014",
 *     "z": "0.014",
 *     "T": 1568014460893
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceLiquidationEvent(
    @JsonProperty("e") String eventType,
    @JsonProperty("E") long eventTime,
    @JsonProperty("o") Order order
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(
        @JsonProperty("s") String symbol,
        @JsonProperty("S") String side,
        @JsonProperty("q") String quantity,
        @JsonProperty("p") String price,
        @JsonProperty("ap") String avgPrice,
        @JsonProperty("X") String status,
        @JsonProperty("z") String filledQty
    ) {}

    public double getQuantity() {
        return Double.parseDouble(order.filledQty());
    }

    public double getPrice() {
        return Double.parseDouble(order.avgPrice());
    }

    public String getSymbol() {
        return order.symbol();
    }

    public String getSide() {
        return order.side();
    }
}
