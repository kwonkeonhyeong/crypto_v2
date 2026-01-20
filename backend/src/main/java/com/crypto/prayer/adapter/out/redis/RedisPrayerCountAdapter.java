package com.crypto.prayer.adapter.out.redis;

import com.crypto.prayer.application.port.out.PrayerCountPort;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisPrayerCountAdapter implements PrayerCountPort {

    private static final Logger log = LoggerFactory.getLogger(RedisPrayerCountAdapter.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    public RedisPrayerCountAdapter(
            StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    @Override
    public long increment(Side side, long delta) {
        String key = keyGenerator.generateKey(side);
        Long result = redisTemplate.opsForValue().increment(key, delta);

        // TTL 설정 (최초 생성 시에만)
        if (result != null && result == delta) {
            redisTemplate.expire(key, Duration.ofSeconds(keyGenerator.getTtlSeconds()));
        }

        return result != null ? result : 0L;
    }

    @Override
    public PrayerCount getCount() {
        List<String> keys = List.of(
            keyGenerator.getUpKey(),
            keyGenerator.getDownKey()
        );

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        if (values == null) {
            return PrayerCount.zero();
        }

        long upCount = parseCount(values.get(0));
        long downCount = parseCount(values.get(1));

        return new PrayerCount(upCount, downCount);
    }

    @Override
    public void merge(PrayerCount delta) {
        if (delta.upCount() > 0) {
            increment(Side.UP, delta.upCount());
        }
        if (delta.downCount() > 0) {
            increment(Side.DOWN, delta.downCount());
        }
        log.info("Merged fallback count: up={}, down={}", delta.upCount(), delta.downCount());
    }

    @Override
    public boolean isAvailable() {
        try {
            String result = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    private long parseCount(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
