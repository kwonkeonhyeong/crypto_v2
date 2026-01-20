package com.crypto.prayer.adapter.in.websocket.ratelimit;

public interface RateLimiter {

    /**
     * 토큰 소비 시도
     * @param clientId 클라이언트 식별자
     * @return 성공 여부
     */
    boolean tryConsume(String clientId);

    /**
     * 클라이언트 제거 (연결 해제 시)
     * @param clientId 클라이언트 식별자
     */
    void removeClient(String clientId);
}
