package com.crypto.prayer.adapter.in.websocket;

import com.crypto.prayer.adapter.in.websocket.ratelimit.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketSessionListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionListener.class);

    private final TokenBucketRateLimiter rateLimiter;
    private final AtomicInteger connectedSessions = new AtomicInteger(0);

    public WebSocketSessionListener(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        int count = connectedSessions.incrementAndGet();
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        log.info("WebSocket connected: sessionId={}, totalConnections={}", sessionId, count);
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        int count = connectedSessions.decrementAndGet();
        String sessionId = event.getSessionId();

        // Rate limiter에서 세션 정리
        rateLimiter.removeClient(sessionId);

        log.info("WebSocket disconnected: sessionId={}, totalConnections={}", sessionId, count);
    }

    public int getConnectedSessionCount() {
        return connectedSessions.get();
    }
}
