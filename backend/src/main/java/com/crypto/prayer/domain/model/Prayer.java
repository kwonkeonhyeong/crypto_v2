package com.crypto.prayer.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Prayer(
    String id,
    Side side,
    String sessionId,
    Instant timestamp
) {
    public static Prayer create(Side side, String sessionId) {
        return new Prayer(
            UUID.randomUUID().toString(),
            side,
            sessionId,
            Instant.now()
        );
    }
}
