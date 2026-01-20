package com.crypto.prayer.application.port.in;

import com.crypto.prayer.domain.model.Prayer;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrayerUseCase 인터페이스")
class PrayerUseCaseTest {

    @Nested
    @DisplayName("pray 메서드")
    class Pray {

        @Test
        @DisplayName("pray는_Prayer를_반환한다")
        void pray는_Prayer를_반환한다() {
            PrayerUseCase useCase = new TestPrayerUseCase();

            Prayer result = useCase.pray(Side.UP, "session-123");

            assertNotNull(result);
            assertEquals(Side.UP, result.side());
            assertEquals("session-123", result.sessionId());
        }
    }

    @Nested
    @DisplayName("prayBatch 메서드")
    class PrayBatch {

        @Test
        @DisplayName("prayBatch는_count만큼_기도를_등록한다")
        void prayBatch는_count만큼_기도를_등록한다() {
            TestPrayerUseCase useCase = new TestPrayerUseCase();

            useCase.prayBatch(Side.DOWN, "session-456", 5);

            assertEquals(5, useCase.getBatchCount());
        }
    }

    /**
     * 테스트용 구현체
     */
    private static class TestPrayerUseCase implements PrayerUseCase {
        private int batchCount = 0;

        @Override
        public Prayer pray(Side side, String sessionId) {
            return Prayer.create(side, sessionId);
        }

        @Override
        public void prayBatch(Side side, String sessionId, int count) {
            this.batchCount = count;
        }

        public int getBatchCount() {
            return batchCount;
        }
    }
}
