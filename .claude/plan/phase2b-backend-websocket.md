# Phase 2b: Backend WebSocket & STOMP

## 목표
Spring WebSocket + STOMP 프로토콜을 사용하여 실시간 양방향 통신을 구현한다.

## 선행 의존성
- Phase 2a: Backend 기반 완료

## 범위
- WebSocket + STOMP 설정
- 메시지 타입 정의 (CLICK, TICKER, LIQUIDATION)
- 세션 관리
- Rate Limiter (토큰 버킷)
- 200ms 브로드캐스터
- RPM 실시간 계산

---

## 디렉토리 구조

```
backend/src/main/java/com/crypto/prayer/
├── adapter/
│   └── in/
│       └── websocket/
│           ├── WebSocketConfig.java
│           ├── WebSocketController.java
│           ├── WebSocketEventListener.java
│           ├── dto/
│           │   ├── PrayerRequest.java
│           │   ├── PrayerResponse.java
│           │   ├── TickerMessage.java
│           │   └── LiquidationMessage.java
│           └── ratelimit/
│               ├── RateLimiter.java
│               └── TokenBucketRateLimiter.java
├── application/
│   ├── port/
│   │   └── out/
│   │       └── BroadcastPort.java
│   └── service/
│       └── BroadcastService.java
└── infrastructure/
    └── scheduler/
        └── BroadcastScheduler.java
```

---

## 상세 구현 단계

### 2b.1 WebSocket 설정

#### adapter/in/websocket/WebSocketConfig.java
```java
package com.crypto.prayer.adapter.in.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트가 구독할 prefix
        registry.enableSimpleBroker("/topic");
        // 클라이언트가 메시지를 보낼 prefix
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();

        // SockJS 없는 순수 WebSocket 엔드포인트
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new RateLimitChannelInterceptor());
    }
}
```

#### RateLimitChannelInterceptor.java
```java
package com.crypto.prayer.adapter.in.websocket;

import com.crypto.prayer.adapter.in.websocket.ratelimit.TokenBucketRateLimiter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

public class RateLimitChannelInterceptor implements ChannelInterceptor {

    private final TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
            String sessionId = accessor.getSessionId();

            if (!rateLimiter.tryConsume(sessionId)) {
                // Rate limit 초과 시 null 반환으로 메시지 차단
                throw new RateLimitExceededException(sessionId);
            }
        }

        return message;
    }
}
```

### 2b.2 Rate Limiter (토큰 버킷)

#### adapter/in/websocket/ratelimit/RateLimiter.java
```java
package com.crypto.prayer.adapter.in.websocket.ratelimit;

public interface RateLimiter {

    /**
     * 토큰 소비 시도
     * @return 성공 여부
     */
    boolean tryConsume(String clientId);

    /**
     * 클라이언트 제거 (연결 해제 시)
     */
    void removeClient(String clientId);
}
```

#### adapter/in/websocket/ratelimit/TokenBucketRateLimiter.java
```java
package com.crypto.prayer.adapter.in.websocket.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TokenBucketRateLimiter implements RateLimiter {

    // 초당 5회, 버스트 최대 20
    private static final double REFILL_RATE = 5.0; // 초당 토큰 충전량
    private static final long MAX_TOKENS = 20;     // 최대 버스트 토큰
    private static final long REFILL_INTERVAL_MS = 200; // 1개 토큰 충전 시간

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

    /**
     * 정기적으로 오래된 버킷 정리 (메모리 관리)
     */
    public void cleanupStaleEntries(long maxIdleTimeMs) {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> {
            TokenBucket bucket = entry.getValue();
            return (now - bucket.lastRefillTime) > maxIdleTimeMs;
        });
    }
}
```

#### RateLimitExceededException.java
```java
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
```

### 2b.3 DTO 정의

#### adapter/in/websocket/dto/PrayerRequest.java
```java
package com.crypto.prayer.adapter.in.websocket.dto;

import com.crypto.prayer.domain.model.Side;

public record PrayerRequest(
    String side,    // "up" 또는 "down"
    int count       // 배치 카운트 (기본 1)
) {
    public PrayerRequest {
        if (count <= 0) {
            count = 1;
        }
    }

    public Side toSide() {
        return Side.fromKey(side);
    }
}
```

#### adapter/in/websocket/dto/PrayerResponse.java
```java
package com.crypto.prayer.adapter.in.websocket.dto;

public record PrayerResponse(
    String type,        // "CLICK"
    long upCount,
    long downCount,
    double upRpm,
    double downRpm,
    double upRatio,
    double downRatio,
    long timestamp
) {
    public static PrayerResponse from(
            long upCount, long downCount,
            double upRpm, double downRpm) {
        long total = upCount + downCount;
        double upRatio = total == 0 ? 0.5 : (double) upCount / total;

        return new PrayerResponse(
            "CLICK",
            upCount,
            downCount,
            upRpm,
            downRpm,
            upRatio,
            1.0 - upRatio,
            System.currentTimeMillis()
        );
    }
}
```

