package com.crypto.prayer.application.service;

import com.crypto.prayer.application.port.in.PrayerQuery;
import com.crypto.prayer.application.port.in.PrayerUseCase;
import com.crypto.prayer.domain.model.Prayer;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.PrayerStats;
import com.crypto.prayer.domain.model.Side;
import com.crypto.prayer.infrastructure.fallback.FallbackManager;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class PrayerService implements PrayerUseCase, PrayerQuery {

    private final FallbackManager countPort;
    private final RpmCalculator rpmCalculator;

    public PrayerService(FallbackManager countPort) {
        this.countPort = countPort;
        this.rpmCalculator = new RpmCalculator();
    }

    @Override
    public Prayer pray(Side side, String sessionId) {
        Prayer prayer = Prayer.create(side, sessionId);
        countPort.increment(side, 1);
        rpmCalculator.record(side);
        return prayer;
    }

    @Override
    public void prayBatch(Side side, String sessionId, int count) {
        countPort.increment(side, count);
        for (int i = 0; i < count; i++) {
            rpmCalculator.record(side);
        }
    }

    @Override
    public PrayerCount getTodayCount() {
        return countPort.getCount();
    }

    @Override
    public PrayerStats getCurrentStats() {
        PrayerCount count = countPort.getCount();
        double upRpm = rpmCalculator.getRpm(Side.UP);
        double downRpm = rpmCalculator.getRpm(Side.DOWN);
        return PrayerStats.create(count, upRpm, downRpm);
    }

    /**
     * 최근 60초 기준 RPM 계산기
     */
    private static class RpmCalculator {
        private static final long WINDOW_MS = 60_000L;

        private final ConcurrentLinkedQueue<TimestampedEvent> upEvents = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<TimestampedEvent> downEvents = new ConcurrentLinkedQueue<>();

        void record(Side side) {
            long now = System.currentTimeMillis();
            TimestampedEvent event = new TimestampedEvent(now);

            switch (side) {
                case UP -> upEvents.add(event);
                case DOWN -> downEvents.add(event);
            }

            cleanOldEvents(now);
        }

        double getRpm(Side side) {
            long now = System.currentTimeMillis();
            cleanOldEvents(now);

            ConcurrentLinkedQueue<TimestampedEvent> events = switch (side) {
                case UP -> upEvents;
                case DOWN -> downEvents;
            };

            return events.size();
        }

        private void cleanOldEvents(long now) {
            long cutoff = now - WINDOW_MS;
            upEvents.removeIf(e -> e.timestamp < cutoff);
            downEvents.removeIf(e -> e.timestamp < cutoff);
        }

        private record TimestampedEvent(long timestamp) {}
    }
}
