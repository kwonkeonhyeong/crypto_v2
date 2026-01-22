package com.crypto.prayer.adapter.out.binance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BinanceConfig")
class BinanceConfigTest {

    @Nested
    @DisplayName("기본값")
    class DefaultValues {

        @Test
        @DisplayName("청산_스트림_URL_기본값이_설정된다")
        void 청산_스트림_URL_기본값이_설정된다() {
            BinanceConfig config = new BinanceConfig();

            assertEquals("wss://fstream.binance.com/ws/!forceOrder@arr", config.getLiquidationStreamUrl());
        }

        @Test
        @DisplayName("티커_스트림_URL_기본값이_설정된다")
        void 티커_스트림_URL_기본값이_설정된다() {
            BinanceConfig config = new BinanceConfig();

            assertEquals("wss://fstream.binance.com/ws/btcusdt@ticker", config.getTickerStreamUrl());
        }

        @Test
        @DisplayName("재연결_초기_딜레이_기본값이_1000ms이다")
        void 재연결_초기_딜레이_기본값이_1000ms이다() {
            BinanceConfig config = new BinanceConfig();

            assertEquals(1000, config.getReconnectInitialDelayMs());
        }

        @Test
        @DisplayName("재연결_최대_딜레이_기본값이_30000ms이다")
        void 재연결_최대_딜레이_기본값이_30000ms이다() {
            BinanceConfig config = new BinanceConfig();

            assertEquals(30000, config.getReconnectMaxDelayMs());
        }
    }

    @Nested
    @DisplayName("설정 변경")
    class SettingChanges {

        @Test
        @DisplayName("청산_스트림_URL을_변경할_수_있다")
        void 청산_스트림_URL을_변경할_수_있다() {
            BinanceConfig config = new BinanceConfig();
            config.setLiquidationStreamUrl("wss://testnet.binance.com/ws/!forceOrder@arr");

            assertEquals("wss://testnet.binance.com/ws/!forceOrder@arr", config.getLiquidationStreamUrl());
        }

        @Test
        @DisplayName("티커_스트림_URL을_변경할_수_있다")
        void 티커_스트림_URL을_변경할_수_있다() {
            BinanceConfig config = new BinanceConfig();
            config.setTickerStreamUrl("wss://testnet.binance.com/ws/btcusdt@ticker");

            assertEquals("wss://testnet.binance.com/ws/btcusdt@ticker", config.getTickerStreamUrl());
        }

        @Test
        @DisplayName("재연결_딜레이를_변경할_수_있다")
        void 재연결_딜레이를_변경할_수_있다() {
            BinanceConfig config = new BinanceConfig();
            config.setReconnectInitialDelayMs(500);
            config.setReconnectMaxDelayMs(10000);

            assertEquals(500, config.getReconnectInitialDelayMs());
            assertEquals(10000, config.getReconnectMaxDelayMs());
        }
    }
}
