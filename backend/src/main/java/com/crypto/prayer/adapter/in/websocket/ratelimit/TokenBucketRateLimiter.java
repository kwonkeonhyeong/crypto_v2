package com.crypto.prayer.adapter.in.websocket.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TokenBucketRateLimiter implements RateLimiter {

    // 초당 5회, 버스트 최대 20
    private static final double REFILL_RATE = 5.0;
    private static final long MAX_TOKENS = 20;
    private static final long REFILL_INTERVAL_MS = 200;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, k -> new TokenBucket());
        return bucket.tryConsume();
    }

    @Override
    public void removeClient(String clientId) {
        buckets.remove(clientId);
    }

    /**
     * 오래된 버킷 정리 (메모리 관리)
     * @param maxIdleTimeMs 최대 유휴 시간 (ms)
     */
    public void cleanupStaleEntries(long maxIdleTimeMs) {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> {
            TokenBucket bucket = entry.getValue();
            return (now - bucket.lastRefillTime) > maxIdleTimeMs;
        });
    }

    private static class TokenBucket {
        private final AtomicLong tokens = new AtomicLong(MAX_TOKENS);
        private volatile long lastRefillTime = System.currentTimeMillis();

        synchronized boolean tryConsume() {
            refill();

            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;

            if (elapsed >= REFILL_INTERVAL_MS) {
                long tokensToAdd = (long) (elapsed / 1000.0 * REFILL_RATE);
                if (tokensToAdd > 0) {
                    long newTokens = Math.min(MAX_TOKENS, tokens.get() + tokensToAdd);
                    tokens.set(newTokens);
                    lastRefillTime = now;
                }
            }
        }
    }
}
