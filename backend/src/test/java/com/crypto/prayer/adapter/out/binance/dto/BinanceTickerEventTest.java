package com.crypto.prayer.adapter.out.binance.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BinanceTickerEvent DTO")
class BinanceTickerEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("JSON 파싱")
    class JsonParsing {

        @Test
        @DisplayName("바이낸스_24시간_티커_JSON을_파싱한다")
        void 바이낸스_24시간_티커_JSON을_파싱한다() throws Exception {
            String json = """
                {
                  "e": "24hrTicker",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "p": "100.00000000",
                  "P": "0.50",
                  "c": "20100.00000000",
                  "h": "20500.00000000",
                  "l": "19800.00000000",
                  "v": "100000.00000000",
                  "q": "2000000000.00000000"
                }
                """;

            BinanceTickerEvent event = objectMapper.readValue(json, BinanceTickerEvent.class);

            assertEquals("24hrTicker", event.eventType());
            assertEquals(1672515782136L, event.eventTime());
            assertEquals("BTCUSDT", event.symbol());
            assertEquals(20100.0, event.getPrice());
            assertEquals(0.50, event.getPriceChangePercent());
        }

        @Test
        @DisplayName("음수_변동률을_파싱한다")
        void 음수_변동률을_파싱한다() throws Exception {
            String json = """
                {
                  "e": "24hrTicker",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "P": "-2.35",
                  "c": "19500.00000000"
                }
                """;

            BinanceTickerEvent event = objectMapper.readValue(json, BinanceTickerEvent.class);

            assertEquals(-2.35, event.getPriceChangePercent());
            assertEquals(19500.0, event.getPrice());
        }

        @Test
        @DisplayName("알수없는_필드는_무시한다")
        void 알수없는_필드는_무시한다() throws Exception {
            String json = """
                {
                  "e": "24hrTicker",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "P": "0.50",
                  "c": "20100.00000000",
                  "unknownField": "value"
                }
                """;

            assertDoesNotThrow(() -> objectMapper.readValue(json, BinanceTickerEvent.class));
        }
    }

    @Nested
    @DisplayName("값 추출 메서드")
    class ValueExtraction {

        @Test
        @DisplayName("getPrice는_closePrice를_반환한다")
        void getPrice는_closePrice를_반환한다() {
            BinanceTickerEvent event = new BinanceTickerEvent(
                "24hrTicker", 123L, "BTCUSDT", "42150.50", "2.5", "43000", "41000", "100000"
            );

            assertEquals(42150.50, event.getPrice());
        }

        @Test
        @DisplayName("getPriceChangePercent는_변동률을_반환한다")
        void getPriceChangePercent는_변동률을_반환한다() {
            BinanceTickerEvent event = new BinanceTickerEvent(
                "24hrTicker", 123L, "BTCUSDT", "42150.50", "-3.25", "43000", "41000", "100000"
            );

            assertEquals(-3.25, event.getPriceChangePercent());
        }
    }
}
