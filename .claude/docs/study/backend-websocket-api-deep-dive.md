# WebSocket/STOMP 및 외부 API 연동 심화 가이드

> 이 문서는 "청산 기도 메타" 프로젝트의 실제 구현 코드를 기반으로 WebSocket/STOMP와 외부 API 연동의 핵심 개념을 상세히 설명합니다.

---

## 목차

1. [WebSocket 기본 개념](#1-websocket-기본-개념)
2. [STOMP 프로토콜 이해](#2-stomp-프로토콜-이해)
3. [실제 구현 코드 분석](#3-실제-구현-코드-분석)
4. [전체 메시지 흐름 정리](#4-전체-메시지-흐름-정리)
5. [Java HttpClient WebSocket](#5-java-httpclient-websocket)
6. [BinanceWebSocketClient 구현 분석](#6-binancewebsocketclient-구현-분석)
7. [Exponential Backoff 재연결 전략](#7-exponential-backoff-재연결-전략)
8. [스트림 핸들러 구현](#8-스트림-핸들러-구현)
9. [리소스 정리](#9-리소스-정리)
10. [전체 아키텍처](#10-전체-아키텍처)

---

## 1. WebSocket 기본 개념

### 1.1. HTTP vs WebSocket

```
HTTP 요청/응답 (매번 새 연결):
┌─────────┐                    ┌─────────┐
│ Client  │ ──── Request ────► │ Server  │
│         │ ◄─── Response ──── │         │
└─────────┘                    └─────────┘
           (연결 종료)

WebSocket (연결 유지):
┌─────────┐                    ┌─────────┐
│ Client  │ ══════════════════ │ Server  │
│         │ ◄── 양방향 통신 ──► │         │
└─────────┘                    └─────────┘
           (연결 유지, 실시간 메시지 교환)
```

### 1.2. WebSocket 핸드셰이크 과정

```
1. 클라이언트 → 서버 (HTTP Upgrade 요청):
   GET /ws HTTP/1.1
   Upgrade: websocket
   Connection: Upgrade
   Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==

2. 서버 → 클라이언트 (101 Switching Protocols):
   HTTP/1.1 101 Switching Protocols
   Upgrade: websocket
   Connection: Upgrade

3. 이후 WebSocket 프레임으로 통신
```

### 1.3. HTTP vs WebSocket 비교표

| 구분 | HTTP | WebSocket |
|------|------|-----------|
| 연결 | 요청마다 새 연결 | 한 번 연결 후 유지 |
| 방향 | 클라이언트 → 서버 (단방향) | 양방향 |
| 용도 | 일반 API | 실시간 통신 |
| 오버헤드 | 매 요청마다 헤더 전송 | 최초 핸드셰이크만 |

---

## 2. STOMP 프로토콜 이해

### 2.1. STOMP란?

STOMP(Simple Text Oriented Messaging Protocol)는 WebSocket 위에서 동작하는 **메시징 프로토콜**입니다.

### 2.2. 순수 WebSocket vs STOMP

**순수 WebSocket 사용 시 문제:**
```javascript
// 직접 모든 것을 처리해야 함
webSocket.onmessage = function(event) {
    var data = JSON.parse(event.data);
    if (data.type === "PRAYER") { ... }
    else if (data.type === "TICKER") { ... }
    else if (data.type === "LIQUIDATION") { ... }
    // 메시지 라우팅, 구독 관리 등을 직접 구현해야 함
};
```

**STOMP 사용 시:**
```javascript
// 프레임워크가 메시지 라우팅을 처리
stompClient.subscribe('/topic/prayer', function(message) {
    // 기도 통계만 처리
});
stompClient.subscribe('/topic/ticker', function(message) {
    // 시세만 처리
});
```

### 2.3. STOMP 프레임 구조

```
COMMAND          ← 명령어 (CONNECT, SUBSCRIBE, SEND, MESSAGE 등)
header1:value1   ← 헤더들
header2:value2

Body             ← 본문 (선택적)
^@               ← NULL 문자 (프레임 종료)
```

**예시 - 클라이언트가 메시지 전송:**
```
SEND
destination:/app/prayer
content-type:application/json

{"side":"up","count":5}^@
```

### 2.4. 프로젝트의 토픽 구조

```
/topic/prayer       ← 기도 통계 브로드캐스트
/topic/ticker       ← BTC 시세
/topic/liquidation  ← 청산 정보
/user/queue/errors  ← 개인 에러 메시지
```

---

## 3. 실제 구현 코드 분석

### 3.1. WebSocket 설정 (`WebSocketConfig.java`)

**파일 위치:** `backend/src/main/java/com/crypto/prayer/adapter/in/websocket/WebSocketConfig.java`

```java
@Configuration
@EnableScheduling              // 스케줄러 활성화 (브로드캐스트용)
@EnableWebSocketMessageBroker  // STOMP 메시지 브로커 활성화
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 1. SimpleBroker: 클라이언트가 구독할 수 있는 destination prefix
        registry.enableSimpleBroker("/topic", "/queue");

        // 2. ApplicationDestinationPrefixes: 클라이언트가 서버로 메시지를 보낼 때 prefix
        registry.setApplicationDestinationPrefixes("/app");

        // 3. UserDestinationPrefix: 특정 사용자에게 메시지 전송 시 prefix
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 순수 WebSocket 엔드포인트
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");

        // SockJS 폴백 포함 엔드포인트
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();  // 브라우저가 WebSocket을 지원하지 않을 때 폴백
    }
}
```

### 3.2. Prefix 흐름 이해

```
┌─────────────────────────────────────────────────────────────────┐
│                        클라이언트                                │
└─────────────────────────────────────────────────────────────────┘
              │                              │
              │ SEND /app/prayer             │ SUBSCRIBE /topic/prayer
              ▼                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring STOMP Broker                         │
│  ┌──────────────────┐        ┌──────────────────────────────┐   │
│  │ @MessageMapping  │        │ SimpleBroker                 │   │
│  │ 처리             │───────►│ /topic/* , /queue/*           │   │
│  │ (/app 제거 후    │        │ 구독자에게 메시지 전달         │   │
│  │  매칭)           │        └──────────────────────────────┘   │
│  └──────────────────┘                                           │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3. SockJS 폴백 전략

```
1차: WebSocket 시도
2차: XHR Streaming
3차: XHR Polling
4차: JSONP Polling
```

---

### 3.4. 메시지 핸들러 (`WebSocketController.java`)

**파일 위치:** `backend/src/main/java/com/crypto/prayer/adapter/in/websocket/WebSocketController.java`

```java
@Controller
public class WebSocketController {

    @MessageMapping("/prayer")  // /app/prayer로 전송된 메시지 처리
    public void handlePrayer(
            PrayerRequest request,            // 자동으로 JSON → 객체 변환
            SimpMessageHeaderAccessor headerAccessor) {  // 헤더 정보 접근

        String sessionId = headerAccessor.getSessionId();

        // Rate Limit 검증
        if (!rateLimiter.tryConsume(sessionId)) {
            throw new RateLimitExceededException(sessionId);
        }

        Side side = request.toSide();

        if (request.count() == 1) {
            prayerUseCase.pray(side, sessionId);
        } else {
            prayerUseCase.prayBatch(side, sessionId, request.count());
        }
    }
}
```

### 3.5. 메시지 흐름

```
클라이언트                                서버
    │                                      │
    │  SEND /app/prayer                    │
    │  {"side":"up","count":5}             │
    │ ───────────────────────────────────► │
    │                                      │
    │                    @MessageMapping("/prayer")
    │                    handlePrayer() 호출
    │                    PrayerRequest로 자동 변환
    │                                      │
```

### 3.6. `SimpMessageHeaderAccessor`가 제공하는 정보

```java
headerAccessor.getSessionId();       // WebSocket 세션 ID
headerAccessor.getUser();            // 인증된 사용자 정보
headerAccessor.getDestination();     // 목적지 (/app/prayer)
headerAccessor.getMessageType();     // MESSAGE, SUBSCRIBE 등
headerAccessor.getNativeHeader("X-Custom");  // 커스텀 헤더
```

---

### 3.7. 예외 처리와 에러 응답

```java
@MessageExceptionHandler(RateLimitExceededException.class)
@SendToUser("/queue/errors")  // 해당 사용자에게만 에러 전송
public ErrorResponse handleRateLimitExceeded(RateLimitExceededException ex) {
    log.warn("Rate limit exceeded for session: {}", ex.getSessionId());
    return new ErrorResponse("RATE_LIMIT_EXCEEDED", "Too many requests.");
}
```

**에러 전송 흐름:**
```
┌────────────────┐
│  사용자 A      │  ◄──── /user/queue/errors 로 에러 수신
└────────────────┘         (다른 사용자에게는 전송되지 않음)
```

**`@SendToUser` 내부 동작:**
```
실제 destination: /user/queue/errors
변환 후:          /user/{sessionId}/queue/errors

Spring이 자동으로 sessionId를 추가하여
해당 사용자에게만 메시지 전송
```

---

### 3.8. 브로드캐스트 서비스 (`BroadcastService.java`)

**파일 위치:** `backend/src/main/java/com/crypto/prayer/application/service/BroadcastService.java`

```java
@Service
public class BroadcastService implements BroadcastPort {

    private final SimpMessagingTemplate messagingTemplate;

    // 모든 구독자에게 전송
    @Override
    public void broadcastPrayerStats(PrayerResponse stats) {
        messagingTemplate.convertAndSend("/topic/prayer", stats);
    }

    @Override
    public void broadcastTicker(TickerMessage ticker) {
        messagingTemplate.convertAndSend("/topic/ticker", ticker);
    }

    @Override
    public void broadcastLiquidation(LiquidationMessage liquidation) {
        messagingTemplate.convertAndSend("/topic/liquidation", liquidation);
    }
}
```

### 3.9. `SimpMessagingTemplate` 주요 메서드

```java
// 모든 구독자에게 전송
messagingTemplate.convertAndSend("/topic/prayer", message);

// 특정 사용자에게 전송
messagingTemplate.convertAndSendToUser(
    "user-principal",        // 사용자 식별자
    "/queue/personal",       // destination
    message
);

// 헤더 포함 전송
messagingTemplate.convertAndSend(
    "/topic/prayer",
    message,
    Map.of("priority", "high")
);
```

---

### 3.10. 스케줄러 기반 브로드캐스트 (`BroadcastScheduler.java`)

**파일 위치:** `backend/src/main/java/com/crypto/prayer/infrastructure/scheduler/BroadcastScheduler.java`

```java
@Component
public class BroadcastScheduler {

    private volatile PrayerStats lastStats;  // volatile: 모든 스레드가 최신 값을 봄

    @Scheduled(fixedRate = 200)  // 200ms마다 실행
    public void broadcastPrayerStats() {
        PrayerStats currentStats = prayerQuery.getCurrentStats();

        // 변경이 있을 때만 전송 (네트워크 트래픽 최적화)
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
        if (lastStats == null) return true;

        // 카운트 변경 또는 RPM이 0.1 이상 변경된 경우
        return current.count().upCount() != lastStats.count().upCount()
            || current.count().downCount() != lastStats.count().downCount()
            || Math.abs(current.upRpm() - lastStats.upRpm()) > 0.1
            || Math.abs(current.downRpm() - lastStats.downRpm()) > 0.1;
    }
}
```

### 3.11. 변경 감지 최적화의 중요성

```
변경 감지 없이:
- 200ms × 5 users × 1KB = 초당 25KB 트래픽
- 대부분 동일한 데이터 반복 전송

변경 감지 적용:
- 실제 변경이 있을 때만 전송
- 네트워크 대역폭 절약
- 클라이언트 렌더링 최적화 (불필요한 리렌더 방지)
```

---

### 3.12. 세션 관리 (`WebSocketSessionListener.java`)

**파일 위치:** `backend/src/main/java/com/crypto/prayer/adapter/in/websocket/WebSocketSessionListener.java`

```java
@Component
public class WebSocketSessionListener {

    private final AtomicInteger connectedSessions = new AtomicInteger(0);

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        int count = connectedSessions.incrementAndGet();
        String sessionId = (String) event.getMessage()
            .getHeaders().get("simpSessionId");
        log.info("Connected: session={}, total={}", sessionId, count);
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        int count = connectedSessions.decrementAndGet();
        String sessionId = event.getSessionId();

        // 세션 종료 시 Rate Limiter 정리 (메모리 누수 방지)
        rateLimiter.removeClient(sessionId);

        log.info("Disconnected: session={}, total={}", sessionId, count);
    }
}
```

### 3.13. Spring WebSocket 이벤트 종류

```
SessionConnectEvent      - 연결 시도
SessionConnectedEvent    - 연결 완료
SessionSubscribeEvent    - 토픽 구독
SessionUnsubscribeEvent  - 구독 해제
SessionDisconnectEvent   - 연결 종료
```

---

## 4. 전체 메시지 흐름 정리

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           클라이언트                                     │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ STOMP Client (SockJS)                                          │    │
│  │                                                                 │    │
│  │  stompClient.send('/app/prayer', {}, JSON.stringify({         │    │
│  │      side: 'up', count: 5                                      │    │
│  │  }));                                                          │    │
│  │                                                                 │    │
│  │  stompClient.subscribe('/topic/prayer', callback);             │    │
│  └────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot Server                              │
│                                                                          │
│   /app/prayer 수신                                                       │
│        │                                                                 │
│        ▼                                                                 │
│   ┌──────────────────┐                                                   │
│   │ WebSocketController│                                                 │
│   │ @MessageMapping   │ ──► PrayerService ──► Redis                      │
│   └──────────────────┘                                                   │
│                                                                          │
│   ┌──────────────────┐      ┌───────────────┐                           │
│   │ BroadcastScheduler│ ──► │ BroadcastService│                          │
│   │ @Scheduled(200ms)│      │ convertAndSend │                           │
│   └──────────────────┘      └───────────────┘                           │
│                                    │                                     │
│                                    ▼                                     │
│                          /topic/prayer 브로드캐스트                       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                    모든 구독 클라이언트에게 메시지 전달
```

---

## 5. Java HttpClient WebSocket

### 5.1. Java 11+ HttpClient WebSocket API

```java
// WebSocket 연결 생성
HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

// 비동기 연결
CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .buildAsync(URI.create("wss://example.com/ws"), listener);
```

### 5.2. `WebSocket.Listener` 인터페이스

```java
public interface WebSocket.Listener {
    // 연결 열림
    default void onOpen(WebSocket webSocket) { }

    // 텍스트 메시지 수신
    default CompletionStage<?> onText(
        WebSocket webSocket,
        CharSequence data,
        boolean last) { return null; }

    // 연결 종료
    default CompletionStage<?> onClose(
        WebSocket webSocket,
        int statusCode,
        String reason) { return null; }

    // 에러 발생
    default void onError(
        WebSocket webSocket,
        Throwable error) { }
}
```

---

## 6. BinanceWebSocketClient 구현 분석

**파일 위치:** `backend/src/main/java/com/crypto/prayer/adapter/out/binance/BinanceWebSocketClient.java`

### 6.1. 클라이언트 구조

```java
@Component
public class BinanceWebSocketClient {

    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    // 여러 스트림을 동시에 관리
    private final ConcurrentHashMap<String, WebSocketConnection> connections;

    public BinanceWebSocketClient(BinanceConfig config) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        // 재연결 스케줄링용 스레드 풀 (2개 스트림: ticker, liquidation)
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.connections = new ConcurrentHashMap<>();
    }
}
```

### 6.2. ConcurrentHashMap 사용 이유

```java
// 멀티스레드 환경에서 안전하게 연결 관리
connections.put("ticker", tickerConnection);      // 스레드 1
connections.put("liquidation", liquidationConnection); // 스레드 2
connections.get("ticker");                        // 스레드 3

// 동시 접근해도 데이터 정합성 보장
```

---

### 6.3. 내부 WebSocketConnection 클래스

```java
private class WebSocketConnection implements WebSocket.Listener {

    private final String streamName;
    private final String url;
    private final Consumer<String> messageHandler;  // 메시지 처리 콜백
    private final ExponentialBackoff backoff;       // 재연결 전략
    private final StringBuilder messageBuffer;       // 분할 메시지 조합

    // volatile: 멀티스레드에서 가시성 보장
    private volatile WebSocket webSocket;
    private volatile boolean closed = false;   // 의도적 종료 플래그
    private volatile boolean connected = false;

    void connect() {
        if (closed) return;  // 이미 종료되었으면 연결 시도 안 함

        log.info("Connecting to {} stream: {}", streamName, url);

        httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI.create(url), this)  // 비동기 연결
            .thenAccept(ws -> {
                // 연결 성공
                this.webSocket = ws;
                this.connected = true;
                backoff.reset();  // 재시도 카운터 리셋
                log.info("Connected to {} stream", streamName);
            })
            .exceptionally(ex -> {
                // 연결 실패
                log.error("Failed to connect to {} stream: {}",
                    streamName, ex.getMessage());
                scheduleReconnect();  // 재연결 스케줄링
                return null;
            });
    }
}
```

### 6.4. 비동기 연결의 장점

```
동기식 연결:
스레드 1: 연결 시도 → [블로킹 대기 10초] → 타임아웃 → 다음 작업

비동기 연결:
스레드 1: 연결 시도 → 즉시 반환 → 다른 작업 수행
I/O 스레드: 백그라운드에서 연결 처리 → 완료 시 콜백 호출
```

---

### 6.5. 메시지 수신 처리

```java
@Override
public CompletionStage<?> onText(
        WebSocket webSocket,
        CharSequence data,
        boolean last) {

    // 1. 분할된 메시지를 버퍼에 추가
    messageBuffer.append(data);

    // 2. 메시지가 완성되었는지 확인
    if (last) {
        String message = messageBuffer.toString();
        messageBuffer.setLength(0);  // 버퍼 초기화

        // 3. 핸들러에게 전달
        try {
            messageHandler.accept(message);
        } catch (Exception e) {
            log.error("Error processing {} message: {}",
                streamName, e.getMessage());
        }
    }

    // 4. 다음 메시지 요청 (backpressure 제어)
    webSocket.request(1);
    return null;
}
```

### 6.6. 메시지 분할 처리 이유

```
대용량 JSON 메시지가 여러 프레임으로 분할될 수 있음:

프레임 1: {"symbol":"BTCUSDT","price":"    (last=false)
프레임 2: 42000.50","change":...          (last=false)
프레임 3: "2.5%"}                          (last=true)

messageBuffer로 조합:
{"symbol":"BTCUSDT","price":"42000.50","change":"2.5%"}
```

### 6.7. `webSocket.request(1)` - Backpressure 제어

```
클라이언트가 처리할 수 있는 만큼만 요청

request(1): 1개 메시지만 받겠다
request(10): 10개까지 받겠다
request(Long.MAX_VALUE): 무제한

→ 서버가 클라이언트보다 빠르면 메시지 큐가 쌓임
→ request()로 흐름 제어
```

---

### 6.8. 연결 종료 및 에러 처리

```java
@Override
public CompletionStage<?> onClose(
        WebSocket webSocket,
        int statusCode,
        String reason) {

    log.warn("{} WebSocket closed: {} - {}", streamName, statusCode, reason);
    connected = false;

    // 의도적 종료가 아니면 재연결
    if (!closed) {
        scheduleReconnect();
    }
    return null;
}

@Override
public void onError(WebSocket webSocket, Throwable error) {
    log.error("{} WebSocket error: {}", streamName, error.getMessage());
    connected = false;

    if (!closed) {
        scheduleReconnect();
    }
}
```

### 6.9. WebSocket 연결 종료 상태 코드 (RFC 6455)

```
1000: 정상 종료 (NORMAL_CLOSURE)
1001: Going Away (서버 셧다운)
1002: Protocol Error
1003: Unsupported Data
1006: Abnormal Closure (비정상 종료, 네트워크 끊김)
1011: Unexpected Condition
```

---

## 7. Exponential Backoff 재연결 전략

**파일 위치:** `backend/src/main/java/com/crypto/prayer/adapter/out/binance/reconnect/ExponentialBackoff.java`

### 7.1. 왜 Exponential Backoff인가?

```
고정 간격 재시도의 문제:
- 서버가 과부하 상태일 때
- 100개 클라이언트가 동시에 1초마다 재연결 시도
- 서버에 초당 100개 연결 요청 → 더 큰 부하

Exponential Backoff:
- 재시도 간격을 점차 증가
- 서버에 회복할 시간을 줌
- Jitter로 동시 재연결 방지
```

### 7.2. 구현 분석

```java
public class ExponentialBackoff {

    private final long initialDelayMs;   // 첫 대기: 1초
    private final long maxDelayMs;       // 최대 대기: 30초
    private final double multiplier;      // 증가 배율: 2배
    private final double jitterFactor;    // 랜덤 변동: ±10%

    private int attempt = 0;

    public long nextDelayMs() {
        // 1. 지수적 증가: delay = initial * multiplier^attempt
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt));

        // 2. 최대값 제한
        delay = Math.min(delay, maxDelayMs);

        // 3. Jitter 추가 (±10%)
        if (jitterFactor > 0) {
            // ThreadLocalRandom: 멀티스레드에서 효율적인 난수 생성
            double jitter = delay * jitterFactor *
                (ThreadLocalRandom.current().nextDouble() * 2 - 1);
            delay = (long) (delay + jitter);
        }

        attempt++;
        return Math.max(delay, 0);  // 음수 방지
    }

    public void reset() {
        attempt = 0;  // 연결 성공 시 호출
    }
}
```

### 7.3. 실제 재시도 간격 예시

```
attempt 0: 1000ms × 2^0 = 1000ms (±100ms) → ~900~1100ms
attempt 1: 1000ms × 2^1 = 2000ms (±200ms) → ~1800~2200ms
attempt 2: 1000ms × 2^2 = 4000ms (±400ms) → ~3600~4400ms
attempt 3: 1000ms × 2^3 = 8000ms (±800ms) → ~7200~8800ms
attempt 4: 1000ms × 2^4 = 16000ms (±1600ms) → ~14400~17600ms
attempt 5+: 30000ms (최대값)
```

### 7.4. Jitter의 중요성

```
Jitter 없이 (100개 클라이언트):
t=0초:    100개 연결 시도, 모두 실패
t=1초:    100개 재연결 시도
t=2초:    100개 재연결 시도
→ 서버에 주기적으로 부하 집중

Jitter 적용:
t=0초:     100개 연결 시도, 모두 실패
t=0.9~1.1초: 분산되어 재연결 시도
t=1.8~2.2초: 더 분산되어 재연결 시도
→ 부하가 시간에 걸쳐 분산
```

---

### 7.5. 스케줄러 기반 재연결

```java
private void scheduleReconnect() {
    if (closed) return;  // 의도적 종료면 재연결 안 함

    long delay = backoff.nextDelayMs();
    log.info("Scheduling reconnect for {} in {}ms (attempt {})",
        streamName, delay, backoff.getAttempt());

    // ScheduledExecutorService로 지연 실행
    scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
}
```

### 7.6. ScheduledExecutorService vs Timer

```java
// Timer (구식, 권장하지 않음)
Timer timer = new Timer();
timer.schedule(task, delay);
// - 단일 스레드
// - 예외 발생 시 전체 타이머 중단

// ScheduledExecutorService (권장)
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
// - 스레드 풀 사용
// - 예외 발생해도 다른 작업에 영향 없음
// - 더 정밀한 시간 제어
```

---

## 8. 스트림 핸들러 구현

### 8.1. LiquidationStreamHandler

**파일 위치:** `backend/src/main/java/com/crypto/prayer/adapter/out/binance/LiquidationStreamHandler.java`

```java
@Component
public class LiquidationStreamHandler {

    @PostConstruct  // Bean 초기화 완료 후 자동 실행
    public void start() {
        webSocketClient.connect(
            "liquidation",                          // 스트림 이름
            config.getLiquidationStreamUrl(),       // wss://fstream.binance.com/ws/!forceOrder@arr
            this::handleMessage                     // 메서드 참조 (콜백)
        );
        log.info("Liquidation stream handler started");
    }

    public void handleMessage(String message) {
        try {
            // 1. JSON 파싱 (바이낸스 DTO)
            BinanceLiquidationEvent event = objectMapper.readValue(
                message, BinanceLiquidationEvent.class);

            // 2. 도메인 모델로 변환 (외부 의존성 격리)
            Liquidation liquidation = Liquidation.of(
                event.getSymbol(),
                event.getSide(),
                event.getQuantity(),
                event.getPrice()
            );

            // 3. 브로드캐스트 DTO로 변환 (클라이언트용)
            LiquidationMessage liqMessage = LiquidationMessage.of(
                liquidation.symbol(),
                liquidation.side().name(),
                liquidation.quantity(),
                liquidation.price()
            );

            // 4. 모든 클라이언트에게 전송
            broadcastPort.broadcastLiquidation(liqMessage);

            // 5. 대형 청산 로깅
            if (liquidation.isLarge()) {
                log.info("Large liquidation: {} {} {}",
                    liquidation.symbol(),
                    liquidation.side(),
                    liquidation.formattedValue());
            }

        } catch (Exception e) {
            log.error("Failed to parse liquidation message: {}", e.getMessage());
        }
    }
}
```

### 8.2. 3단계 변환의 이유 (헥사고날 아키텍처)

```
┌────────────────────┐      ┌────────────────────┐      ┌────────────────────┐
│ BinanceLiquidation │  →   │    Liquidation     │  →   │ LiquidationMessage │
│      Event         │      │   (Domain Model)   │      │    (DTO)           │
│                    │      │                    │      │                    │
│ 외부 API 형식       │      │ 비즈니스 로직용      │      │ 클라이언트 전송용    │
│ 바이낸스 의존적      │      │ 외부 의존성 없음     │      │ 불필요한 필드 제외   │
└────────────────────┘      └────────────────────┘      └────────────────────┘
```

---

### 8.3. TickerStreamHandler

**파일 위치:** `backend/src/main/java/com/crypto/prayer/adapter/out/binance/TickerStreamHandler.java`

```java
@Component
public class TickerStreamHandler {

    // 최신 시세를 메모리에 캐싱 (AtomicReference로 스레드 안전)
    private final AtomicReference<Ticker> latestTicker = new AtomicReference<>();

    @PostConstruct
    public void start() {
        webSocketClient.connect(
            "ticker",
            config.getTickerStreamUrl(),  // wss://fstream.binance.com/ws/btcusdt@ticker
            this::handleMessage
        );
    }

    public void handleMessage(String message) {
        try {
            BinanceTickerEvent event = objectMapper.readValue(
                message, BinanceTickerEvent.class);

            Ticker ticker = Ticker.of(
                event.symbol(),
                event.getPrice(),
                event.getPriceChangePercent()
            );

            // 최신 시세 캐싱
            latestTicker.set(ticker);

            TickerMessage tickerMessage = TickerMessage.of(
                ticker.symbol(),
                ticker.price(),
                ticker.priceChange24h()
            );

            broadcastPort.broadcastTicker(tickerMessage);

        } catch (Exception e) {
            log.error("Failed to parse ticker message: {}", e.getMessage());
        }
    }

    // 다른 서비스에서 최신 시세 조회 가능
    public Ticker getLatestTicker() {
        return latestTicker.get();
    }
}
```

### 8.4. AtomicReference 사용 이유

```java
// 문제: 일반 참조는 스레드 안전하지 않음
private Ticker latestTicker;

// 스레드 1: latestTicker = ticker1;
// 스레드 2: Ticker t = latestTicker;  // ticker1? 이전 값?

// 해결: AtomicReference
private final AtomicReference<Ticker> latestTicker = new AtomicReference<>();

latestTicker.set(ticker);     // 원자적 쓰기
latestTicker.get();           // 원자적 읽기
```

---

## 9. 리소스 정리

### 9.1. `@PreDestroy` 사용

```java
@PreDestroy  // 애플리케이션 종료 시 자동 호출
public void destroy() {
    // 1. 모든 WebSocket 연결 종료
    connections.values().forEach(WebSocketConnection::close);

    // 2. 스케줄러 종료
    scheduler.shutdown();

    log.info("BinanceWebSocketClient destroyed");
}
```

### 9.2. 연결 종료 흐름

```java
void close() {
    closed = true;      // 재연결 방지 플래그
    connected = false;

    if (webSocket != null) {
        // 정상 종료 프레임 전송
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown");
    }
}
```

### 9.3. `@PreDestroy`가 중요한 이유

```
리소스 정리하지 않으면:
- WebSocket 연결이 열린 채로 남음
- 스케줄러 스레드가 계속 실행
- 메모리 누수 + 리소스 낭비
- 서버 재시작 시 "이미 바인딩됨" 에러 가능
```

---

## 10. 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Spring Boot Application                           │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        BinanceWebSocketClient                        │    │
│  │  ┌──────────────────────┐    ┌──────────────────────┐               │    │
│  │  │ WebSocketConnection  │    │ WebSocketConnection  │               │    │
│  │  │ (ticker)             │    │ (liquidation)        │               │    │
│  │  │                      │    │                      │               │    │
│  │  │ ExponentialBackoff   │    │ ExponentialBackoff   │               │    │
│  │  └──────────┬───────────┘    └──────────┬───────────┘               │    │
│  └─────────────┼────────────────────────────┼───────────────────────────┘    │
│                │                            │                                 │
│                ▼                            ▼                                 │
│  ┌─────────────────────────┐    ┌─────────────────────────┐                  │
│  │   TickerStreamHandler   │    │ LiquidationStreamHandler│                  │
│  │   @PostConstruct start()│    │   @PostConstruct start()│                  │
│  │   handleMessage()       │    │   handleMessage()       │                  │
│  └───────────┬─────────────┘    └───────────┬─────────────┘                  │
│              │                              │                                 │
│              ▼                              ▼                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        BroadcastService                               │   │
│  │  convertAndSend("/topic/ticker", ...)                                 │   │
│  │  convertAndSend("/topic/liquidation", ...)                            │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│              │                                                               │
│              ▼                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    Spring STOMP Message Broker                        │   │
│  │              /topic/ticker    /topic/liquidation                      │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│              │                              │                                 │
└──────────────┼──────────────────────────────┼─────────────────────────────────┘
               ▼                              ▼
        ┌─────────────┐               ┌─────────────┐
        │  Client A   │               │  Client B   │
        │  (Browser)  │               │  (Browser)  │
        └─────────────┘               └─────────────┘
```

---

## 핵심 개념 요약

| 개념 | 설명 | 프로젝트 적용 |
|------|------|--------------|
| STOMP | WebSocket 위의 메시징 프로토콜 | `/topic/*` 구독, `/app/*` 전송 |
| SimpleBroker | 인메모리 메시지 브로커 | 모든 클라이언트에 브로드캐스트 |
| @SendToUser | 특정 사용자에게만 전송 | 에러 메시지 개별 전송 |
| @Scheduled | 주기적 작업 실행 | 200ms마다 통계 브로드캐스트 |
| HttpClient WebSocket | Java 11+ 비동기 WebSocket | 바이낸스 API 연결 |
| Exponential Backoff | 재시도 간격 지수 증가 | 바이낸스 재연결 전략 |
| Jitter | 재시도 시간 랜덤 분산 | 동시 재연결 방지 |
| @PreDestroy | 빈 종료 시 정리 작업 | 연결 종료, 스레드 풀 종료 |
| volatile | 멀티스레드 가시성 보장 | 연결 상태 플래그 |
| AtomicReference | 스레드 안전한 참조 | 최신 시세 캐싱 |

---

## 추천 학습 순서

1. **WebSocketConfig 분석**: STOMP 설정 이해
2. **WebSocketController 분석**: 메시지 핸들링 흐름 파악
3. **BroadcastService 분석**: 메시지 전송 방식 이해
4. **BinanceWebSocketClient 분석**: 외부 API 연결 패턴
5. **ExponentialBackoff 분석**: 재연결 전략 구현
6. **StreamHandler 분석**: 데이터 변환 흐름

각 단계에서 실제 코드를 디버깅하며 데이터 흐름을 추적해보세요.
