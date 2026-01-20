package com.crypto.prayer.application.port.out;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrayerCountPort 인터페이스")
class PrayerCountPortTest {

    private TestPrayerCountPort port;

    @BeforeEach
    void setUp() {
        port = new TestPrayerCountPort();
    }

    @Nested
    @DisplayName("increment 메서드")
    class Increment {

        @Test
        @DisplayName("UP_증가시_upCount가_증가한다")
        void UP_증가시_upCount가_증가한다() {
            long result = port.increment(Side.UP, 5L);

            assertEquals(5L, result);
            assertEquals(5L, port.getCount().upCount());
        }

        @Test
        @DisplayName("DOWN_증가시_downCount가_증가한다")
        void DOWN_증가시_downCount가_증가한다() {
            long result = port.increment(Side.DOWN, 3L);

            assertEquals(3L, result);
            assertEquals(3L, port.getCount().downCount());
        }
    }

    @Nested
    @DisplayName("getCount 메서드")
    class GetCount {

        @Test
        @DisplayName("getCount는_현재_카운트를_반환한다")
        void getCount는_현재_카운트를_반환한다() {
            port.increment(Side.UP, 10L);
            port.increment(Side.DOWN, 5L);

            PrayerCount count = port.getCount();

            assertEquals(10L, count.upCount());
            assertEquals(5L, count.downCount());
        }
    }

    @Nested
    @DisplayName("merge 메서드")
    class Merge {

        @Test
        @DisplayName("merge는_delta를_현재_카운트에_합친다")
        void merge는_delta를_현재_카운트에_합친다() {
            port.increment(Side.UP, 10L);
            PrayerCount delta = new PrayerCount(5L, 3L);

            port.merge(delta);

            assertEquals(15L, port.getCount().upCount());
            assertEquals(3L, port.getCount().downCount());
        }
    }

    @Nested
    @DisplayName("isAvailable 메서드")
    class IsAvailable {

        @Test
        @DisplayName("isAvailable은_연결_상태를_반환한다")
        void isAvailable은_연결_상태를_반환한다() {
            assertTrue(port.isAvailable());
        }
    }

    private static class TestPrayerCountPort implements PrayerCountPort {
        private long upCount = 0;
        private long downCount = 0;

        @Override
        public long increment(Side side, long delta) {
            return switch (side) {
                case UP -> upCount += delta;
                case DOWN -> downCount += delta;
            };
        }

        @Override
        public PrayerCount getCount() {
            return new PrayerCount(upCount, downCount);
        }

        @Override
        public void merge(PrayerCount delta) {
            upCount += delta.upCount();
            downCount += delta.downCount();
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
