package com.crypto.prayer.adapter.out.binance.reconnect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExponentialBackoff")
class ExponentialBackoffTest {

    @Nested
    @DisplayName("기본 생성자")
    class DefaultConstructor {

        @Test
        @DisplayName("기본값으로_생성된다")
        void 기본값으로_생성된다() {
            ExponentialBackoff backoff = new ExponentialBackoff();

            // 첫 번째 딜레이는 약 1초 (jitter로 인해 ±10%)
            long delay = backoff.nextDelayMs();
            assertTrue(delay >= 900 && delay <= 1100, "첫 딜레이는 900~1100ms 사이여야 합니다: " + delay);
        }
    }

    @Nested
    @DisplayName("nextDelayMs 메서드")
    class NextDelayMs {

        @Test
        @DisplayName("지수적으로_증가한다")
        void 지수적으로_증가한다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0, 0.0);

            assertEquals(1000, backoff.nextDelayMs());
            assertEquals(2000, backoff.nextDelayMs());
            assertEquals(4000, backoff.nextDelayMs());
            assertEquals(8000, backoff.nextDelayMs());
        }

        @Test
        @DisplayName("최대_딜레이를_초과하지_않는다")
        void 최대_딜레이를_초과하지_않는다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0, 0.0);

            // 1, 2, 4, 8, 16, 32 -> 32는 30을 초과하므로 30
            for (int i = 0; i < 5; i++) {
                backoff.nextDelayMs();
            }
            assertEquals(30000, backoff.nextDelayMs()); // 6번째
            assertEquals(30000, backoff.nextDelayMs()); // 7번째도 최대값
        }

        @Test
        @DisplayName("Jitter가_적용된다")
        void Jitter가_적용된다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0, 0.1);

            long delay = backoff.nextDelayMs();
            // 1000 ± 10% = 900 ~ 1100
            assertTrue(delay >= 900 && delay <= 1100, "Jitter 적용 후 딜레이: " + delay);
        }

        @Test
        @DisplayName("attempt가_증가한다")
        void attempt가_증가한다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0, 0.0);

            assertEquals(0, backoff.getAttempt());
            backoff.nextDelayMs();
            assertEquals(1, backoff.getAttempt());
            backoff.nextDelayMs();
            assertEquals(2, backoff.getAttempt());
        }
    }

    @Nested
    @DisplayName("reset 메서드")
    class Reset {

        @Test
        @DisplayName("attempt를_0으로_초기화한다")
        void attempt를_0으로_초기화한다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0, 0.0);

            backoff.nextDelayMs();
            backoff.nextDelayMs();
            assertEquals(2, backoff.getAttempt());

            backoff.reset();
            assertEquals(0, backoff.getAttempt());
        }

        @Test
        @DisplayName("리셋_후_딜레이가_처음부터_시작한다")
        void 리셋_후_딜레이가_처음부터_시작한다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 2.0, 0.0);

            backoff.nextDelayMs(); // 1000
            backoff.nextDelayMs(); // 2000
            backoff.nextDelayMs(); // 4000

            backoff.reset();

            assertEquals(1000, backoff.nextDelayMs()); // 다시 1000부터
        }
    }

    @Nested
    @DisplayName("커스텀 설정")
    class CustomSettings {

        @Test
        @DisplayName("초기_딜레이를_설정할_수_있다")
        void 초기_딜레이를_설정할_수_있다() {
            ExponentialBackoff backoff = new ExponentialBackoff(500, 30000, 2.0, 0.0);

            assertEquals(500, backoff.nextDelayMs());
        }

        @Test
        @DisplayName("배수를_설정할_수_있다")
        void 배수를_설정할_수_있다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 30000, 3.0, 0.0);

            assertEquals(1000, backoff.nextDelayMs());
            assertEquals(3000, backoff.nextDelayMs());
            assertEquals(9000, backoff.nextDelayMs());
        }

        @Test
        @DisplayName("최대_딜레이를_설정할_수_있다")
        void 최대_딜레이를_설정할_수_있다() {
            ExponentialBackoff backoff = new ExponentialBackoff(1000, 5000, 2.0, 0.0);

            backoff.nextDelayMs(); // 1000
            backoff.nextDelayMs(); // 2000
            backoff.nextDelayMs(); // 4000
            assertEquals(5000, backoff.nextDelayMs()); // 8000 -> 5000 (max)
        }
    }
}
