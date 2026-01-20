package com.crypto.prayer.application.port.out;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;

public interface PrayerCountPort {

    /**
     * 기도 카운트 증가
     * @return 증가 후 총 카운트
     */
    long increment(Side side, long delta);

    /**
     * 현재 카운트 조회
     */
    PrayerCount getCount();

    /**
     * 카운트 Merge (폴백 복구용)
     */
    void merge(PrayerCount delta);

    /**
     * 연결 상태 확인
     */
    boolean isAvailable();
}
