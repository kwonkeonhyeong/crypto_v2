package com.crypto.prayer.infrastructure.scheduler;

import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import com.crypto.prayer.application.port.in.PrayerQuery;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.crypto.prayer.domain.model.PrayerStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BroadcastScheduler {

    private static final Logger log = LoggerFactory.getLogger(BroadcastScheduler.class);

    private final PrayerQuery prayerQuery;
    private final BroadcastPort broadcastPort;

    private volatile PrayerStats lastStats;

    public BroadcastScheduler(
            PrayerQuery prayerQuery,
            BroadcastPort broadcastPort) {
        this.prayerQuery = prayerQuery;
        this.broadcastPort = broadcastPort;
    }

    /**
     * 200ms마다 기도 통계 브로드캐스트
     */
    @Scheduled(fixedRate = 200)
    public void broadcastPrayerStats() {
        PrayerStats currentStats = prayerQuery.getCurrentStats();

        if (hasChanged(currentStats)) {
            PrayerResponse response = PrayerResponse.from(
                currentStats.count().upCount(),
                currentStats.count().downCount(),
                currentStats.upRpm(),
                currentStats.downRpm()
            );

            broadcastPort.broadcastPrayerStats(response);
            lastStats = currentStats;
        }
    }

    private boolean hasChanged(PrayerStats current) {
        if (lastStats == null) {
            return true;
        }

        return current.count().upCount() != lastStats.count().upCount()
            || current.count().downCount() != lastStats.count().downCount()
            || Math.abs(current.upRpm() - lastStats.upRpm()) > 0.1
            || Math.abs(current.downRpm() - lastStats.downRpm()) > 0.1;
    }
}
