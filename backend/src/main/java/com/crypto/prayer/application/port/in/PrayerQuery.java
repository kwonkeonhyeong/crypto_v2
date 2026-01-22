package com.crypto.prayer.application.port.in;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.PrayerStats;

public interface PrayerQuery {

    /**
     * 오늘의 기도 카운트 조회
     */
    PrayerCount getTodayCount();

    /**
     * 현재 통계 조회 (RPM 포함)
     */
    PrayerStats getCurrentStats();
}
