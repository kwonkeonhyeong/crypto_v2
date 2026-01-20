package com.crypto.prayer.adapter.in.websocket;

import com.crypto.prayer.adapter.in.websocket.dto.PrayerRequest;
import com.crypto.prayer.adapter.in.websocket.ratelimit.RateLimitExceededException;
import com.crypto.prayer.adapter.in.websocket.ratelimit.TokenBucketRateLimiter;
import com.crypto.prayer.application.port.in.PrayerUseCase;
import com.crypto.prayer.domain.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final PrayerUseCase prayerUseCase;
    private final TokenBucketRateLimiter rateLimiter;

    public WebSocketController(
            PrayerUseCase prayerUseCase,
            TokenBucketRateLimiter rateLimiter) {
        this.prayerUseCase = prayerUseCase;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 기도 클릭 처리
     * 클라이언트: SEND /app/prayer
     */
    @MessageMapping("/prayer")
    public void handlePrayer(
            PrayerRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();

        // Rate limit 체크
        if (!rateLimiter.tryConsume(sessionId)) {
            throw new RateLimitExceededException(sessionId);
        }

        Side side = request.toSide();

        if (request.count() == 1) {
            prayerUseCase.pray(side, sessionId);
        } else {
            prayerUseCase.prayBatch(side, sessionId, request.count());
        }

        log.debug("Prayer received: side={}, count={}, session={}",
            side, request.count(), sessionId);
    }

    /**
     * Rate Limit 초과 에러 처리
     */
    @MessageExceptionHandler(RateLimitExceededException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded for session: {}", ex.getSessionId());
        return new ErrorResponse("RATE_LIMIT_EXCEEDED", "Too many requests. Please slow down.");
    }

    public record ErrorResponse(String code, String message) {}
}
