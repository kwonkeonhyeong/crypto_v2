package com.crypto.prayer.adapter.in.websocket.ratelimit;

public class RateLimitExceededException extends RuntimeException {

    private final String sessionId;

    public RateLimitExceededException(String sessionId) {
        super("Rate limit exceeded for session: " + sessionId);
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
