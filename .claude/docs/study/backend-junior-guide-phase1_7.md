# 백엔드 주니어 개발자를 위한 학습 가이드

> 이 문서는 "청산 기도 메타" 프로젝트의 백엔드 구현을 통해 주니어 개발자가 실무에서 자주 사용하는 패턴과 기술을 학습할 수 있도록 정리한 가이드입니다.

---

## 목차

1. [헥사고날 아키텍처](#1-헥사고날-아키텍처)
2. [Java Record와 불변 객체](#2-java-record와-불변-객체)
3. [Redis 연동과 원자적 연산](#3-redis-연동과-원자적-연산)
4. [폴백 패턴과 장애 대응](#4-폴백-패턴과-장애-대응)
5. [Rate Limiter (토큰 버킷 알고리즘)](#5-rate-limiter-토큰-버킷-알고리즘)
6. [WebSocket과 STOMP](#6-websocket과-stomp)
7. [외부 API 연동과 재연결 전략](#7-외부-api-연동과-재연결-전략)
8. [동시성 처리](#8-동시성-처리)
9. [테스트 작성 전략](#9-테스트-작성-전략)
10. [실무에서 자주 실수하는 부분](#10-실무에서-자주-실수하는-부분)

---

## 1. 헥사고날 아키텍처

### 1.1. 개념

헥사고날 아키텍처(Ports & Adapters)는 비즈니스 로직을 외부 시스템으로부터 격리하는 설계 패턴입니다.

```
┌─────────────────────────────────────────────────────────┐
│                     외부 세계                            │
│        (WebSocket, REST API, 스케줄러 등)                │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                  Adapter (In)                           │
│     WebSocketController, REST Controller 등             │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼ Port (In) - 인터페이스
┌─────────────────────────────────────────────────────────┐
│                 Application Layer                       │
│          PrayerService (유스케이스 구현)                  │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼ Port (Out) - 인터페이스
┌─────────────────────────────────────────────────────────┐
│                  Adapter (Out)                          │
│    RedisPrayerCountAdapter, BinanceWebSocketClient      │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    외부 시스템                           │
│              (Redis, Binance API 등)                    │
└─────────────────────────────────────────────────────────┘
```

### 1.2. 프로젝트 패키지 구조

```
backend/src/main/java/com/crypto/prayer/
├── domain/                      ◄── 비즈니스 핵심 (외부 의존성 없음)
│   └── model/
│       ├── Side.java                - 기도 방향 enum
│       ├── Prayer.java              - 기도 도메인 모델
│       ├── PrayerCount.java         - 카운트 Value Object
│       └── PrayerStats.java         - 통계 Value Object
│
├── application/                 ◄── 유스케이스 (비즈니스 로직 조율)
│   ├── port/
│   │   ├── in/                      - Driving Ports (외부 → 내부 호출)
│   │   │   ├── PrayerUseCase.java
│   │   │   └── PrayerQuery.java
│   │   └── out/                     - Driven Ports (내부 → 외부 호출)
│   │       ├── PrayerCountPort.java
│   │       └── BroadcastPort.java
│   └── service/
│       └── PrayerService.java       - 유스케이스 구현체
│
├── adapter/                     ◄── 외부 시스템 연동
│   ├── in/
│   │   └── websocket/               - WebSocket 입력 어댑터
│   └── out/
│       ├── redis/                   - Redis 출력 어댑터
│       └── binance/                 - 바이낸스 출력 어댑터
│
└── infrastructure/              ◄── 인프라 (폴백, 설정 등)
    ├── fallback/
    └── scheduler/
```

### 1.3. 왜 이런 구조를 사용하나요?

| 이점 | 설명 | 예시 |
|------|------|------|
| **테스트 용이성** | Redis 없이 비즈니스 로직 테스트 가능 | Mock으로 Port 대체 |
| **유연성** | 저장소 교체 시 비즈니스 로직 변경 없음 | Redis → MySQL 교체 |
| **의존성 방향** | 항상 안쪽(도메인)을 향함 | 핵심 로직 보호 |

### 1.4. 실제 구현 예시

**Port 인터페이스 정의** (`application/port/out/PrayerCountPort.java`):
```java
public interface PrayerCountPort {
    long increment(Side side, long delta);
    PrayerCount getCount();
    void merge(PrayerCount delta);
    boolean isAvailable();
}
```

**Service가 Port를 사용** (`application/service/PrayerService.java:26-30`):
```java
@Override
public Prayer pray(Side side, String sessionId) {
    Prayer prayer = Prayer.create(side, sessionId);
    countPort.increment(side, 1);  // Port 인터페이스 호출
    rpmCalculator.record(side);
    return prayer;
}
```

**핵심**: Service는 `PrayerCountPort` 인터페이스만 알면 됩니다. Redis인지 InMemory인지 모릅니다.

---

## 2. Java Record와 불변 객체

### 2.1. Java Record란?

Java 16+에서 도입된 불변 데이터 클래스를 간결하게 정의하는 방법입니다.

**기존 방식 (보일러플레이트 코드)**:
```java
public class PrayerCount {
    private final long upCount;
    private final long downCount;

    public PrayerCount(long upCount, long downCount) {
        this.upCount = upCount;
        this.downCount = downCount;
    }

    public long getUpCount() { return upCount; }
    public long getDownCount() { return downCount; }

    @Override
    public boolean equals(Object o) { /* ... */ }

    @Override
    public int hashCode() { /* ... */ }

    @Override
    public String toString() { /* ... */ }
}
```

**Record 사용** (`domain/model/PrayerCount.java`):
```java
public record PrayerCount(
    long upCount,
    long downCount
) {
    // equals(), hashCode(), toString() 자동 생성
    // 생성자, getter 자동 생성
}
```

### 2.2. 불변 객체의 중요성

**가변 객체의 위험성**:
```java
// ❌ 가변 객체 - 원본이 변경됨
PrayerCount count = new PrayerCount(100, 50);
count.setUpCount(count.getUpCount() + 1);  // 원본 객체가 변경됨!

// 다른 곳에서 count를 사용하면 의도치 않은 값을 볼 수 있음
```

**불변 객체 패턴** (`domain/model/PrayerCount.java:11-13`):
```java
// ✅ 불변 객체 - 새 객체 반환
public PrayerCount incrementUp(long delta) {
    return new PrayerCount(upCount + delta, downCount);  // 새 객체 반환
}
```

**실제 사용 예**:
```java
PrayerCount original = new PrayerCount(100, 50);
PrayerCount increased = original.incrementUp(5);

// original: { upCount: 100, downCount: 50 } - 변경되지 않음
// increased: { upCount: 105, downCount: 50 } - 새 객체
```

### 2.3. 팩토리 메서드 패턴

**직접 생성자 호출의 문제**:
```java
// ❌ 매번 UUID, 타임스탬프를 직접 생성해야 함
new Prayer(UUID.randomUUID().toString(), Side.UP, "session", Instant.now());
```

**팩토리 메서드 사용** (`domain/model/Prayer.java:8-14`):
```java
public static Prayer create(Side side, String sessionId) {
    return new Prayer(
        UUID.randomUUID().toString(),
        side,
        sessionId,
        Instant.now()
    );
}

// ✅ 필요한 것만 전달
Prayer prayer = Prayer.create(Side.UP, "session-123");
```

### 2.4. Value Object vs Entity

| 구분 | Value Object | Entity |
|------|-------------|--------|
| 동등성 | 값으로 비교 | ID로 비교 |
| 불변성 | 불변 | 가변 가능 |
| 예시 | `PrayerCount` | `User` |

```java
// Value Object - 값이 같으면 같은 객체
new PrayerCount(100, 50).equals(new PrayerCount(100, 50))  // true

// Entity - ID가 같아야 같은 객체
new User(1L, "A").equals(new User(1L, "B"))  // true (ID가 같으면)
```

---

## 3. Redis 연동과 원자적 연산

### 3.1. Redis 키 전략

**날짜별 키 분리** (`adapter/out/redis/RedisKeyGenerator.java:19-23`):
```java
public String generateKey(LocalDate date, Side side) {
    String dateStr = date.format(DATE_FORMAT);  // "20240120"
    return String.format("%s:%s:%s", PREFIX, dateStr, side.getKey());
    // 결과: "prayer:20240120:up"
}
```

**왜 날짜별로 키를 분리하나요?**
- 매일 자정(UTC 00:00)에 카운트 자동 리셋
- 어제 데이터 조회 가능 (통계용)
- TTL 48시간으로 자동 삭제

### 3.2. 원자적 연산 (Atomic Operation)

**동시성 문제 - 일반 연산**:
```
사용자A: count = 100 → count = 101
사용자B: count = 100 → count = 101  ❌ 둘 다 100을 읽어서 101이 됨
```

**원자적 연산 사용** (`adapter/out/redis/RedisPrayerCountAdapter.java:30-32`):
```java
@Override
public long increment(Side side, long delta) {
    String key = keyGenerator.generateKey(side);
    Long result = redisTemplate.opsForValue().increment(key, delta);  // INCRBY
    // ...
}
```

**Redis INCRBY 명령**:
```
사용자A: INCRBY count 1 → 101
사용자B: INCRBY count 1 → 102  ✅ 원자적으로 처리됨
```

### 3.3. MGET으로 효율적인 조회

**여러 키를 한 번에 조회** (`adapter/out/redis/RedisPrayerCountAdapter.java:45-60`):
```java
@Override
public PrayerCount getCount() {
    List<String> keys = List.of(
        keyGenerator.getUpKey(),    // "prayer:20240120:up"
        keyGenerator.getDownKey()   // "prayer:20240120:down"
    );

    List<String> values = redisTemplate.opsForValue().multiGet(keys);  // MGET
    // 한 번의 네트워크 왕복으로 두 값을 조회
}
```

**주요 Redis 명령어 매핑**:

| 메서드 | Redis 명령 | 설명 |
|--------|-----------|------|
| `increment()` | `INCRBY` | 원자적 증가 |
| `multiGet()` | `MGET` | 여러 키 한번에 조회 |
| `expire()` | `EXPIRE` | TTL 설정 |

### 3.4. TTL 설정 시 주의점

```java
// TTL 설정 - 최초 생성 시에만
if (result != null && result == delta) {  // 처음 증가된 경우
    redisTemplate.expire(key, Duration.ofSeconds(keyGenerator.getTtlSeconds()));
}
```

**`result == delta` 조건의 의미**:
- 키가 없었다면: `INCRBY key 1` → 결과 `1` (delta와 같음)
- 키가 있었다면: `INCRBY key 1` → 결과 `101` 등 (delta와 다름)
- 첫 생성 시에만 TTL을 설정하여 중복 설정 방지

---

## 4. 폴백 패턴과 장애 대응

### 4.1. 폴백 패턴이란?

주 시스템(Redis)에 장애가 발생해도 서비스가 중단되지 않도록 대체 시스템(InMemory)으로 자동 전환하는 패턴입니다.

### 4.2. 구현 구조

```
정상 상태:
┌──────────┐     ┌─────────────────┐     ┌───────┐
│ 클라이언트 │ ──► │ FallbackManager │ ──► │ Redis │ ✓
└──────────┘     └─────────────────┘     └───────┘

Redis 장애:
┌──────────┐     ┌─────────────────┐     ┌───────┐
│ 클라이언트 │ ──► │ FallbackManager │ ──► │ Redis │ ✗
└──────────┘     └─────────────────┘     └───────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │    InMemory     │  ◄── 임시 저장
                 └─────────────────┘

Redis 복구:
                 ┌─────────────────┐
                 │    InMemory     │ ──┐
                 └─────────────────┘   │ 데이터 병합
                                       ▼
┌──────────┐     ┌─────────────────┐     ┌───────┐
│ 클라이언트 │ ──► │ FallbackManager │ ──► │ Redis │ ✓
└──────────┘     └─────────────────┘     └───────┘
```

### 4.3. 핵심 구현

**폴백 전환 로직** (`infrastructure/fallback/FallbackManager.java:31-43`):
```java
@Override
public long increment(Side side, long delta) {
    if (usingFallback.get()) {
        return inMemoryAdapter.increment(side, delta);  // 이미 폴백 중
    }

    try {
        return redisAdapter.increment(side, delta);     // 정상 시도
    } catch (Exception e) {
        log.warn("Redis increment failed, switching to fallback: {}", e.getMessage());
        usingFallback.set(true);                        // 폴백 전환
        return inMemoryAdapter.increment(side, delta);
    }
}
```

**자동 복구 스케줄러** (`infrastructure/fallback/FallbackManager.java:72-91`):
```java
@Scheduled(fixedRate = 30000)  // 30초마다 실행
public void checkAndRecover() {
    if (!usingFallback.get()) {
        return;  // 폴백 모드가 아니면 스킵
    }

    if (redisAdapter.isAvailable()) {
        log.info("Redis connection recovered, merging fallback data...");

        // 누적된 인메모리 데이터를 Redis로 병합
        if (inMemoryAdapter.hasData()) {
            PrayerCount fallbackData = inMemoryAdapter.getAndReset();
            redisAdapter.merge(fallbackData);
            log.info("Merged {} up and {} down prayers to Redis",
                fallbackData.upCount(), fallbackData.downCount());
        }

        usingFallback.set(false);  // 정상 모드로 복귀
        log.info("Switched back to Redis");
    }
}
```

### 4.4. 폴백 시 고려사항

| 고려사항 | 설명 | 이 프로젝트에서의 처리 |
|---------|------|---------------------|
| 데이터 유실 | 폴백 중 서버 재시작 시 인메모리 데이터 유실 | 허용 (카운트 데이터는 중요도 낮음) |
| 복구 지연 | Redis 복구 후 최대 30초까지 폴백 유지 | 허용 가능한 수준 |
| 상태 동기화 | 복구 시 데이터 병합 필요 | `merge()` 메서드로 처리 |

---

## 5. Rate Limiter (토큰 버킷 알고리즘)

### 5.1. 왜 Rate Limiter가 필요한가요?

- DDoS 공격 방지
- 서버 리소스 보호
- 공정한 사용 보장

### 5.2. 토큰 버킷 알고리즘

```
┌─────────────────────────────────────────────────┐
│                   토큰 버킷                        │
│  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐      │
│  │ ● │ ● │ ● │ ● │ ● │   │   │   │   │   │      │
│  └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘      │
│    토큰: 5개            최대: 20개 (버스트)        │
│                                                  │
│  ▲ 초당 5개 충전                                  │
│  ▼ 요청마다 1개 소비                              │
└─────────────────────────────────────────────────┘
```

- **토큰**: 요청을 처리할 수 있는 권한
- **초당 5개 충전**: 일정 속도로 토큰 생성
- **최대 20개**: 버스트(순간 대량 요청) 허용량
- **요청 시 1개 소비**: 토큰이 없으면 요청 거부

### 5.3. 실제 구현

**토큰 버킷 클래스** (`adapter/in/websocket/ratelimit/TokenBucketRateLimiter.java`):
```java
public class TokenBucketRateLimiter implements RateLimiter {

    private static final double REFILL_RATE = 5.0;    // 초당 토큰 충전량
    private static final long MAX_TOKENS = 20;        // 최대 버스트 토큰
    private static final long REFILL_INTERVAL_MS = 200;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, k -> new TokenBucket());
        return bucket.tryConsume();
    }

    // ...
}
```

**토큰 소비 로직** (`TokenBucketRateLimiter.java:42-49`):
```java
synchronized boolean tryConsume() {
    refill();  // 먼저 토큰 충전

    if (tokens.get() > 0) {
        tokens.decrementAndGet();  // 토큰 1개 소비
        return true;               // 요청 허용
    }
    return false;  // 토큰 부족 - 요청 거부
}
```

**토큰 충전 로직** (`TokenBucketRateLimiter.java:52-64`):
```java
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
```

### 5.4. 클라이언트별 독립 관리

```java
// 클라이언트마다 별도의 버킷
ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

// client1이 토큰을 다 써도 client2에는 영향 없음
buckets.computeIfAbsent("client1", k -> new TokenBucket());
buckets.computeIfAbsent("client2", k -> new TokenBucket());
```

### 5.5. 메모리 누수 방지

```java
public void cleanupStaleEntries(long maxIdleTimeMs) {
    long now = System.currentTimeMillis();
    buckets.entrySet().removeIf(entry -> {
        TokenBucket bucket = entry.getValue();
        return (now - bucket.lastRefillTime) > maxIdleTimeMs;  // 10분 이상 미사용 시 제거
    });
}
```

---

## 6. WebSocket과 STOMP

### 6.1. WebSocket vs HTTP

| 구분 | HTTP | WebSocket |
|------|------|-----------|
| 연결 | 요청마다 새 연결 | 한 번 연결 후 유지 |
| 방향 | 클라이언트 → 서버 (단방향) | 양방향 |
| 용도 | 일반 API | 실시간 통신 |

### 6.2. STOMP 프로토콜

STOMP(Simple Text Oriented Messaging Protocol)는 WebSocket 위에서 동작하는 메시지 프로토콜입니다.

**토픽 구조**:
```
/topic/prayer       ← 기도 통계 브로드캐스트
/topic/ticker       ← BTC 시세
/topic/liquidation  ← 청산 정보
/user/queue/errors  ← 개인 에러 메시지
```

### 6.3. WebSocket 설정

**STOMP 설정** (`adapter/in/websocket/WebSocketConfig.java`):
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");            // 구독 prefix
        registry.setApplicationDestinationPrefixes("/app"); // 송신 prefix
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();  // SockJS 폴백 지원
    }
}
```

### 6.4. 메시지 핸들러

**클라이언트 메시지 처리** (`adapter/in/websocket/WebSocketController.java:28-44`):
```java
@Controller
public class WebSocketController {

    @MessageMapping("/prayer")  // /app/prayer로 전송된 메시지 처리
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
    }
}
```

### 6.5. 브로드캐스트 스케줄러

**200ms마다 통계 전송** (`infrastructure/scheduler/BroadcastScheduler.java:30-47`):
```java
@Scheduled(fixedRate = 200)  // 200ms마다 실행
public void broadcastPrayerStats() {
    PrayerStats currentStats = prayerQuery.getCurrentStats();

    // 변경이 있을 때만 브로드캐스트 (불필요한 전송 방지)
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
```

---

## 7. 외부 API 연동과 재연결 전략

### 7.1. Exponential Backoff

외부 시스템 연결 실패 시 재시도 간격을 점차 늘려가는 전략입니다.

```
시도 1: 1초 대기 후 재연결
시도 2: 2초 대기 후 재연결
시도 3: 4초 대기 후 재연결
시도 4: 8초 대기 후 재연결
시도 5: 16초 대기 후 재연결
시도 6+: 30초 대기 (최대값)
```

**구현** (`adapter/out/binance/reconnect/ExponentialBackoff.java`):
```java
public class ExponentialBackoff {

    private final long initialDelayMs;  // 1000ms
    private final long maxDelayMs;      // 30000ms
    private final double multiplier;    // 2.0
    private final double jitterFactor;  // 0.1 (±10% 랜덤)

    private int attempt = 0;

    public long nextDelayMs() {
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt));
        delay = Math.min(delay, maxDelayMs);  // 최대값 제한

        // Jitter 추가 (여러 클라이언트가 동시에 재연결하는 것 방지)
        double jitter = delay * jitterFactor * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
        delay = (long) (delay + jitter);

        attempt++;
        return Math.max(delay, 0);
    }

    public void reset() {
        attempt = 0;  // 연결 성공 시 리셋
    }
}
```

### 7.2. Java HttpClient WebSocket

**비동기 WebSocket 연결** (`adapter/out/binance/BinanceWebSocketClient.java:94-113`):
```java
void connect() {
    if (closed) return;

    log.info("Connecting to {} stream: {}", streamName, url);

    httpClient.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .buildAsync(URI.create(url), this)          // 비동기 연결
        .thenAccept(ws -> {
            this.webSocket = ws;
            this.connected = true;
            backoff.reset();                        // 연결 성공 - 재시도 카운터 리셋
            log.info("Connected to {} stream", streamName);
        })
        .exceptionally(ex -> {
            log.error("Failed to connect to {} stream: {}", streamName, ex.getMessage());
            scheduleReconnect();                    // 연결 실패 - 재연결 스케줄링
            return null;
        });
}
```

### 7.3. 메시지 수신 처리

**분할된 메시지 처리** (`BinanceWebSocketClient.java:145-162`):
```java
@Override
public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
    messageBuffer.append(data);

    if (last) {  // 메시지가 완성되면
        String message = messageBuffer.toString();
        messageBuffer.setLength(0);  // 버퍼 초기화

        try {
            messageHandler.accept(message);  // 핸들러에게 전달
        } catch (Exception e) {
            log.error("Error processing {} message: {}", streamName, e.getMessage());
        }
    }

    webSocket.request(1);  // 다음 메시지 요청 (backpressure)
    return null;
}
```

### 7.4. 자동 재연결

```java
@Override
public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
    log.warn("{} WebSocket closed: {} - {}", streamName, statusCode, reason);
    connected = false;

    if (!closed) {
        scheduleReconnect();  // 의도적 종료가 아니면 재연결
    }
    return null;
}

private void scheduleReconnect() {
    if (closed) return;

    long delay = backoff.nextDelayMs();
    log.info("Scheduling reconnect for {} in {}ms (attempt {})",
        streamName, delay, backoff.getAttempt());

    scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
}
```

---

## 8. 동시성 처리

### 8.1. AtomicLong vs synchronized

**AtomicLong 사용** (`infrastructure/fallback/InMemoryPrayerCountAdapter.java`):
```java
private final AtomicLong upCount = new AtomicLong(0);

// synchronized 없이 스레드 안전한 증가
public long increment(Side side, long delta) {
    return switch (side) {
        case UP -> upCount.addAndGet(delta);      // CAS 기반 원자적 연산
        case DOWN -> downCount.addAndGet(delta);
    };
}
```

**왜 AtomicLong인가요?**
- `synchronized`보다 성능이 좋음 (lock-free)
- CAS(Compare-And-Swap) 기반 동작
- 단순 증가 연산에 적합

### 8.2. ConcurrentLinkedQueue

**RPM 계산기의 이벤트 저장** (`application/service/PrayerService.java:60-61`):
```java
private final ConcurrentLinkedQueue<TimestampedEvent> upEvents = new ConcurrentLinkedQueue<>();
private final ConcurrentLinkedQueue<TimestampedEvent> downEvents = new ConcurrentLinkedQueue<>();
```

**왜 ConcurrentLinkedQueue인가요?**
- 멀티스레드에서 안전한 추가/제거
- 순서 보장 (FIFO)
- 슬라이딩 윈도우 구현에 적합

### 8.3. volatile 키워드

```java
private volatile long lastRefillTime = System.currentTimeMillis();
private volatile boolean connected = false;
```

**volatile의 역할**:
- 모든 스레드에서 최신 값을 읽도록 보장
- 단순 읽기/쓰기에 사용 (복합 연산에는 AtomicXXX 사용)

### 8.4. AtomicBoolean으로 상태 관리

```java
private final AtomicBoolean usingFallback = new AtomicBoolean(false);

// 폴백 전환
usingFallback.set(true);

// 상태 확인
if (usingFallback.get()) { ... }
```

---

## 9. 테스트 작성 전략

### 9.1. Given-When-Then 패턴

```java
@Test
@DisplayName("UP_카운트를_증가시킨다")
void UP_카운트를_증가시킨다() {
    // Given (준비)
    PrayerCount count = PrayerCount.zero();

    // When (실행)
    PrayerCount result = count.increment(Side.UP, 5);

    // Then (검증)
    assertThat(result.upCount()).isEqualTo(5);
    assertThat(result.downCount()).isZero();  // 다른 값은 변경되지 않음
}
```

### 9.2. Mock을 사용한 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
class PrayerServiceTest {

    @Mock
    private FallbackManager fallbackManager;  // 가짜 객체

    private PrayerService prayerService;

    @BeforeEach
    void setUp() {
        prayerService = new PrayerService(fallbackManager);
    }

    @Test
    @DisplayName("pray()는_카운트를_증가시키고_Prayer를_반환한다")
    void pray_증가_반환() {
        // Given
        when(fallbackManager.increment(eq(Side.UP), eq(1L))).thenReturn(10L);

        // When
        Prayer result = prayerService.pray(Side.UP, "session-123");

        // Then
        assertThat(result.side()).isEqualTo(Side.UP);
        verify(fallbackManager).increment(Side.UP, 1L);  // 호출 검증
    }
}
```

### 9.3. 통합 테스트 (Testcontainers)

```java
@SpringBootTest
@Testcontainers
class RedisIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private RedisPrayerCountAdapter adapter;

    @Test
    @DisplayName("Redis에서_카운트_증가_및_조회_테스트")
    void incrementAndGet() {
        long upResult = adapter.increment(Side.UP, 10);
        assertThat(upResult).isGreaterThanOrEqualTo(10);
    }
}
```

### 9.4. 테스트 네이밍 규칙

```java
// 한국어로 행동을 설명
@DisplayName("카운트가_0일_때_50%_비율을_반환한다")
void 카운트가_0일_때_50퍼센트_비율을_반환한다() { ... }

// 메서드명도 한국어로 작성 가능
@Test
void 버스트_제한_내에서는_요청을_허용한다() { ... }
```

---

## 10. 실무에서 자주 실수하는 부분

### 10.1. null 처리 누락

**잘못된 코드**:
```java
List<String> values = redisTemplate.opsForValue().multiGet(keys);
long upCount = Long.parseLong(values.get(0));  // NullPointerException 가능!
```

**올바른 코드** (`RedisPrayerCountAdapter.java:52-58`):
```java
List<String> values = redisTemplate.opsForValue().multiGet(keys);

if (values == null) {
    return PrayerCount.zero();  // null 체크
}

long upCount = parseCount(values.get(0));  // 안전한 파싱

private long parseCount(String value) {
    if (value == null || value.isEmpty()) {
        return 0L;
    }
    try {
        return Long.parseLong(value);
    } catch (NumberFormatException e) {
        return 0L;
    }
}
```

### 10.2. 리소스 정리 누락

**잘못된 코드**:
```java
public void connect() {
    // 연결만 하고 종료 처리 없음
    httpClient.newWebSocketBuilder().buildAsync(uri, listener);
}
```

**올바른 코드** (`BinanceWebSocketClient.java:36-39`):
```java
@PreDestroy  // 애플리케이션 종료 시 자동 호출
public void destroy() {
    connections.values().forEach(WebSocketConnection::close);
    scheduler.shutdown();
    log.info("BinanceWebSocketClient destroyed");
}
```

### 10.3. 동시성 문제

**잘못된 코드**:
```java
// 두 연산이 원자적이지 않음
if (count > 0) {
    count--;  // 다른 스레드가 중간에 값을 변경할 수 있음
}
```

**올바른 코드**:
```java
synchronized boolean tryConsume() {
    refill();
    if (tokens.get() > 0) {
        tokens.decrementAndGet();
        return true;
    }
    return false;
}
```

### 10.4. 예외 처리 누락

**잘못된 코드**:
```java
public long increment(Side side, long delta) {
    return redisTemplate.opsForValue().increment(key, delta);  // 예외 발생 시 서비스 중단
}
```

**올바른 코드** (`FallbackManager.java:36-42`):
```java
try {
    return redisAdapter.increment(side, delta);
} catch (Exception e) {
    log.warn("Redis increment failed, switching to fallback: {}", e.getMessage());
    usingFallback.set(true);
    return inMemoryAdapter.increment(side, delta);  // 폴백으로 처리
}
```

### 10.5. 로깅 레벨 오용

```java
// ❌ 잘못된 예시
log.info("Processing message...");  // 매 메시지마다 로그 → 성능 저하
log.error("User clicked button");   // 정상 동작인데 error 레벨

// ✅ 올바른 예시
log.debug("Processing message: {}", message);  // 디버그 레벨
log.warn("Redis connection failed, using fallback");  // 경고 상황
log.error("Failed to parse message: {}", e.getMessage());  // 실제 에러
```

---

## 핵심 개념 요약

| 개념 | 설명 | 프로젝트 적용 |
|------|------|-------------|
| 헥사고날 아키텍처 | 비즈니스 로직과 외부 시스템 분리 | Port & Adapter 패턴 |
| 불변 객체 | 상태 변경 대신 새 객체 반환 | Java Record 사용 |
| 원자적 연산 | 중간 상태 없이 완료되는 연산 | Redis INCRBY, AtomicLong |
| 폴백 패턴 | 장애 시 대체 시스템으로 전환 | FallbackManager |
| Rate Limiting | 요청 속도 제한 | 토큰 버킷 알고리즘 |
| Exponential Backoff | 재시도 간격을 점차 증가 | 바이낸스 재연결 |

---

## 추천 학습 순서

1. **도메인 모델 읽기**: `domain/model/` 패키지의 모든 파일
2. **Port 인터페이스 이해**: `application/port/` 패키지
3. **Service 로직 분석**: `PrayerService.java`
4. **Adapter 구현 확인**: `adapter/out/redis/`
5. **폴백 로직 학습**: `infrastructure/fallback/`
6. **테스트 코드 분석**: 각 클래스의 테스트 파일

각 단계에서 실제 코드를 직접 디버깅하며 데이터 흐름을 추적해보시기 바랍니다.