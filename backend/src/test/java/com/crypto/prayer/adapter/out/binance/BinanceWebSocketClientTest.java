package com.crypto.prayer.adapter.out.binance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BinanceWebSocketClient")
class BinanceWebSocketClientTest {

    private BinanceConfig config;
    private BinanceWebSocketClient client;

    @BeforeEach
    void setUp() {
        config = new BinanceConfig();
        client = new BinanceWebSocketClient(config);
    }

    @Nested
    @DisplayName("생성자")
    class Constructor {

        @Test
        @DisplayName("설정으로_생성된다")
        void 설정으로_생성된다() {
            assertNotNull(client);
        }
    }

    @Nested
    @DisplayName("connect 메서드")
    class Connect {

        @Test
        @DisplayName("스트림_이름과_URL로_연결을_시도한다")
        void 스트림_이름과_URL로_연결을_시도한다() {
            AtomicBoolean called = new AtomicBoolean(false);

            // 실제 연결은 하지 않고 메서드 호출만 확인
            assertDoesNotThrow(() -> {
                // 존재하지 않는 URL로 연결 시도 (실제로는 실패하지만 예외는 발생하지 않음)
                client.connect("test", "ws://invalid-url-for-test:1234", message -> {
                    called.set(true);
                });
            });
        }
    }

    @Nested
    @DisplayName("disconnect 메서드")
    class Disconnect {

        @Test
        @DisplayName("존재하지_않는_스트림_연결_해제시_예외없이_처리된다")
        void 존재하지_않는_스트림_연결_해제시_예외없이_처리된다() {
            assertDoesNotThrow(() -> client.disconnect("nonexistent"));
        }
    }

    @Nested
    @DisplayName("isConnected 메서드")
    class IsConnected {

        @Test
        @DisplayName("연결_전에는_false를_반환한다")
        void 연결_전에는_false를_반환한다() {
            assertFalse(client.isConnected("test"));
        }
    }
}
