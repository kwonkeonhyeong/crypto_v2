package com.crypto.prayer.adapter.out.redis;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisPrayerCountAdapter")
class RedisPrayerCountAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisKeyGenerator keyGenerator;
    private RedisPrayerCountAdapter adapter;

    @BeforeEach
    void setUp() {
        keyGenerator = new RedisKeyGenerator();
        adapter = new RedisPrayerCountAdapter(redisTemplate, keyGenerator);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("increment 메서드")
    class Increment {

        @Test
        @DisplayName("UP_증가시_Redis_INCRBY를_호출한다")
        void UP_증가시_Redis_INCRBY를_호출한다() {
            when(valueOperations.increment(anyString(), eq(5L))).thenReturn(5L);

            long result = adapter.increment(Side.UP, 5L);

            assertEquals(5L, result);
            verify(valueOperations).increment(contains(":up"), eq(5L));
        }

        @Test
        @DisplayName("DOWN_증가시_Redis_INCRBY를_호출한다")
        void DOWN_증가시_Redis_INCRBY를_호출한다() {
            when(valueOperations.increment(anyString(), eq(3L))).thenReturn(3L);

            long result = adapter.increment(Side.DOWN, 3L);

            assertEquals(3L, result);
            verify(valueOperations).increment(contains(":down"), eq(3L));
        }

        @Test
        @DisplayName("최초_증가시_TTL을_설정한다")
        void 최초_증가시_TTL을_설정한다() {
            when(valueOperations.increment(anyString(), eq(1L))).thenReturn(1L);

            adapter.increment(Side.UP, 1L);

            verify(redisTemplate).expire(anyString(), any());
        }
    }

    @Nested
    @DisplayName("getCount 메서드")
    class GetCount {

        @Test
        @DisplayName("Redis에서_up과_down_카운트를_조회한다")
        void Redis에서_up과_down_카운트를_조회한다() {
            when(valueOperations.multiGet(anyList())).thenReturn(List.of("100", "50"));

            PrayerCount count = adapter.getCount();

            assertEquals(100L, count.upCount());
            assertEquals(50L, count.downCount());
        }

        @Test
        @DisplayName("값이_null이면_0을_반환한다")
        void 값이_null이면_0을_반환한다() {
            when(valueOperations.multiGet(anyList())).thenReturn(null);

            PrayerCount count = adapter.getCount();

            assertEquals(0L, count.upCount());
            assertEquals(0L, count.downCount());
        }

        @Test
        @DisplayName("빈_문자열이면_0을_반환한다")
        void 빈_문자열이면_0을_반환한다() {
            when(valueOperations.multiGet(anyList())).thenReturn(List.of("", ""));

            PrayerCount count = adapter.getCount();

            assertEquals(0L, count.upCount());
            assertEquals(0L, count.downCount());
        }
    }

    @Nested
    @DisplayName("merge 메서드")
    class Merge {

        @Test
        @DisplayName("delta가_0보다_크면_increment를_호출한다")
        void delta가_0보다_크면_increment를_호출한다() {
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(10L);
            PrayerCount delta = new PrayerCount(5L, 3L);

            adapter.merge(delta);

            verify(valueOperations).increment(contains(":up"), eq(5L));
            verify(valueOperations).increment(contains(":down"), eq(3L));
        }

        @Test
        @DisplayName("delta가_0이면_increment를_호출하지_않는다")
        void delta가_0이면_increment를_호출하지_않는다() {
            PrayerCount delta = new PrayerCount(0L, 0L);

            adapter.merge(delta);

            verify(valueOperations, never()).increment(anyString(), anyLong());
        }
    }
}
