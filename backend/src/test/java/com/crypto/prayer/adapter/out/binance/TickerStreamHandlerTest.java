package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.in.websocket.dto.TickerMessage;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.crypto.prayer.domain.model.Ticker;
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

@DisplayName("TickerStreamHandler")
@ExtendWith(MockitoExtension.class)
class TickerStreamHandlerTest {

    @Mock
    private BinanceWebSocketClient webSocketClient;

    @Mock
    private BroadcastPort broadcastPort;

    private BinanceConfig config;
    private ObjectMapper objectMapper;
    private TickerStreamHandler handler;

    @BeforeEach
    void setUp() {
        config = new BinanceConfig();
        objectMapper = new ObjectMapper();
        handler = new TickerStreamHandler(webSocketClient, config, broadcastPort, objectMapper);
    }

    @Nested
    @DisplayName("handleMessage 메서드")
    class HandleMessage {

        @Test
        @DisplayName("티커_메시지를_파싱하여_브로드캐스트한다")
        void 티커_메시지를_파싱하여_브로드캐스트한다() {
            String json = """
                {
                  "e": "24hrTicker",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "P": "2.50",
                  "c": "42150.50"
                }
                """;

            handler.handleMessage(json);

            ArgumentCaptor<TickerMessage> captor = ArgumentCaptor.forClass(TickerMessage.class);
            verify(broadcastPort).broadcastTicker(captor.capture());

            TickerMessage captured = captor.getValue();
            assertEquals("BTCUSDT", captured.symbol());
            assertEquals(42150.50, captured.price());
            assertEquals(2.50, captured.priceChange24h());
        }

        @Test
        @DisplayName("음수_변동률도_정상적으로_처리된다")
        void 음수_변동률도_정상적으로_처리된다() {
            String json = """
                {
                  "e": "24hrTicker",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "P": "-3.25",
                  "c": "40000.00"
                }
                """;

            handler.handleMessage(json);

            ArgumentCaptor<TickerMessage> captor = ArgumentCaptor.forClass(TickerMessage.class);
            verify(broadcastPort).broadcastTicker(captor.capture());

            TickerMessage captured = captor.getValue();
            assertEquals(-3.25, captured.priceChange24h());
        }

        @Test
        @DisplayName("잘못된_JSON은_예외없이_무시된다")
        void 잘못된_JSON은_예외없이_무시된다() {
            String invalidJson = "{ invalid json }";

            assertDoesNotThrow(() -> handler.handleMessage(invalidJson));
            verify(broadcastPort, never()).broadcastTicker(any());
        }
    }

    @Nested
    @DisplayName("getLatestTicker 메서드")
    class GetLatestTicker {

        @Test
        @DisplayName("마지막_수신된_티커를_반환한다")
        void 마지막_수신된_티커를_반환한다() {
            String json = """
                {
                  "e": "24hrTicker",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "P": "2.50",
                  "c": "42150.50"
                }
                """;

            handler.handleMessage(json);

            Ticker latest = handler.getLatestTicker();
            assertNotNull(latest);
            assertEquals("BTCUSDT", latest.symbol());
            assertEquals(42150.50, latest.price());
        }

        @Test
        @DisplayName("메시지_수신_전에는_null을_반환한다")
        void 메시지_수신_전에는_null을_반환한다() {
            assertNull(handler.getLatestTicker());
        }
    }
}
