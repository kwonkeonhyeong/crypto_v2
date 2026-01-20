package com.crypto.prayer.adapter.out.binance.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BinanceLiquidationEvent DTO")
class BinanceLiquidationEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("JSON 파싱")
    class JsonParsing {

        @Test
        @DisplayName("바이낸스_청산_JSON을_파싱한다")
        void 바이낸스_청산_JSON을_파싱한다() throws Exception {
            String json = """
                {
                  "e": "forceOrder",
                  "E": 1568014460893,
                  "o": {
                    "s": "BTCUSDT",
                    "S": "SELL",
                    "o": "LIMIT",
                    "f": "IOC",
                    "q": "0.014",
                    "p": "9910",
                    "ap": "9910",
                    "X": "FILLED",
                    "l": "0.014",
                    "z": "0.014",
                    "T": 1568014460893
                  }
                }
                """;

            BinanceLiquidationEvent event = objectMapper.readValue(json, BinanceLiquidationEvent.class);

            assertEquals("forceOrder", event.eventType());
            assertEquals(1568014460893L, event.eventTime());
            assertEquals("BTCUSDT", event.getSymbol());
            assertEquals("SELL", event.getSide());
            assertEquals(0.014, event.getQuantity());
            assertEquals(9910.0, event.getPrice());
        }

        @Test
        @DisplayName("BUY_청산_JSON을_파싱한다")
        void BUY_청산_JSON을_파싱한다() throws Exception {
            String json = """
                {
                  "e": "forceOrder",
                  "E": 1568014460893,
                  "o": {
                    "s": "ETHUSDT",
                    "S": "BUY",
                    "o": "LIMIT",
                    "f": "IOC",
                    "q": "10.5",
                    "p": "3000",
                    "ap": "2980.50",
                    "X": "FILLED",
                    "l": "10.5",
                    "z": "10.5",
                    "T": 1568014460893
                  }
                }
                """;

            BinanceLiquidationEvent event = objectMapper.readValue(json, BinanceLiquidationEvent.class);

            assertEquals("ETHUSDT", event.getSymbol());
            assertEquals("BUY", event.getSide());
            assertEquals(10.5, event.getQuantity());
            assertEquals(2980.50, event.getPrice());
        }

        @Test
        @DisplayName("알수없는_필드는_무시한다")
        void 알수없는_필드는_무시한다() throws Exception {
            String json = """
                {
                  "e": "forceOrder",
                  "E": 1568014460893,
                  "unknownField": "value",
                  "o": {
                    "s": "BTCUSDT",
                    "S": "SELL",
                    "q": "0.014",
                    "p": "9910",
                    "ap": "9910",
                    "X": "FILLED",
                    "z": "0.014",
                    "extraField": 123
                  }
                }
                """;

            assertDoesNotThrow(() -> objectMapper.readValue(json, BinanceLiquidationEvent.class));
        }
    }

    @Nested
    @DisplayName("값 추출 메서드")
    class ValueExtraction {

        @Test
        @DisplayName("getQuantity는_filledQty를_반환한다")
        void getQuantity는_filledQty를_반환한다() {
            BinanceLiquidationEvent.Order order = new BinanceLiquidationEvent.Order(
                "BTCUSDT", "SELL", "1.5", "50000", "49500", "FILLED", "1.25"
            );
            BinanceLiquidationEvent event = new BinanceLiquidationEvent("forceOrder", 123L, order);

            assertEquals(1.25, event.getQuantity());
        }

        @Test
        @DisplayName("getPrice는_avgPrice를_반환한다")
        void getPrice는_avgPrice를_반환한다() {
            BinanceLiquidationEvent.Order order = new BinanceLiquidationEvent.Order(
                "BTCUSDT", "SELL", "1.5", "50000", "49500", "FILLED", "1.25"
            );
            BinanceLiquidationEvent event = new BinanceLiquidationEvent("forceOrder", 123L, order);

            assertEquals(49500.0, event.getPrice());
        }
    }
}
