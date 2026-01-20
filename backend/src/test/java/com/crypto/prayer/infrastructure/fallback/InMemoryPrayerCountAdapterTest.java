package com.crypto.prayer.infrastructure.fallback;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryPrayerCountAdapter")
class InMemoryPrayerCountAdapterTest {

    private InMemoryPrayerCountAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryPrayerCountAdapter();
    }

    @Nested
    @DisplayName("increment 메서드")
    class Increment {

        @Test
        @DisplayName("UP_증가시_upCount가_증가한다")
        void UP_증가시_upCount가_증가한다() {
            long result = adapter.increment(Side.UP, 5L);

            assertEquals(5L, result);
            assertEquals(5L, adapter.getCount().upCount());
        }

        @Test
        @DisplayName("DOWN_증가시_downCount가_증가한다")
        void DOWN_증가시_downCount가_증가한다() {
            long result = adapter.increment(Side.DOWN, 3L);

            assertEquals(3L, result);
            assertEquals(3L, adapter.getCount().downCount());
        }

        @Test
        @DisplayName("여러번_증가시_누적된다")
        void 여러번_증가시_누적된다() {
            adapter.increment(Side.UP, 5L);
            adapter.increment(Side.UP, 10L);

            assertEquals(15L, adapter.getCount().upCount());
        }
    }

    @Nested
    @DisplayName("getCount 메서드")
    class GetCount {

        @Test
        @DisplayName("초기값은_0이다")
        void 초기값은_0이다() {
            PrayerCount count = adapter.getCount();

            assertEquals(0L, count.upCount());
            assertEquals(0L, count.downCount());
        }
    }

    @Nested
    @DisplayName("merge 메서드")
    class Merge {

        @Test
        @DisplayName("delta를_현재_카운트에_합친다")
        void delta를_현재_카운트에_합친다() {
            adapter.increment(Side.UP, 10L);
            PrayerCount delta = new PrayerCount(5L, 3L);

            adapter.merge(delta);

            assertEquals(15L, adapter.getCount().upCount());
            assertEquals(3L, adapter.getCount().downCount());
        }
    }

    @Nested
    @DisplayName("isAvailable 메서드")
    class IsAvailable {

        @Test
        @DisplayName("항상_true를_반환한다")
        void 항상_true를_반환한다() {
            assertTrue(adapter.isAvailable());
        }
    }

    @Nested
    @DisplayName("getAndReset 메서드")
    class GetAndReset {

        @Test
        @DisplayName("현재_카운트를_반환하고_0으로_리셋한다")
        void 현재_카운트를_반환하고_0으로_리셋한다() {
            adapter.increment(Side.UP, 10L);
            adapter.increment(Side.DOWN, 5L);

            PrayerCount result = adapter.getAndReset();

            assertEquals(10L, result.upCount());
            assertEquals(5L, result.downCount());
            assertEquals(0L, adapter.getCount().upCount());
            assertEquals(0L, adapter.getCount().downCount());
        }
    }

    @Nested
    @DisplayName("hasData 메서드")
    class HasData {

        @Test
        @DisplayName("데이터가_없으면_false를_반환한다")
        void 데이터가_없으면_false를_반환한다() {
            assertFalse(adapter.hasData());
        }

        @Test
        @DisplayName("데이터가_있으면_true를_반환한다")
        void 데이터가_있으면_true를_반환한다() {
            adapter.increment(Side.UP, 1L);

            assertTrue(adapter.hasData());
        }
    }
}
