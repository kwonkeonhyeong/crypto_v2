package com.crypto.prayer.adapter.out.binance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "binance")
public class BinanceConfig {

    private String liquidationStreamUrl = "wss://fstream.binance.com/ws/!forceOrder@arr";
    private String tickerStreamUrl = "wss://fstream.binance.com/ws/btcusdt@ticker";

    private int reconnectInitialDelayMs = 1000;
    private int reconnectMaxDelayMs = 30000;

    public String getLiquidationStreamUrl() {
        return liquidationStreamUrl;
    }

    public void setLiquidationStreamUrl(String liquidationStreamUrl) {
        this.liquidationStreamUrl = liquidationStreamUrl;
    }

    public String getTickerStreamUrl() {
        return tickerStreamUrl;
    }

    public void setTickerStreamUrl(String tickerStreamUrl) {
        this.tickerStreamUrl = tickerStreamUrl;
    }

    public int getReconnectInitialDelayMs() {
        return reconnectInitialDelayMs;
    }

    public void setReconnectInitialDelayMs(int reconnectInitialDelayMs) {
        this.reconnectInitialDelayMs = reconnectInitialDelayMs;
    }

    public int getReconnectMaxDelayMs() {
        return reconnectMaxDelayMs;
    }

    public void setReconnectMaxDelayMs(int reconnectMaxDelayMs) {
        this.reconnectMaxDelayMs = reconnectMaxDelayMs;
    }
}
