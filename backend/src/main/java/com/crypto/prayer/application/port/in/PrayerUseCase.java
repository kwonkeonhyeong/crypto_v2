package com.crypto.prayer.application.port.in;

import com.crypto.prayer.domain.model.Prayer;
import com.crypto.prayer.domain.model.Side;

public interface PrayerUseCase {

    /**
     * 기도를 등록하고 생성된 Prayer를 반환
     */
    Prayer pray(Side side, String sessionId);

    /**
     * 배치로 기도 등록 (클라이언트 배칭 지원)
     */
    void prayBatch(Side side, String sessionId, int count);
}
