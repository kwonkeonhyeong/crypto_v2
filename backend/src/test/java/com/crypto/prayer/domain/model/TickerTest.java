package com.crypto.prayer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Ticker 도메인 모델")
class TickerTest {

    @Nested
    @DisplayName("of 팩토리 메서드")
    class OfFactory {

        @Test
        @DisplayName("심볼_가격_변동률로_생성한다")
        void 심볼_가격_변동률로_생성한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 2.5);

            assertEquals("BTCUSDT", ticker.symbol());
            assertEquals(42150.50, ticker.price());
            assertEquals(2.5, ticker.priceChange24h());
        }

        @Test
        @DisplayName("timestamp가_생성된다")
        void timestamp가_생성된다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 2.5);

            assertNotNull(ticker.timestamp());
        }

        @Test
        @DisplayName("고가_저가_거래량은_기본값으로_0이다")
        void 고가_저가_거래량은_기본값으로_0이다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 2.5);

            assertEquals(0, ticker.high24h());
            assertEquals(0, ticker.low24h());
            assertEquals(0, ticker.volume24h());
        }
    }

    @Nested
    @DisplayName("isPositive 메서드")
    class IsPositive {

        @Test
        @DisplayName("변동률이_양수면_true를_반환한다")
        void 변동률이_양수면_true를_반환한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 2.5);

            assertTrue(ticker.isPositive());
        }

        @Test
        @DisplayName("변동률이_0이면_true를_반환한다")
        void 변동률이_0이면_true를_반환한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 0.0);

            assertTrue(ticker.isPositive());
        }

        @Test
        @DisplayName("변동률이_음수면_false를_반환한다")
        void 변동률이_음수면_false를_반환한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, -3.2);

            assertFalse(ticker.isPositive());
        }
    }

    @Nested
    @DisplayName("formattedPrice 메서드")
    class FormattedPrice {

        @Test
        @DisplayName("1000_이상은_소수점_둘째자리까지_표시한다")
        void _1000_이상은_소수점_둘째자리까지_표시한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 2.5);

            assertEquals("$42150.50", ticker.formattedPrice());
        }

        @Test
        @DisplayName("1000_미만은_소수점_넷째자리까지_표시한다")
        void _1000_미만은_소수점_넷째자리까지_표시한다() {
            Ticker ticker = Ticker.of("SHIB", 0.00001234, 5.0);

            assertEquals("$0.0000", ticker.formattedPrice());
        }

        @Test
        @DisplayName("정확히_1000은_소수점_둘째자리까지_표시한다")
        void 정확히_1000은_소수점_둘째자리까지_표시한다() {
            Ticker ticker = Ticker.of("ETHUSDT", 1000.00, 1.0);

            assertEquals("$1000.00", ticker.formattedPrice());
        }
    }

    @Nested
    @DisplayName("formattedChange 메서드")
    class FormattedChange {

        @Test
        @DisplayName("양수_변동률은_플러스_기호와_함께_표시한다")
        void 양수_변동률은_플러스_기호와_함께_표시한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 2.5);

            assertEquals("+2.50%", ticker.formattedChange());
        }

        @Test
        @DisplayName("음수_변동률은_마이너스_기호와_함께_표시한다")
        void 음수_변동률은_마이너스_기호와_함께_표시한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, -3.25);

            assertEquals("-3.25%", ticker.formattedChange());
        }

        @Test
        @DisplayName("0_변동률은_플러스_기호와_함께_표시한다")
        void _0_변동률은_플러스_기호와_함께_표시한다() {
            Ticker ticker = Ticker.of("BTCUSDT", 42150.50, 0.0);

            assertEquals("+0.00%", ticker.formattedChange());
        }
    }
}