#### adapter/in/websocket/dto/TickerMessage.java
```java
package com.crypto.prayer.adapter.in.websocket.dto;

public record TickerMessage(
    String type,            // "TICKER"
    String symbol,          // "BTCUSDT"
    double price,           // 현재가
    double priceChange24h,  // 24시간 변동률 (%)
    long timestamp
) {
    public static TickerMessage of(String symbol, double price, double priceChange24h) {
        return new TickerMessage(
            "TICKER",
            symbol,
            price,
            priceChange24h,
            System.currentTimeMillis()
        );
    }
}
```

#### adapter/in/websocket/dto/LiquidationMessage.java
```java
package com.crypto.prayer.adapter.in.websocket.dto;

public record LiquidationMessage(
    String type,            // "LIQUIDATION"
    String symbol,          // "BTCUSDT"
    String side,            // "LONG" 또는 "SHORT"
    double quantity,        // 청산 수량
    double price,           // 청산 가격
    double usdValue,        // USD 가치
    boolean isLarge,        // $100,000 이상 여부
    long timestamp
) {
    private static final double LARGE_THRESHOLD = 100_000.0;

    public static LiquidationMessage of(
            String symbol, String side,
            double quantity, double price) {
        double usdValue = quantity * price;
        return new LiquidationMessage(
            "LIQUIDATION",
            symbol,
            side,
            quantity,
            price,
            usdValue,
            usdValue >= LARGE_THRESHOLD,
            System.currentTimeMillis()
        );
    }
}
```

### 2b.4 WebSocket Controller

#### adapter/in/websocket/WebSocketController.java
```java
package com.crypto.prayer.adapter.in.websocket;

import com.crypto.prayer.adapter.in.websocket.dto.PrayerRequest;
import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import com.crypto.prayer.adapter.in.websocket.ratelimit.RateLimitExceededException;
import com.crypto.prayer.application.port.in.PrayerQuery;
import com.crypto.prayer.application.port.in.PrayerUseCase;
import com.crypto.prayer.domain.model.PrayerStats;
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
    private final PrayerQuery prayerQuery;

    public WebSocketController(
            PrayerUseCase prayerUseCase,
            PrayerQuery prayerQuery) {
        this.prayerUseCase = prayerUseCase;
        this.prayerQuery = prayerQuery;
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
```

### 2b.5 세션 이벤트 리스너

#### adapter/in/websocket/WebSocketEventListener.java
```java
package com.crypto.prayer.adapter.in.websocket;

import com.crypto.prayer.adapter.in.websocket.ratelimit.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final SimpMessageSendingOperations messagingTemplate;
    private final TokenBucketRateLimiter rateLimiter;
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    public WebSocketEventListener(
            SimpMessageSendingOperations messagingTemplate,
            TokenBucketRateLimiter rateLimiter) {
        this.messagingTemplate = messagingTemplate;
        this.rateLimiter = rateLimiter;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        int count = connectionCount.incrementAndGet();
        log.info("New WebSocket connection: sessionId={}, totalConnections={}",
            sessionId, count);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        // Rate limiter에서 세션 제거
        rateLimiter.removeClient(sessionId);

        int count = connectionCount.decrementAndGet();
        log.info("WebSocket disconnected: sessionId={}, totalConnections={}",
            sessionId, count);
    }

    public int getConnectionCount() {
        return connectionCount.get();
    }
}
```

### 2b.6 Broadcast Service

#### application/port/out/BroadcastPort.java
```java
package com.crypto.prayer.application.port.out;

import com.crypto.prayer.adapter.in.websocket.dto.LiquidationMessage;
import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import com.crypto.prayer.adapter.in.websocket.dto.TickerMessage;

public interface BroadcastPort {

    void broadcastPrayerStats(PrayerResponse stats);

    void broadcastTicker(TickerMessage ticker);

    void broadcastLiquidation(LiquidationMessage liquidation);
}
```

#### application/service/BroadcastService.java
```java
package com.crypto.prayer.application.service;

import com.crypto.prayer.adapter.in.websocket.dto.LiquidationMessage;
import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import com.crypto.prayer.adapter.in.websocket.dto.TickerMessage;
import com.crypto.prayer.application.port.out.BroadcastPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class BroadcastService implements BroadcastPort {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    private static final String TOPIC_PRAYER = "/topic/prayer";
    private static final String TOPIC_TICKER = "/topic/ticker";
    private static final String TOPIC_LIQUIDATION = "/topic/liquidation";

    private final SimpMessagingTemplate messagingTemplate;

    public BroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcastPrayerStats(PrayerResponse stats) {
        messagingTemplate.convertAndSend(TOPIC_PRAYER, stats);
    }

    @Override
    public void broadcastTicker(TickerMessage ticker) {
        messagingTemplate.convertAndSend(TOPIC_TICKER, ticker);
        log.debug("Ticker broadcast: symbol={}, price={}",
            ticker.symbol(), ticker.price());
    }

    @Override
    public void broadcastLiquidation(LiquidationMessage liquidation) {
        messagingTemplate.convertAndSend(TOPIC_LIQUIDATION, liquidation);
        log.debug("Liquidation broadcast: symbol={}, side={}, value=${}",
            liquidation.symbol(), liquidation.side(), liquidation.usdValue());
    }
}
```

