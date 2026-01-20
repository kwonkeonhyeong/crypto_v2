package com.crypto.prayer.adapter.out.binance.reconnect;

import java.util.concurrent.ThreadLocalRandom;

public class ExponentialBackoff {

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final double jitterFactor;

    private int attempt = 0;

    public ExponentialBackoff() {
        this(1000, 30000, 2.0, 0.1);
    }

    public ExponentialBackoff(
            long initialDelayMs,
            long maxDelayMs,
            double multiplier,
            double jitterFactor) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
        this.jitterFactor = jitterFactor;
    }

    /**
     * 다음 재시도까지 대기 시간 (ms)
     */
    public long nextDelayMs() {
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt));
        delay = Math.min(delay, maxDelayMs);

        // Jitter 추가 (±jitterFactor%)
        if (jitterFactor > 0) {
            double jitter = delay * jitterFactor * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
            delay = (long) (delay + jitter);
        }

        attempt++;
        return Math.max(delay, 0);
    }

    /**
     * 성공 시 리셋
     */
    public void reset() {
        attempt = 0;
    }

    public int getAttempt() {
        return attempt;
    }
}
