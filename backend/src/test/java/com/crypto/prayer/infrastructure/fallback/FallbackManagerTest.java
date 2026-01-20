package com.crypto.prayer.infrastructure.fallback;

import com.crypto.prayer.adapter.out.redis.RedisPrayerCountAdapter;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FallbackManager")
class FallbackManagerTest {

    @Mock
    private RedisPrayerCountAdapter redisAdapter;

    private InMemoryPrayerCountAdapter inMemoryAdapter;
    private FallbackManager fallbackManager;

    @BeforeEach
    void setUp() {
        inMemoryAdapter = new InMemoryPrayerCountAdapter();
        fallbackManager = new FallbackManager(redisAdapter, inMemoryAdapter);
    }

    @Nested
    @DisplayName("increment 메서드")
    class Increment {

        @Test
        @DisplayName("정상시_Redis를_사용한다")
        void 정상시_Redis를_사용한다() {
            when(redisAdapter.increment(Side.UP, 5L)).thenReturn(5L);

            long result = fallbackManager.increment(Side.UP, 5L);

            assertEquals(5L, result);
            verify(redisAdapter).increment(Side.UP, 5L);
        }

        @Test
        @DisplayName("Redis_실패시_인메모리로_전환한다")
        void Redis_실패시_인메모리로_전환한다() {
            when(redisAdapter.increment(any(), anyLong())).thenThrow(new RuntimeException("Redis down"));

            long result = fallbackManager.increment(Side.UP, 5L);

            assertEquals(5L, result);
            assertTrue(fallbackManager.isUsingFallback());
        }

        @Test
        @DisplayName("폴백_모드에서_인메모리를_사용한다")
        void 폴백_모드에서_인메모리를_사용한다() {
            when(redisAdapter.increment(any(), anyLong())).thenThrow(new RuntimeException("Redis down"));
            fallbackManager.increment(Side.UP, 5L); // 폴백 전환

            reset(redisAdapter);
            long result = fallbackManager.increment(Side.DOWN, 3L);

            assertEquals(3L, result);
            verifyNoInteractions(redisAdapter);
        }
    }

    @Nested
    @DisplayName("getCount 메서드")
    class GetCount {

        @Test
        @DisplayName("정상시_Redis에서_조회한다")
        void 정상시_Redis에서_조회한다() {
            PrayerCount expected = new PrayerCount(100L, 50L);
            when(redisAdapter.getCount()).thenReturn(expected);

            PrayerCount result = fallbackManager.getCount();

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Redis_실패시_인메모리에서_조회한다")
        void Redis_실패시_인메모리에서_조회한다() {
            when(redisAdapter.getCount()).thenThrow(new RuntimeException("Redis down"));
            inMemoryAdapter.increment(Side.UP, 10L);

            PrayerCount result = fallbackManager.getCount();

            assertEquals(10L, result.upCount());
        }
    }

    @Nested
    @DisplayName("checkAndRecover 메서드")
    class CheckAndRecover {

        @Test
        @DisplayName("폴백_모드가_아니면_아무것도_하지_않는다")
        void 폴백_모드가_아니면_아무것도_하지_않는다() {
            fallbackManager.checkAndRecover();

            verify(redisAdapter, never()).isAvailable();
        }

        @Test
        @DisplayName("Redis_복구시_인메모리_데이터를_merge한다")
        void Redis_복구시_인메모리_데이터를_merge한다() {
            // 폴백 모드로 전환
            when(redisAdapter.increment(any(), anyLong())).thenThrow(new RuntimeException("Redis down"));
            fallbackManager.increment(Side.UP, 10L);
            fallbackManager.increment(Side.DOWN, 5L);

            // Redis 복구
            when(redisAdapter.isAvailable()).thenReturn(true);
            fallbackManager.checkAndRecover();

            verify(redisAdapter).merge(any(PrayerCount.class));
            assertFalse(fallbackManager.isUsingFallback());
        }
    }

    @Nested
    @DisplayName("isAvailable 메서드")
    class IsAvailable {

        @Test
        @DisplayName("Redis_또는_인메모리가_사용가능하면_true를_반환한다")
        void Redis_또는_인메모리가_사용가능하면_true를_반환한다() {
            when(redisAdapter.isAvailable()).thenReturn(false);

            assertTrue(fallbackManager.isAvailable());
        }
    }
}
