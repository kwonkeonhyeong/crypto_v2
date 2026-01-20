package com.crypto.prayer.adapter.in.websocket.dto;

import com.crypto.prayer.domain.model.Side;

public record PrayerRequest(
    String side,
    int count
) {
    public PrayerRequest {
        if (count <= 0) {
            count = 1;
        }
        if (count > 20) {
            count = 20;
        }
    }

    public Side toSide() {
        return Side.fromKey(side);
    }
}
