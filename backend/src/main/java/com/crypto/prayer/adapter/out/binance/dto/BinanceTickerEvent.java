package com.crypto.prayer.adapter.out.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 바이낸스 24시간 티커
 * {
 *   "e": "24hrTicker",
 *   "E": 1672515782136,
 *   "s": "BTCUSDT",
 *   "p": "100.00000000",
 *   "P": "0.50",
 *   "c": "20100.00000000",
 *   "h": "20500.00000000",
 *   "l": "19800.00000000",
 *   "v": "100000.00000000",
 *   "q": "2000000000.00000000"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTickerEvent(
    @JsonProperty("e") String eventType,
    @JsonProperty("E") long eventTime,
    @JsonProperty("s") String symbol,
    @JsonProperty("c") String closePrice,
    @JsonProperty("P") String priceChangePercent,
    @JsonProperty("h") String highPrice,
    @JsonProperty("l") String lowPrice,
    @JsonProperty("v") String volume
) {
    public double getPrice() {
        return Double.parseDouble(closePrice);
    }

    public double getPriceChangePercent() {
        return Double.parseDouble(priceChangePercent);
    }
}
