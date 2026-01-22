package com.crypto.prayer.adapter.in.websocket.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter();
    }

    @Nested
    @DisplayName("tryConsume()")
    class TryConsume {

        @Test
        @DisplayName("버스트_제한_내에서는_요청을_허용한다")
        void 버스트_제한_내에서는_요청을_허용한다() {
            String clientId = "client-1";

            // 버스트 제한(20)까지 모두 허용
            for (int i = 0; i < 20; i++) {
                assertThat(rateLimiter.tryConsume(clientId))
                    .as("Request %d should be allowed", i + 1)
                    .isTrue();
            }
        }

        @Test
        @DisplayName("버스트_제한을_초과하면_요청을_거부한다")
        void 버스트_제한을_초과하면_요청을_거부한다() {
            String clientId = "client-2";

            // 버스트 제한 소진
            for (int i = 0; i < 20; i++) {
                rateLimiter.tryConsume(clientId);
            }

            // 21번째 요청은 거부
            assertThat(rateLimiter.tryConsume(clientId)).isFalse();
        }

        @Test
        @DisplayName("클라이언트별로_독립적인_버킷을_관리한다")
        void 클라이언트별로_독립적인_버킷을_관리한다() {
            String client1 = "client-1";
            String client2 = "client-2";

            // client1 버스트 소진
            for (int i = 0; i < 20; i++) {
                rateLimiter.tryConsume(client1);
            }

            // client2는 여전히 허용
            assertThat(rateLimiter.tryConsume(client2)).isTrue();
        }
    }

    @Nested
    @DisplayName("removeClient()")
    class RemoveClient {

        @Test
        @DisplayName("클라이언트_제거_후_새_버킷으로_시작한다")
        void 클라이언트_제거_후_새_버킷으로_시작한다() {
            String clientId = "client-3";

            // 버스트 소진
            for (int i = 0; i < 20; i++) {
                rateLimiter.tryConsume(clientId);
            }
            assertThat(rateLimiter.tryConsume(clientId)).isFalse();

            // 클라이언트 제거
            rateLimiter.removeClient(clientId);

            // 새 버킷으로 다시 허용
            assertThat(rateLimiter.tryConsume(clientId)).isTrue();
        }
    }

    @Nested
    @DisplayName("cleanupStaleEntries()")
    class CleanupStaleEntries {

        @Test
        @DisplayName("충분한_유휴_시간이_지난_버킷을_정리한다")
        void 충분한_유휴_시간이_지난_버킷을_정리한다() throws InterruptedException {
            String clientId = "client-4";

            // 버킷 생성 및 모든 토큰 소진
            for (int i = 0; i < 20; i++) {
                rateLimiter.tryConsume(clientId);
            }
            // 21번째 요청은 거부됨
            assertThat(rateLimiter.tryConsume(clientId)).isFalse();

            // 짧은 대기 후 정리 (1ms보다 긴 유휴 시간 경과)
            Thread.sleep(5);
            rateLimiter.cleanupStaleEntries(1);

            // 정리 후 새 버킷으로 시작 - 20개 모두 허용
            for (int i = 0; i < 20; i++) {
                assertThat(rateLimiter.tryConsume(clientId))
                    .as("Request %d should be allowed after cleanup", i + 1)
                    .isTrue();
            }
        }
    }
}
