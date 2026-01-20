package com.crypto.prayer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Prayer record")
class PrayerTest {

    @Nested
    @DisplayName("create 팩토리 메서드")
    class Create {

        @Test
        @DisplayName("UP_기도를_생성하면_side가_UP이다")
        void UP_기도를_생성하면_side가_UP이다() {
            Prayer prayer = Prayer.create(Side.UP, "session-123");

            assertEquals(Side.UP, prayer.side());
        }

        @Test
        @DisplayName("DOWN_기도를_생성하면_side가_DOWN이다")
        void DOWN_기도를_생성하면_side가_DOWN이다() {
            Prayer prayer = Prayer.create(Side.DOWN, "session-456");

            assertEquals(Side.DOWN, prayer.side());
        }

        @Test
        @DisplayName("기도를_생성하면_sessionId가_설정된다")
        void 기도를_생성하면_sessionId가_설정된다() {
            String sessionId = "test-session-id";
            Prayer prayer = Prayer.create(Side.UP, sessionId);

            assertEquals(sessionId, prayer.sessionId());
        }

        @Test
        @DisplayName("기도를_생성하면_고유한_id가_생성된다")
        void 기도를_생성하면_고유한_id가_생성된다() {
            Prayer prayer1 = Prayer.create(Side.UP, "session-1");
            Prayer prayer2 = Prayer.create(Side.UP, "session-1");

            assertNotNull(prayer1.id());
            assertNotNull(prayer2.id());
            assertNotEquals(prayer1.id(), prayer2.id());
        }

        @Test
        @DisplayName("기도를_생성하면_timestamp가_현재_시간_근처이다")
        void 기도를_생성하면_timestamp가_현재_시간_근처이다() {
            Instant before = Instant.now();
            Prayer prayer = Prayer.create(Side.UP, "session-123");
            Instant after = Instant.now();

            assertNotNull(prayer.timestamp());
            assertFalse(prayer.timestamp().isBefore(before));
            assertFalse(prayer.timestamp().isAfter(after));
        }
    }

    @Nested
    @DisplayName("record 생성자")
    class Constructor {

        @Test
        @DisplayName("모든_필드를_직접_지정하여_생성할_수_있다")
        void 모든_필드를_직접_지정하여_생성할_수_있다() {
            String id = "test-id";
            Side side = Side.DOWN;
            String sessionId = "test-session";
            Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");

            Prayer prayer = new Prayer(id, side, sessionId, timestamp);

            assertEquals(id, prayer.id());
            assertEquals(side, prayer.side());
            assertEquals(sessionId, prayer.sessionId());
            assertEquals(timestamp, prayer.timestamp());
        }
    }
}
