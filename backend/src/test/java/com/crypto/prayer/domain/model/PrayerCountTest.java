package com.crypto.prayer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrayerCount record")
class PrayerCountTest {

    @Nested
    @DisplayName("zero 팩토리 메서드")
    class Zero {

        @Test
        @DisplayName("zero는_upCount가_0이다")
        void zero는_upCount가_0이다() {
            PrayerCount count = PrayerCount.zero();

            assertEquals(0L, count.upCount());
        }

        @Test
        @DisplayName("zero는_downCount가_0이다")
        void zero는_downCount가_0이다() {
            PrayerCount count = PrayerCount.zero();

            assertEquals(0L, count.downCount());
        }
    }

    @Nested
    @DisplayName("increment 메서드")
    class Increment {

        @Test
        @DisplayName("incrementUp은_upCount만_증가한다")
        void incrementUp은_upCount만_증가한다() {
            PrayerCount count = new PrayerCount(10L, 5L);

            PrayerCount result = count.incrementUp(3L);

            assertEquals(13L, result.upCount());
            assertEquals(5L, result.downCount());
        }

        @Test
        @DisplayName("incrementDown은_downCount만_증가한다")
        void incrementDown은_downCount만_증가한다() {
            PrayerCount count = new PrayerCount(10L, 5L);

            PrayerCount result = count.incrementDown(7L);

            assertEquals(10L, result.upCount());
            assertEquals(12L, result.downCount());
        }

        @Test
        @DisplayName("increment_UP은_upCount를_증가한다")
        void increment_UP은_upCount를_증가한다() {
            PrayerCount count = new PrayerCount(10L, 5L);

            PrayerCount result = count.increment(Side.UP, 5L);

            assertEquals(15L, result.upCount());
            assertEquals(5L, result.downCount());
        }

        @Test
        @DisplayName("increment_DOWN은_downCount를_증가한다")
        void increment_DOWN은_downCount를_증가한다() {
            PrayerCount count = new PrayerCount(10L, 5L);

            PrayerCount result = count.increment(Side.DOWN, 5L);

            assertEquals(10L, result.upCount());
            assertEquals(10L, result.downCount());
        }

        @Test
        @DisplayName("increment는_불변성을_유지한다")
        void increment는_불변성을_유지한다() {
            PrayerCount original = new PrayerCount(10L, 5L);

            PrayerCount result = original.incrementUp(5L);

            assertEquals(10L, original.upCount());
            assertEquals(15L, result.upCount());
            assertNotSame(original, result);
        }
    }

    @Nested
    @DisplayName("total 메서드")
    class Total {

        @Test
        @DisplayName("total은_upCount와_downCount의_합이다")
        void total은_upCount와_downCount의_합이다() {
            PrayerCount count = new PrayerCount(100L, 50L);

            assertEquals(150L, count.total());
        }

        @Test
        @DisplayName("zero의_total은_0이다")
        void zero의_total은_0이다() {
            assertEquals(0L, PrayerCount.zero().total());
        }
    }

    @Nested
    @DisplayName("ratio 메서드")
    class Ratio {

        @Test
        @DisplayName("upRatio는_upCount_나누기_total이다")
        void upRatio는_upCount_나누기_total이다() {
            PrayerCount count = new PrayerCount(75L, 25L);

            assertEquals(0.75, count.upRatio(), 0.001);
        }

        @Test
        @DisplayName("downRatio는_downCount_나누기_total이다")
        void downRatio는_downCount_나누기_total이다() {
            PrayerCount count = new PrayerCount(75L, 25L);

            assertEquals(0.25, count.downRatio(), 0.001);
        }

        @Test
        @DisplayName("total이_0이면_upRatio는_0점5이다")
        void total이_0이면_upRatio는_0점5이다() {
            PrayerCount count = PrayerCount.zero();

            assertEquals(0.5, count.upRatio(), 0.001);
        }

        @Test
        @DisplayName("total이_0이면_downRatio는_0점5이다")
        void total이_0이면_downRatio는_0점5이다() {
            PrayerCount count = PrayerCount.zero();

            assertEquals(0.5, count.downRatio(), 0.001);
        }

        @Test
        @DisplayName("upRatio와_downRatio의_합은_1이다")
        void upRatio와_downRatio의_합은_1이다() {
            PrayerCount count = new PrayerCount(123L, 456L);

            assertEquals(1.0, count.upRatio() + count.downRatio(), 0.001);
        }
    }

    @Nested
    @DisplayName("merge 메서드")
    class Merge {

        @Test
        @DisplayName("merge는_두_카운트를_합친다")
        void merge는_두_카운트를_합친다() {
            PrayerCount count1 = new PrayerCount(100L, 50L);
            PrayerCount count2 = new PrayerCount(30L, 20L);

            PrayerCount result = count1.merge(count2);

            assertEquals(130L, result.upCount());
            assertEquals(70L, result.downCount());
        }

        @Test
        @DisplayName("merge는_불변성을_유지한다")
        void merge는_불변성을_유지한다() {
            PrayerCount count1 = new PrayerCount(100L, 50L);
            PrayerCount count2 = new PrayerCount(30L, 20L);

            PrayerCount result = count1.merge(count2);

            assertEquals(100L, count1.upCount());
            assertEquals(30L, count2.upCount());
            assertNotSame(count1, result);
        }
    }
}
