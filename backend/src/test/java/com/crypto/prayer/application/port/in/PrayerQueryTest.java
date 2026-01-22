package com.crypto.prayer.application.port.in;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.PrayerStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrayerQuery 인터페이스")
class PrayerQueryTest {

    @Nested
    @DisplayName("getTodayCount 메서드")
    class GetTodayCount {

        @Test
        @DisplayName("getTodayCount는_PrayerCount를_반환한다")
        void getTodayCount는_PrayerCount를_반환한다() {
            PrayerQuery query = new TestPrayerQuery();

            PrayerCount result = query.getTodayCount();

            assertNotNull(result);
            assertEquals(100L, result.upCount());
            assertEquals(50L, result.downCount());
        }
    }

    @Nested
    @DisplayName("getCurrentStats 메서드")
    class GetCurrentStats {

        @Test
        @DisplayName("getCurrentStats는_PrayerStats를_반환한다")
        void getCurrentStats는_PrayerStats를_반환한다() {
            PrayerQuery query = new TestPrayerQuery();

            PrayerStats result = query.getCurrentStats();

            assertNotNull(result);
            assertEquals(100L, result.count().upCount());
            assertEquals(10.0, result.upRpm(), 0.001);
            assertEquals(5.0, result.downRpm(), 0.001);
        }
    }

    private static class TestPrayerQuery implements PrayerQuery {
        @Override
        public PrayerCount getTodayCount() {
            return new PrayerCount(100L, 50L);
        }

        @Override
        public PrayerStats getCurrentStats() {
            return PrayerStats.create(new PrayerCount(100L, 50L), 10.0, 5.0);
        }
    }
}
