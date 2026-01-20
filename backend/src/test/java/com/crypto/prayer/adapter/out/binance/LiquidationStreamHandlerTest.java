package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.in.websocket.dto.LiquidationMessage;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LiquidationStreamHandler")
@ExtendWith(MockitoExtension.class)
class LiquidationStreamHandlerTest {

    @Mock
    private BinanceWebSocketClient webSocketClient;

    @Mock
    private BroadcastPort broadcastPort;

    private BinanceConfig config;
    private ObjectMapper objectMapper;
    private LiquidationStreamHandler handler;

    @BeforeEach
    void setUp() {
        config = new BinanceConfig();
        objectMapper = new ObjectMapper();
        handler = new LiquidationStreamHandler(webSocketClient, config, broadcastPort, objectMapper);
    }

    @Nested
    @DisplayName("handleMessage 메서드")
    class HandleMessage {

        @Test
        @DisplayName("청산_메시지를_파싱하여_브로드캐스트한다")
        void 청산_메시지를_파싱하여_브로드캐스트한다() {
            String json = """
                {
                  "e": "forceOrder",
                  "E": 1568014460893,
                  "o": {
                    "s": "BTCUSDT",
                    "S": "SELL",
                    "q": "0.014",
                    "p": "9910",
                    "ap": "50000",
                    "X": "FILLED",
                    "z": "1.0"
                  }
                }
                """;

            handler.handleMessage(json);

            ArgumentCaptor<LiquidationMessage> captor = ArgumentCaptor.forClass(LiquidationMessage.class);
            verify(broadcastPort).broadcastLiquidation(captor.capture());

            LiquidationMessage captured = captor.getValue();
            assertEquals("BTCUSDT", captured.symbol());
            assertEquals("LONG", captured.side()); // SELL -> LONG 청산
            assertEquals(1.0, captured.quantity());
            assertEquals(50000.0, captured.price());
            assertEquals(50000.0, captured.usdValue());
        }

        @Test
        @DisplayName("BUY_청산은_SHORT_청산으로_변환된다")
        void BUY_청산은_SHORT_청산으로_변환된다() {
            String json = """
                {
                  "e": "forceOrder",
                  "E": 1568014460893,
                  "o": {
                    "s": "ETHUSDT",
                    "S": "BUY",
                    "q": "10",
                    "p": "3000",
                    "ap": "3000",
                    "X": "FILLED",
                    "z": "10"
                  }
                }
                """;

            handler.handleMessage(json);

            ArgumentCaptor<LiquidationMessage> captor = ArgumentCaptor.forClass(LiquidationMessage.class);
            verify(broadcastPort).broadcastLiquidation(captor.capture());

            LiquidationMessage captured = captor.getValue();
            assertEquals("SHORT", captured.side()); // BUY -> SHORT 청산
        }

        @Test
        @DisplayName("대형_청산은_isLarge가_true이다")
        void 대형_청산은_isLarge가_true이다() {
            String json = """
                {
                  "e": "forceOrder",
                  "E": 1568014460893,
                  "o": {
                    "s": "BTCUSDT",
                    "S": "SELL",
                    "q": "10",
                    "p": "50000",
                    "ap": "50000",
                    "X": "FILLED",
                    "z": "2.5"
                  }
                }
                """;

            handler.handleMessage(json);

            ArgumentCaptor<LiquidationMessage> captor = ArgumentCaptor.forClass(LiquidationMessage.class);
            verify(broadcastPort).broadcastLiquidation(captor.capture());

            LiquidationMessage captured = captor.getValue();
            assertTrue(captured.isLarge()); // 2.5 * 50000 = 125000 >= 100000
        }

        @Test
        @DisplayName("잘못된_JSON은_예외없이_무시된다")
        void 잘못된_JSON은_예외없이_무시된다() {
            String invalidJson = "{ invalid json }";

            assertDoesNotThrow(() -> handler.handleMessage(invalidJson));
            verify(broadcastPort, never()).broadcastLiquidation(any());
        }
    }
}
