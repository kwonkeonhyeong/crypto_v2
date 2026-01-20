package com.crypto.prayer.infrastructure.fallback;

import com.crypto.prayer.adapter.out.redis.RedisPrayerCountAdapter;
import com.crypto.prayer.application.port.out.PrayerCountPort;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FallbackManager implements PrayerCountPort {

    private static final Logger log = LoggerFactory.getLogger(FallbackManager.class);

    private final RedisPrayerCountAdapter redisAdapter;
    private final InMemoryPrayerCountAdapter inMemoryAdapter;
    private final AtomicBoolean usingFallback = new AtomicBoolean(false);

    public FallbackManager(
            RedisPrayerCountAdapter redisAdapter,
            InMemoryPrayerCountAdapter inMemoryAdapter) {
        this.redisAdapter = redisAdapter;
        this.inMemoryAdapter = inMemoryAdapter;
    }

    @Override
    public long increment(Side side, long delta) {
        if (usingFallback.get()) {
            return inMemoryAdapter.increment(side, delta);
        }

        try {
            return redisAdapter.increment(side, delta);
        } catch (Exception e) {
            log.warn("Redis increment failed, switching to fallback: {}", e.getMessage());
            usingFallback.set(true);
            return inMemoryAdapter.increment(side, delta);
        }
    }

    @Override
    public PrayerCount getCount() {
        if (usingFallback.get()) {
            return inMemoryAdapter.getCount();
        }

        try {
            return redisAdapter.getCount();
        } catch (Exception e) {
            log.warn("Redis getCount failed, using fallback: {}", e.getMessage());
            return inMemoryAdapter.getCount();
        }
    }

    @Override
    public void merge(PrayerCount delta) {
        redisAdapter.merge(delta);
    }

    @Override
    public boolean isAvailable() {
        return redisAdapter.isAvailable() || inMemoryAdapter.isAvailable();
    }

    /**
     * 30초마다 Redis 연결 상태 확인 및 복구 시도
     */
    @Scheduled(fixedRate = 30000)
    public void checkAndRecover() {
        if (!usingFallback.get()) {
            return;
        }

        if (redisAdapter.isAvailable()) {
            log.info("Redis connection recovered, merging fallback data...");

            if (inMemoryAdapter.hasData()) {
                PrayerCount fallbackData = inMemoryAdapter.getAndReset();
                redisAdapter.merge(fallbackData);
                log.info("Merged {} up and {} down prayers to Redis",
                    fallbackData.upCount(), fallbackData.downCount());
            }

            usingFallback.set(false);
            log.info("Switched back to Redis");
        }
    }

    public boolean isUsingFallback() {
        return usingFallback.get();
    }
}
