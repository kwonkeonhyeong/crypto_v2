package com.crypto.prayer.integration;

import com.crypto.prayer.adapter.out.redis.RedisPrayerCountAdapter;
import com.crypto.prayer.adapter.out.redis.RedisKeyGenerator;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Redis 통합 테스트")
class RedisIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private RedisPrayerCountAdapter adapter;

    @Autowired
    private RedisKeyGenerator keyGenerator;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear all keys before each test
        Set<String> keys = redisTemplate.keys("prayer:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Nested
    @DisplayName("increment 메서드")
    class Increment {

        @Test
        @DisplayName("UP_카운터를_증가시킨다")
        void UP_카운터를_증가시킨다() {
            long result = adapter.increment(Side.UP, 1L);

            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("DOWN_카운터를_증가시킨다")
        void DOWN_카운터를_증가시킨다() {
            long result = adapter.increment(Side.DOWN, 1L);

            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("여러번_증가시_누적된다")
        void 여러번_증가시_누적된다() {
            adapter.increment(Side.UP, 5L);
            adapter.increment(Side.UP, 10L);
            long result = adapter.increment(Side.UP, 3L);

            assertThat(result).isEqualTo(18L);
        }

        @Test
        @DisplayName("TTL이_설정된다")
        void TTL이_설정된다() {
            adapter.increment(Side.UP, 1L);

            Long ttl = redisTemplate.getExpire(keyGenerator.getUpKey());
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("getCount 메서드")
    class GetCount {

        @Test
        @DisplayName("초기_상태에서_0을_반환한다")
        void 초기_상태에서_0을_반환한다() {
            PrayerCount count = adapter.getCount();

            assertThat(count.upCount()).isEqualTo(0L);
            assertThat(count.downCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("증가된_카운트를_조회한다")
        void 증가된_카운트를_조회한다() {
            adapter.increment(Side.UP, 100L);
            adapter.increment(Side.DOWN, 50L);

            PrayerCount count = adapter.getCount();

            assertThat(count.upCount()).isEqualTo(100L);
            assertThat(count.downCount()).isEqualTo(50L);
        }
    }

    @Nested
    @DisplayName("merge 메서드")
    class Merge {

        @Test
        @DisplayName("기존_카운트에_델타를_병합한다")
        void 기존_카운트에_델타를_병합한다() {
            adapter.increment(Side.UP, 10L);
            adapter.increment(Side.DOWN, 5L);

            PrayerCount delta = new PrayerCount(20L, 15L);
            adapter.merge(delta);

            PrayerCount count = adapter.getCount();
            assertThat(count.upCount()).isEqualTo(30L);
            assertThat(count.downCount()).isEqualTo(20L);
        }

        @Test
        @DisplayName("0_값은_증가시키지_않는다")
        void 영_값은_증가시키지_않는다() {
            adapter.increment(Side.UP, 10L);

            PrayerCount delta = new PrayerCount(0L, 5L);
            adapter.merge(delta);

            PrayerCount count = adapter.getCount();
            assertThat(count.upCount()).isEqualTo(10L);
            assertThat(count.downCount()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("isAvailable 메서드")
    class IsAvailable {

        @Test
        @DisplayName("Redis_연결이_가능하면_true를_반환한다")
        void Redis_연결이_가능하면_true를_반환한다() {
            boolean available = adapter.isAvailable();

            assertThat(available).isTrue();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class Concurrency {

        @Test
        @DisplayName("여러_스레드에서_동시에_증가시켜도_정확한_결과를_반환한다")
        void 여러_스레드에서_동시에_증가시켜도_정확한_결과를_반환한다() throws InterruptedException {
            int threadCount = 10;
            int incrementsPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        adapter.increment(Side.UP, 1L);
                    }
                });
            }

            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            PrayerCount count = adapter.getCount();
            assertThat(count.upCount()).isEqualTo(threadCount * incrementsPerThread);
        }
    }
}
