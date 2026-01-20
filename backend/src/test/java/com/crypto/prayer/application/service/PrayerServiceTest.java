package com.crypto.prayer.application.service;

import com.crypto.prayer.domain.model.Prayer;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.PrayerStats;
import com.crypto.prayer.domain.model.Side;
import com.crypto.prayer.infrastructure.fallback.FallbackManager;
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
@DisplayName("PrayerService")
class PrayerServiceTest {

    @Mock
    private FallbackManager countPort;

    private PrayerService prayerService;

    @BeforeEach
    void setUp() {
        prayerService = new PrayerService(countPort);
    }

    @Nested
    @DisplayName("pray 메서드")
    class Pray {

        @Test
        @DisplayName("기도를_등록하고_Prayer를_반환한다")
        void 기도를_등록하고_Prayer를_반환한다() {
            when(countPort.increment(Side.UP, 1L)).thenReturn(1L);

            Prayer result = prayerService.pray(Side.UP, "session-123");

            assertNotNull(result);
            assertEquals(Side.UP, result.side());
            assertEquals("session-123", result.sessionId());
            verify(countPort).increment(Side.UP, 1L);
        }

        @Test
        @DisplayName("DOWN_기도를_등록할_수_있다")
        void DOWN_기도를_등록할_수_있다() {
            when(countPort.increment(Side.DOWN, 1L)).thenReturn(1L);

            Prayer result = prayerService.pray(Side.DOWN, "session-456");

            assertEquals(Side.DOWN, result.side());
            verify(countPort).increment(Side.DOWN, 1L);
        }
    }

    @Nested
    @DisplayName("prayBatch 메서드")
    class PrayBatch {

        @Test
        @DisplayName("count만큼_증가시킨다")
        void count만큼_증가시킨다() {
            when(countPort.increment(Side.UP, 5L)).thenReturn(5L);

            prayerService.prayBatch(Side.UP, "session-123", 5);

            verify(countPort).increment(Side.UP, 5L);
        }
    }

    @Nested
    @DisplayName("getTodayCount 메서드")
    class GetTodayCount {

        @Test
        @DisplayName("오늘의_기도_카운트를_반환한다")
        void 오늘의_기도_카운트를_반환한다() {
            PrayerCount expected = new PrayerCount(100L, 50L);
            when(countPort.getCount()).thenReturn(expected);

            PrayerCount result = prayerService.getTodayCount();

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("getCurrentStats 메서드")
    class GetCurrentStats {

        @Test
        @DisplayName("현재_통계를_반환한다")
        void 현재_통계를_반환한다() {
            PrayerCount count = new PrayerCount(100L, 50L);
            when(countPort.getCount()).thenReturn(count);

            PrayerStats result = prayerService.getCurrentStats();

            assertNotNull(result);
            assertEquals(count, result.count());
        }

        @Test
        @DisplayName("RPM은_0_이상이다")
        void RPM은_0_이상이다() {
            when(countPort.getCount()).thenReturn(PrayerCount.zero());

            PrayerStats result = prayerService.getCurrentStats();

            assertTrue(result.upRpm() >= 0);
            assertTrue(result.downRpm() >= 0);
        }
    }
}