### 2b.7 Broadcast Scheduler (200ms 주기)

#### infrastructure/scheduler/BroadcastScheduler.java
```java
package com.crypto.prayer.infrastructure.scheduler;

import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import com.crypto.prayer.application.port.in.PrayerQuery;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.crypto.prayer.domain.model.PrayerStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BroadcastScheduler {

    private static final Logger log = LoggerFactory.getLogger(BroadcastScheduler.class);

    private final PrayerQuery prayerQuery;
    private final BroadcastPort broadcastPort;

    // 이전 상태 캐시 (변경 시에만 브로드캐스트)
    private volatile PrayerStats lastStats;

    public BroadcastScheduler(
            PrayerQuery prayerQuery,
            BroadcastPort broadcastPort) {
        this.prayerQuery = prayerQuery;
        this.broadcastPort = broadcastPort;
    }

    /**
     * 200ms마다 기도 통계 브로드캐스트
     */
    @Scheduled(fixedRate = 200)
    public void broadcastPrayerStats() {
        PrayerStats currentStats = prayerQuery.getCurrentStats();

        // 변경이 있을 때만 브로드캐스트
        if (hasChanged(currentStats)) {
            PrayerResponse response = PrayerResponse.from(
                currentStats.count().upCount(),
                currentStats.count().downCount(),
                currentStats.upRpm(),
                currentStats.downRpm()
            );

            broadcastPort.broadcastPrayerStats(response);
            lastStats = currentStats;
        }
    }

    private boolean hasChanged(PrayerStats current) {
        if (lastStats == null) {
            return true;
        }

        return current.count().upCount() != lastStats.count().upCount()
            || current.count().downCount() != lastStats.count().downCount()
            || Math.abs(current.upRpm() - lastStats.upRpm()) > 0.1
            || Math.abs(current.downRpm() - lastStats.downRpm()) > 0.1;
    }
}
```

### 2b.8 TokenBucketRateLimiter Bean 등록

#### infrastructure/config/WebSocketBeanConfig.java
```java
package com.crypto.prayer.infrastructure.config;

import com.crypto.prayer.adapter.in.websocket.ratelimit.TokenBucketRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class WebSocketBeanConfig {

    @Bean
    public TokenBucketRateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter();
    }

    /**
     * 10분마다 오래된 Rate Limiter 버킷 정리
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanupRateLimiter() {
        tokenBucketRateLimiter().cleanupStaleEntries(600_000); // 10분 유휴 시 제거
    }
}
```

---

## STOMP 토픽 구조

| 토픽 | 설명 | 메시지 타입 |
|------|------|-------------|
| `/topic/prayer` | 기도 통계 (200ms 주기) | PrayerResponse |
| `/topic/ticker` | BTC 시세 | TickerMessage |
| `/topic/liquidation` | 청산 정보 | LiquidationMessage |
| `/user/queue/errors` | 개인 에러 (Rate Limit 등) | ErrorResponse |

## 클라이언트 메시지

| Destination | 설명 | Payload |
|-------------|------|---------|
| `/app/prayer` | 기도 클릭 | `{"side": "up", "count": 1}` |

---

## 체크리스트

- [ ] WebSocket 설정
  - [ ] WebSocketConfig (STOMP)
  - [ ] SockJS 폴백 설정
- [ ] Rate Limiter 구현
  - [ ] TokenBucketRateLimiter (5회/초, 버스트 20)
  - [ ] RateLimitChannelInterceptor
  - [ ] RateLimitExceededException
- [ ] DTO 정의
  - [ ] PrayerRequest
  - [ ] PrayerResponse
  - [ ] TickerMessage
  - [ ] LiquidationMessage
- [ ] WebSocketController 구현
  - [ ] /app/prayer 핸들러
  - [ ] 에러 핸들러
- [ ] 세션 이벤트 리스너
  - [ ] 연결/해제 로깅
  - [ ] Rate Limiter 세션 정리
- [ ] BroadcastService 구현
  - [ ] 토픽별 브로드캐스트
- [ ] BroadcastScheduler
  - [ ] 200ms 주기 통계 브로드캐스트
  - [ ] 변경 감지 최적화
- [ ] 통합 테스트
  - [ ] WebSocket 연결 테스트
  - [ ] Rate Limit 테스트
  - [ ] 브로드캐스트 테스트

---

## 검증 명령어

```bash
# WebSocket 테스트
cd backend && ./gradlew test --tests "*WebSocket*"

# Rate Limiter 테스트
./gradlew test --tests "*RateLimiter*"

# 수동 테스트 (wscat)
npm install -g wscat
wscat -c ws://localhost:8080/ws
> ["CONNECT\naccept-version:1.2\n\n\u0000"]
> ["SUBSCRIBE\nid:sub-0\ndestination:/topic/prayer\n\n\u0000"]
```

---

## 다음 Phase
→ [Phase 3: 바이낸스 연동](phase3-binance-integration.md)
