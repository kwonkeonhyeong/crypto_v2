package com.crypto.prayer.adapter.out.redis;

import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RedisKeyGenerator")
class RedisKeyGeneratorTest {

    private RedisKeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        keyGenerator = new RedisKeyGenerator();
    }

    @Nested
    @DisplayName("generateKey 메서드")
    class GenerateKey {

        @Test
        @DisplayName("UP_키는_prayer_날짜_up_형식이다")
        void UP_키는_prayer_날짜_up_형식이다() {
            LocalDate date = LocalDate.of(2024, 1, 15);

            String key = keyGenerator.generateKey(date, Side.UP);

            assertEquals("prayer:20240115:up", key);
        }

        @Test
        @DisplayName("DOWN_키는_prayer_날짜_down_형식이다")
        void DOWN_키는_prayer_날짜_down_형식이다() {
            LocalDate date = LocalDate.of(2024, 12, 31);

            String key = keyGenerator.generateKey(date, Side.DOWN);

            assertEquals("prayer:20241231:down", key);
        }

        @Test
        @DisplayName("오늘_날짜로_키를_생성한다")
        void 오늘_날짜로_키를_생성한다() {
            String key = keyGenerator.generateKey(Side.UP);

            assertTrue(key.startsWith("prayer:"));
            assertTrue(key.endsWith(":up"));
        }
    }

    @Nested
    @DisplayName("getUpKey/getDownKey 메서드")
    class GetUpDownKey {

        @Test
        @DisplayName("getUpKey는_오늘의_up_키를_반환한다")
        void getUpKey는_오늘의_up_키를_반환한다() {
            String key = keyGenerator.getUpKey();

            assertTrue(key.endsWith(":up"));
        }

        @Test
        @DisplayName("getDownKey는_오늘의_down_키를_반환한다")
        void getDownKey는_오늘의_down_키를_반환한다() {
            String key = keyGenerator.getDownKey();

            assertTrue(key.endsWith(":down"));
        }
    }

    @Nested
    @DisplayName("getTtlSeconds 메서드")
    class GetTtlSeconds {

        @Test
        @DisplayName("TTL은_48시간이다")
        void TTL은_48시간이다() {
            long ttl = keyGenerator.getTtlSeconds();

            assertEquals(48 * 60 * 60, ttl);
        }
    }
}
