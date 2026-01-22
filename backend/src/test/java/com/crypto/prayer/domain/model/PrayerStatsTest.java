package com.crypto.prayer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrayerStats record")
class PrayerStatsTest {

    @Nested
    @DisplayName("create 팩토리 메서드")
    class Create {

        @Test
        @DisplayName("create는_count를_설정한다")
        void create는_count를_설정한다() {
            PrayerCount count = new PrayerCount(100L, 50L);

            PrayerStats stats = PrayerStats.create(count, 10.0, 5.0);

            assertEquals(count, stats.count());
        }

        @Test
        @DisplayName("create는_upRpm을_설정한다")
        void create는_upRpm을_설정한다() {
            PrayerCount count = new PrayerCount(100L, 50L);

            PrayerStats stats = PrayerStats.create(count, 10.0, 5.0);

            assertEquals(10.0, stats.upRpm(), 0.001);
        }

        @Test
        @DisplayName("create는_downRpm을_설정한다")
        void create는_downRpm을_설정한다() {
            PrayerCount count = new PrayerCount(100L, 50L);

            PrayerStats stats = PrayerStats.create(count, 10.0, 5.0);

            assertEquals(5.0, stats.downRpm(), 0.001);
        }

        @Test
        @DisplayName("create는_timestamp를_현재_시간으로_설정한다")
        void create는_timestamp를_현재_시간으로_설정한다() {
            long before = System.currentTimeMillis();
            PrayerStats stats = PrayerStats.create(PrayerCount.zero(), 0, 0);
            long after = System.currentTimeMillis();

            assertTrue(stats.timestamp() >= before);
            assertTrue(stats.timestamp() <= after);
        }
    }

    @Nested
    @DisplayName("totalRpm 메서드")
    class TotalRpm {

        @Test
        @DisplayName("totalRpm은_upRpm과_downRpm의_합이다")
        void totalRpm은_upRpm과_downRpm의_합이다() {
            PrayerStats stats = PrayerStats.create(PrayerCount.zero(), 100.5, 50.5);

            assertEquals(151.0, stats.totalRpm(), 0.001);
        }

        @Test
        @DisplayName("totalRpm은_0일_수_있다")
        void totalRpm은_0일_수_있다() {
            PrayerStats stats = PrayerStats.create(PrayerCount.zero(), 0, 0);

            assertEquals(0.0, stats.totalRpm(), 0.001);
        }
    }

    @Nested
    @DisplayName("record 생성자")
    class Constructor {

        @Test
        @DisplayName("모든_필드를_직접_지정하여_생성할_수_있다")
        void 모든_필드를_직접_지정하여_생성할_수_있다() {
            PrayerCount count = new PrayerCount(200L, 100L);
            double upRpm = 25.5;
            double downRpm = 15.5;
            long timestamp = 1705381234567L;

            PrayerStats stats = new PrayerStats(count, upRpm, downRpm, timestamp);

            assertEquals(count, stats.count());
            assertEquals(upRpm, stats.upRpm(), 0.001);
            assertEquals(downRpm, stats.downRpm(), 0.001);
            assertEquals(timestamp, stats.timestamp());
        }
    }
}
