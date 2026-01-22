package com.crypto.prayer.infrastructure.fallback;

import com.crypto.prayer.application.port.out.PrayerCountPort;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryPrayerCountAdapter implements PrayerCountPort {

    private final AtomicLong upCount = new AtomicLong(0);
    private final AtomicLong downCount = new AtomicLong(0);

    @Override
    public long increment(Side side, long delta) {
        return switch (side) {
            case UP -> upCount.addAndGet(delta);
            case DOWN -> downCount.addAndGet(delta);
        };
    }

    @Override
    public PrayerCount getCount() {
        return new PrayerCount(upCount.get(), downCount.get());
    }

    @Override
    public void merge(PrayerCount delta) {
        upCount.addAndGet(delta.upCount());
        downCount.addAndGet(delta.downCount());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    public PrayerCount getAndReset() {
        long up = upCount.getAndSet(0);
        long down = downCount.getAndSet(0);
        return new PrayerCount(up, down);
    }

    public boolean hasData() {
        return upCount.get() > 0 || downCount.get() > 0;
    }
}
