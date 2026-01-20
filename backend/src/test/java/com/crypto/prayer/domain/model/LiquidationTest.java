package com.crypto.prayer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Liquidation 도메인 모델")
class LiquidationTest {

    @Nested
    @DisplayName("of 팩토리 메서드")
    class OfFactory {

        @Test
        @DisplayName("SELL_주문은_LONG_청산이다")
        void SELL_주문은_LONG_청산이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 1.0, 50000.0);

            assertEquals("BTCUSDT", liquidation.symbol());
            assertEquals(Liquidation.LiquidationSide.LONG, liquidation.side());
            assertEquals(1.0, liquidation.quantity());
            assertEquals(50000.0, liquidation.price());
            assertEquals(50000.0, liquidation.usdValue());
        }

        @Test
        @DisplayName("BUY_주문은_SHORT_청산이다")
        void BUY_주문은_SHORT_청산이다() {
            Liquidation liquidation = Liquidation.of("ETHUSDT", "BUY", 10.0, 3000.0);

            assertEquals("ETHUSDT", liquidation.symbol());
            assertEquals(Liquidation.LiquidationSide.SHORT, liquidation.side());
            assertEquals(10.0, liquidation.quantity());
            assertEquals(3000.0, liquidation.price());
            assertEquals(30000.0, liquidation.usdValue());
        }

        @Test
        @DisplayName("소문자_SELL도_LONG_청산이다")
        void 소문자_SELL도_LONG_청산이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "sell", 1.0, 50000.0);

            assertEquals(Liquidation.LiquidationSide.LONG, liquidation.side());
        }

        @Test
        @DisplayName("USD_가치는_수량과_가격의_곱이다")
        void USD_가치는_수량과_가격의_곱이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 2.5, 40000.0);

            assertEquals(100000.0, liquidation.usdValue());
        }

        @Test
        @DisplayName("timestamp가_생성된다")
        void timestamp가_생성된다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 1.0, 50000.0);

            assertNotNull(liquidation.timestamp());
        }
    }

    @Nested
    @DisplayName("isLarge 메서드")
    class IsLarge {

        @Test
        @DisplayName("USD_100000_이상이면_대형_청산이다")
        void USD_100000_이상이면_대형_청산이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 2.0, 50000.0);

            assertTrue(liquidation.isLarge());
        }

        @Test
        @DisplayName("USD_100000_미만이면_대형_청산이_아니다")
        void USD_100000_미만이면_대형_청산이_아니다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 1.0, 50000.0);

            assertFalse(liquidation.isLarge());
        }

        @Test
        @DisplayName("정확히_USD_100000이면_대형_청산이다")
        void 정확히_USD_100000이면_대형_청산이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 2.5, 40000.0);

            assertTrue(liquidation.isLarge());
        }
    }

    @Nested
    @DisplayName("formattedValue 메서드")
    class FormattedValue {

        @Test
        @DisplayName("백만_이상은_M_형식이다")
        void 백만_이상은_M_형식이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 100.0, 50000.0);

            assertEquals("$5.00M", liquidation.formattedValue());
        }

        @Test
        @DisplayName("천_이상은_K_형식이다")
        void 천_이상은_K_형식이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 1.0, 50000.0);

            assertEquals("$50.0K", liquidation.formattedValue());
        }

        @Test
        @DisplayName("천_미만은_달러_형식이다")
        void 천_미만은_달러_형식이다() {
            Liquidation liquidation = Liquidation.of("BTCUSDT", "SELL", 0.01, 50000.0);

            assertEquals("$500", liquidation.formattedValue());
        }
    }

    @Nested
    @DisplayName("LiquidationSide enum")
    class LiquidationSideEnum {

        @Test
        @DisplayName("LONG과_SHORT_두_가지_값이_있다")
        void LONG과_SHORT_두_가지_값이_있다() {
            assertEquals(2, Liquidation.LiquidationSide.values().length);
            assertNotNull(Liquidation.LiquidationSide.LONG);
            assertNotNull(Liquidation.LiquidationSide.SHORT);
        }
    }
}
