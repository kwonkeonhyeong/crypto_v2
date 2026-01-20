# Phase 2a 백엔드 기반 구현 보고서

> 작성일: 2026-01-20
> 작성자: Claude (AI Assistant)
> 대상: 주니어 개발자를 위한 코드 이해 가이드

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [도메인 모델](#2-도메인-모델-domain-layer)
3. [Application Layer](#3-application-layer-유스케이스)
4. [Adapter Layer](#4-adapter-layer-외부-시스템-연동)
5. [Infrastructure Layer](#5-infrastructure-layer-폴백-시스템)
6. [패키지 구조 요약](#6-패키지-구조-요약)
7. [테스트 전략](#7-테스트-전략)
8. [리뷰 필수 코드](#8-리뷰-필수-코드)
9. [핵심 설계 원칙](#9-핵심-설계-원칙)
10. [다음 단계](#10-다음-단계-phase-2b)

---

## 1. 아키텍처 개요

### 1.1. 헥사고날 아키텍처 (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────────────┐
│                        외부 세계                                  │
│  (WebSocket, REST API, 스케줄러 등)                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Adapter (In)                                  │
│              - WebSocket Handler (Phase 2b)                      │
│              - REST Controller (필요시)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ Port (In) - 인터페이스
┌─────────────────────────────────────────────────────────────────┐
│                    Application Layer                             │
│              - PrayerService (유스케이스 구현)                    │
│              - 비즈니스 로직 조율                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ Port (Out) - 인터페이스
┌─────────────────────────────────────────────────────────────────┐
│                    Adapter (Out)                                 │
│              - RedisPrayerCountAdapter                           │
│              - InMemoryPrayerCountAdapter                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        외부 시스템                                │
│              (Redis, 외부 API 등)                                │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2. 왜 헥사고날 아키텍처를 사용하나요?

| 이점 | 설명 |
|------|------|
| **테스트 용이성** | Redis 없이도 비즈니스 로직 테스트 가능 |
| **유연성** | Redis → MySQL로 교체해도 비즈니스 로직 변경 없음 |
| **의존성 방향** | 항상 안쪽(도메인)을 향함 → 핵심 로직 보호 |

---

## 2. 도메인 모델 (Domain Layer)

도메인 모델은 **비즈니스의 핵심 개념**을 코드로 표현한 것입니다.

### 2.1. Side.java - 기도 방향

**파일 위치**: `domain/model/Side.java`

```java
public enum Side {
    UP("up", "상승"),
    DOWN("down", "하락");

    private final String key;
    private final String displayName;

    Side(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Side fromKey(String key) {
        for (Side side : values()) {
            if (side.key.equalsIgnoreCase(key)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Unknown side: " + key);
    }
}
```

**역할**: 사용자가 "상승"을 기도하는지 "하락"을 기도하는지 구분

**왜 enum인가요?**
- 값이 두 개로 고정됨 (UP/DOWN)
- 타입 안전성: `String side = "UP"` 대신 `Side.UP` 사용 → 오타 방지
- IDE 자동완성 지원

**주요 메서드 사용 예시**:
```java
Side.fromKey("up")  // → Side.UP (문자열을 enum으로 변환)
Side.UP.getKey()    // → "up" (Redis 키 생성에 사용)
```

---

### 2.2. Prayer.java - 기도 도메인 모델

**파일 위치**: `domain/model/Prayer.java`

```java
public record Prayer(
    String id,           // 고유 식별자 (UUID)
    Side side,           // UP 또는 DOWN
    String sessionId,    // 사용자 세션
    Instant timestamp    // 기도 시간
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
```

**역할**: 한 번의 "기도" 행위를 표현

**왜 record인가요?** (Java 16+)
- 불변(immutable) 객체 자동 생성
- `equals()`, `hashCode()`, `toString()` 자동 생성
- 보일러플레이트 코드 제거

**팩토리 메서드 패턴**:
```java
// ❌ 직접 생성자 호출 (id, timestamp를 매번 지정해야 함)
new Prayer(UUID.randomUUID().toString(), Side.UP, "session", Instant.now());

// ✅ 팩토리 메서드 (필요한 것만 전달)
Prayer.create(Side.UP, "session");
```

---

### 2.3. PrayerCount.java - 카운트 Value Object

**파일 위치**: `domain/model/PrayerCount.java`

```java
public record PrayerCount(
    long upCount,
    long downCount
) {
    public static PrayerCount zero() {
        return new PrayerCount(0L, 0L);
    }

    public PrayerCount incrementUp(long delta) {
        return new PrayerCount(upCount + delta, downCount);  // 새 객체 반환
    }

    public PrayerCount incrementDown(long delta) {
        return new PrayerCount(upCount, downCount + delta);
    }

    public PrayerCount increment(Side side, long delta) {
        return switch (side) {
            case UP -> incrementUp(delta);
            case DOWN -> incrementDown(delta);
        };
    }

    public long total() {
        return upCount + downCount;
    }

    public double upRatio() {
        long total = total();
        return total == 0 ? 0.5 : (double) upCount / total;
    }

    public double downRatio() {
        return 1.0 - upRatio();
    }

    public PrayerCount merge(PrayerCount other) {
        return new PrayerCount(
            this.upCount + other.upCount,
            this.downCount + other.downCount
        );
    }
}
```

**역할**: UP/DOWN 기도 횟수를 담는 값 객체

**Value Object란?**
- 값으로 동등성을 판단 (ID가 아님)
- `new PrayerCount(100, 50).equals(new PrayerCount(100, 50))` → true
- 불변 객체로 만들어 부작용 방지

**불변성이 중요한 이유**:
```java
// ❌ 가변 객체 (위험)
count.setUpCount(count.getUpCount() + 1);  // 원본이 변경됨

// ✅ 불변 객체 (안전)
PrayerCount newCount = count.incrementUp(1);  // 원본 유지, 새 객체 반환
```

**비율 계산 로직**:
```java
public double upRatio() {
    long total = total();
    return total == 0 ? 0.5 : (double) upCount / total;  // 0으로 나누기 방지
}
```
- total이 0이면 50:50 (0.5) 반환
- 게이지 바에서 사용 (UP 70%, DOWN 30% 표시)

---

### 2.4. PrayerStats.java - 통계 Value Object

**파일 위치**: `domain/model/PrayerStats.java`

```java
public record PrayerStats(
    PrayerCount count,   // 총 카운트
    double upRpm,        // 분당 UP 기도 수
    double downRpm,      // 분당 DOWN 기도 수
    long timestamp       // 통계 생성 시간
) {
    public static PrayerStats create(PrayerCount count, double upRpm, double downRpm) {
        return new PrayerStats(count, upRpm, downRpm, System.currentTimeMillis());
    }

    public double totalRpm() {
        return upRpm + downRpm;
    }
}
```

**역할**: 클라이언트에게 브로드캐스트할 실시간 통계

**RPM (Requests Per Minute)**:
- 최근 60초간의 기도 횟수
- 실시간 활동량을 보여주는 지표
- "지금 이 순간 얼마나 많은 사람이 기도하는가?"

---

## 3. Application Layer (유스케이스)

### 3.1. Port (In) - Driving Ports

외부에서 애플리케이션을 **호출**하는 인터페이스

#### PrayerUseCase.java

**파일 위치**: `application/port/in/PrayerUseCase.java`

```java
public interface PrayerUseCase {
    /**
     * 기도를 등록하고 생성된 Prayer를 반환
     */
    Prayer pray(Side side, String sessionId);

    /**
     * 배치로 기도 등록 (클라이언트 배칭 지원)
     */
    void prayBatch(Side side, String sessionId, int count);
}
```

**왜 인터페이스인가요?**
- WebSocket Handler가 구체 클래스(PrayerService)를 직접 의존하지 않음
- 테스트 시 Mock 객체로 대체 가능

#### PrayerQuery.java

**파일 위치**: `application/port/in/PrayerQuery.java`

```java
public interface PrayerQuery {
    /**
     * 오늘의 기도 카운트 조회
     */
    PrayerCount getTodayCount();

    /**
     * 현재 통계 조회 (RPM 포함)
     */
    PrayerStats getCurrentStats();
}
```

**Command-Query 분리 (CQS)**:
- `PrayerUseCase`: 상태를 **변경**하는 명령 (Command)
- `PrayerQuery`: 상태를 **조회**만 하는 쿼리 (Query)
- 분리하면 코드 이해가 쉬워짐

---

### 3.2. Port (Out) - Driven Ports

애플리케이션이 외부 시스템을 **호출**하는 인터페이스

#### PrayerCountPort.java

**파일 위치**: `application/port/out/PrayerCountPort.java`

```java
public interface PrayerCountPort {
    /**
     * 기도 카운트 증가
     * @return 증가 후 총 카운트
     */
    long increment(Side side, long delta);

    /**
     * 현재 카운트 조회
     */
    PrayerCount getCount();

    /**
     * 카운트 Merge (폴백 복구용)
     */
    void merge(PrayerCount delta);

    /**
     * 연결 상태 확인
     */
    boolean isAvailable();
}
```

**왜 Port가 필요한가요?**

```
PrayerService → PrayerCountPort(인터페이스) ← RedisPrayerCountAdapter
                                            ← InMemoryPrayerCountAdapter
```

- PrayerService는 "카운트를 저장한다"는 것만 알면 됨
- Redis인지 메모리인지 **몰라도** 됨
- 저장소 교체 시 PrayerService 코드 변경 없음

---

### 3.3. PrayerService.java - 유스케이스 구현

**파일 위치**: `application/service/PrayerService.java`

```java
@Service
public class PrayerService implements PrayerUseCase, PrayerQuery {

    private final FallbackManager countPort;
    private final RpmCalculator rpmCalculator;

    public PrayerService(FallbackManager countPort) {
        this.countPort = countPort;
        this.rpmCalculator = new RpmCalculator();
    }

    @Override
    public Prayer pray(Side side, String sessionId) {
        Prayer prayer = Prayer.create(side, sessionId);  // 1. 도메인 객체 생성
        countPort.increment(side, 1);                    // 2. 저장소에 카운트 증가
        rpmCalculator.record(side);                      // 3. RPM 계산기에 기록
        return prayer;
    }

    @Override
    public void prayBatch(Side side, String sessionId, int count) {
        countPort.increment(side, count);
        for (int i = 0; i < count; i++) {
            rpmCalculator.record(side);
        }
    }

    @Override
    public PrayerCount getTodayCount() {
        return countPort.getCount();
    }

    @Override
    public PrayerStats getCurrentStats() {
        PrayerCount count = countPort.getCount();        // 저장소에서 조회
        double upRpm = rpmCalculator.getRpm(Side.UP);    // RPM 계산
        double downRpm = rpmCalculator.getRpm(Side.DOWN);
        return PrayerStats.create(count, upRpm, downRpm);
    }

    /**
     * 최근 60초 기준 RPM 계산기
     */
    private static class RpmCalculator {
        private static final long WINDOW_MS = 60_000L;

        private final ConcurrentLinkedQueue<TimestampedEvent> upEvents = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<TimestampedEvent> downEvents = new ConcurrentLinkedQueue<>();

        void record(Side side) {
            long now = System.currentTimeMillis();
            TimestampedEvent event = new TimestampedEvent(now);

            switch (side) {
                case UP -> upEvents.add(event);
                case DOWN -> downEvents.add(event);
            }

            cleanOldEvents(now);
        }

        double getRpm(Side side) {
            long now = System.currentTimeMillis();
            cleanOldEvents(now);

            ConcurrentLinkedQueue<TimestampedEvent> events = switch (side) {
                case UP -> upEvents;
                case DOWN -> downEvents;
            };

            return events.size();
        }

        private void cleanOldEvents(long now) {
            long cutoff = now - WINDOW_MS;
            upEvents.removeIf(e -> e.timestamp < cutoff);
            downEvents.removeIf(e -> e.timestamp < cutoff);
        }

        private record TimestampedEvent(long timestamp) {}
    }
}
```

**역할**: 비즈니스 로직을 **조율(orchestrate)**

**슬라이딩 윈도우 알고리즘** (RpmCalculator):
```
시간축: ─────────────────────────────────────────►
              │◄───── 60초 윈도우 ─────►│
이벤트:    ×  ×     ×  ×  ×  ×  ×      │  현재
              │  이 구간의 이벤트만 카운트  │
```

---

## 4. Adapter Layer (외부 시스템 연동)

### 4.1. RedisKeyGenerator.java - Redis 키 생성

**파일 위치**: `adapter/out/redis/RedisKeyGenerator.java`

```java
@Component
public class RedisKeyGenerator {

    private static final String PREFIX = "prayer";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long TTL_HOURS = 48;

    public String generateKey(Side side) {
        return generateKey(LocalDate.now(), side);
    }

    public String generateKey(LocalDate date, Side side) {
        String dateStr = date.format(DATE_FORMAT);
        return String.format("%s:%s:%s", PREFIX, dateStr, side.getKey());
        // 결과: "prayer:20240120:up"
    }

    public String getUpKey() {
        return generateKey(Side.UP);
    }

    public String getDownKey() {
        return generateKey(Side.DOWN);
    }

    public long getTtlSeconds() {
        return TTL_HOURS * 60 * 60;
    }
}
```

**Redis 키 전략**:
```
prayer:20240120:up    ← 2024년 1월 20일 UP 카운트
prayer:20240120:down  ← 2024년 1월 20일 DOWN 카운트
```

**왜 날짜별로 키를 분리하나요?**
- 매일 자정(UTC 00:00)에 카운트 리셋
- 어제 데이터 조회 가능 (통계용)
- TTL 48시간으로 자동 삭제

---

### 4.2. RedisConfig.java - Spring Redis 설정

**파일 위치**: `adapter/out/redis/RedisConfig.java`

```java
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

**역할**: Spring에서 Redis를 사용하기 위한 설정

---

### 4.3. RedisPrayerCountAdapter.java - Redis 구현체

**파일 위치**: `adapter/out/redis/RedisPrayerCountAdapter.java`

```java
@Component
public class RedisPrayerCountAdapter implements PrayerCountPort {

    private static final Logger log = LoggerFactory.getLogger(RedisPrayerCountAdapter.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    public RedisPrayerCountAdapter(
            StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    @Override
    public long increment(Side side, long delta) {
        String key = keyGenerator.generateKey(side);  // "prayer:20240120:up"
        Long result = redisTemplate.opsForValue().increment(key, delta);

        // TTL 설정 (최초 생성 시에만)
        if (result != null && result == delta) {
            redisTemplate.expire(key, Duration.ofSeconds(keyGenerator.getTtlSeconds()));
        }

        return result != null ? result : 0L;
    }

    @Override
    public PrayerCount getCount() {
        List<String> keys = List.of(
            keyGenerator.getUpKey(),    // "prayer:20240120:up"
            keyGenerator.getDownKey()   // "prayer:20240120:down"
        );

        List<String> values = redisTemplate.opsForValue().multiGet(keys);  // MGET 명령

        if (values == null) {
            return PrayerCount.zero();
        }

        long upCount = parseCount(values.get(0));
        long downCount = parseCount(values.get(1));

        return new PrayerCount(upCount, downCount);
    }

    @Override
    public void merge(PrayerCount delta) {
        if (delta.upCount() > 0) {
            increment(Side.UP, delta.upCount());
        }
        if (delta.downCount() > 0) {
            increment(Side.DOWN, delta.downCount());
        }
        log.info("Merged fallback count: up={}, down={}", delta.upCount(), delta.downCount());
    }

    @Override
    public boolean isAvailable() {
        try {
            String result = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

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
}
```

**Redis 명령어 매핑**:

| 메서드 | Redis 명령 | 설명 |
|--------|-----------|------|
| `increment()` | `INCRBY key delta` | 원자적 증가 |
| `getCount()` | `MGET key1 key2` | 여러 키 한번에 조회 |
| `expire()` | `EXPIRE key seconds` | TTL 설정 |
| `isAvailable()` | `PING` | 연결 상태 확인 |

**원자적(Atomic) 연산이 중요한 이유**:
```
동시에 두 사용자가 클릭:
사용자A: count = 100 → count = 101
사용자B: count = 100 → count = 101  ❌ 101이 되어버림

INCRBY 사용:
사용자A: INCRBY count 1 → 101
사용자B: INCRBY count 1 → 102  ✅ 정확하게 증가
```

---

## 5. Infrastructure Layer (폴백 시스템)

### 5.1. InMemoryPrayerCountAdapter.java - 인메모리 폴백

**파일 위치**: `infrastructure/fallback/InMemoryPrayerCountAdapter.java`

```java
@Component
public class InMemoryPrayerCountAdapter implements PrayerCountPort {

    private final AtomicLong upCount = new AtomicLong(0);
    private final AtomicLong downCount = new AtomicLong(0);

    @Override
    public long increment(Side side, long delta) {
        return switch (side) {
            case UP -> upCount.addAndGet(delta);    // 원자적 증가
            case DOWN -> downCount.addAndGet(delta);
        };
    }

    @Override
    public PrayerCount getCount() {
        return new PrayerCount(upCount.get(), downCount.get());
    }

    @Override
    public void merge(PrayerCount delta) {
        upCount.addAndGet(delta.upCount());
        downCount.addAndGet(delta.downCount());
    }

    @Override
    public boolean isAvailable() {
        return true;  // 인메모리는 항상 사용 가능
    }

    public PrayerCount getAndReset() {
        long up = upCount.getAndSet(0);    // 값 반환 후 0으로 리셋
        long down = downCount.getAndSet(0);
        return new PrayerCount(up, down);
    }

    public boolean hasData() {
        return upCount.get() > 0 || downCount.get() > 0;
    }
}
```

**AtomicLong 사용 이유**:
- 멀티스레드 환경에서 안전한 증가 연산
- `synchronized` 없이 성능 유지

---

### 5.2. FallbackManager.java - 폴백 관리자

**파일 위치**: `infrastructure/fallback/FallbackManager.java`

```java
@Component
public class FallbackManager implements PrayerCountPort {

    private static final Logger log = LoggerFactory.getLogger(FallbackManager.class);

    private final RedisPrayerCountAdapter redisAdapter;
    private final InMemoryPrayerCountAdapter inMemoryAdapter;
    private final AtomicBoolean usingFallback = new AtomicBoolean(false);

    public FallbackManager(
            RedisPrayerCountAdapter redisAdapter,
            InMemoryPrayerCountAdapter inMemoryAdapter) {
        this.redisAdapter = redisAdapter;
        this.inMemoryAdapter = inMemoryAdapter;
    }

    @Override
    public long increment(Side side, long delta) {
        if (usingFallback.get()) {
            return inMemoryAdapter.increment(side, delta);  // 폴백 모드
        }

        try {
            return redisAdapter.increment(side, delta);     // 정상 모드
        } catch (Exception e) {
            log.warn("Redis increment failed, switching to fallback: {}", e.getMessage());
            usingFallback.set(true);                        // 폴백 전환
            return inMemoryAdapter.increment(side, delta);
        }
    }

    @Override
    public PrayerCount getCount() {
        if (usingFallback.get()) {
            return inMemoryAdapter.getCount();
        }

        try {
            return redisAdapter.getCount();
        } catch (Exception e) {
            log.warn("Redis getCount failed, using fallback: {}", e.getMessage());
            return inMemoryAdapter.getCount();
        }
    }

    @Override
    public void merge(PrayerCount delta) {
        redisAdapter.merge(delta);
    }

    @Override
    public boolean isAvailable() {
        return redisAdapter.isAvailable() || inMemoryAdapter.isAvailable();
    }

    /**
     * 30초마다 Redis 연결 상태 확인 및 복구 시도
     */
    @Scheduled(fixedRate = 30000)
    public void checkAndRecover() {
        if (!usingFallback.get()) {
            return;
        }

        if (redisAdapter.isAvailable()) {
            log.info("Redis connection recovered, merging fallback data...");

            // 누적된 인메모리 데이터를 Redis로 Merge
            if (inMemoryAdapter.hasData()) {
                PrayerCount fallbackData = inMemoryAdapter.getAndReset();
                redisAdapter.merge(fallbackData);
                log.info("Merged {} up and {} down prayers to Redis",
                    fallbackData.upCount(), fallbackData.downCount());
            }

            usingFallback.set(false);
            log.info("Switched back to Redis");
        }
    }

    public boolean isUsingFallback() {
        return usingFallback.get();
    }
}
```

**폴백 흐름 다이어그램**:

```
정상 상태:
┌──────────┐      ┌─────────────────┐      ┌───────┐
│ 클라이언트 │ ──► │ FallbackManager │ ──► │ Redis │ ✓
└──────────┘      └─────────────────┘      └───────┘

Redis 장애 발생:
┌──────────┐      ┌─────────────────┐      ┌───────┐
│ 클라이언트 │ ──► │ FallbackManager │ ──► │ Redis │ ✗
└──────────┘      └─────────────────┘      └───────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │    InMemory     │ (임시 저장)
                  └─────────────────┘

Redis 복구:
                  ┌─────────────────┐
                  │    InMemory     │ ──┐
                  └─────────────────┘   │ 데이터 병합
                                        ▼
┌──────────┐      ┌─────────────────┐      ┌───────┐
│ 클라이언트 │ ──► │ FallbackManager │ ──► │ Redis │ ✓
└──────────┘      └─────────────────┘      └───────┘
```

**장애 대응 전략**:
1. Redis 실패 시 **즉시** 인메모리로 전환 (서비스 무중단)
2. 30초마다 Redis 상태 확인 (`@Scheduled`)
3. 복구 시 누적된 데이터를 Redis로 병합

---

## 6. 패키지 구조 요약

```
backend/src/main/java/com/crypto/prayer/
│
├── domain/                          ◄── 비즈니스 핵심 (외부 의존성 없음)
│   └── model/
│       ├── Side.java                    - 기도 방향 enum
│       ├── Prayer.java                  - 기도 도메인 모델
│       ├── PrayerCount.java             - 카운트 Value Object
│       └── PrayerStats.java             - 통계 Value Object
│
├── application/                     ◄── 유스케이스 (비즈니스 로직 조율)
│   ├── port/
│   │   ├── in/                          - Driving Ports (외부→내부)
│   │   │   ├── PrayerUseCase.java           - 기도 등록 인터페이스
│   │   │   └── PrayerQuery.java             - 조회 인터페이스
│   │   └── out/                         - Driven Ports (내부→외부)
│   │       └── PrayerCountPort.java         - 저장소 인터페이스
│   └── service/
│       └── PrayerService.java           - 유스케이스 구현체
│
├── adapter/                         ◄── 외부 시스템 연동
│   └── out/
│       └── redis/
│           ├── RedisConfig.java             - Spring Redis 설정
│           ├── RedisKeyGenerator.java       - Redis 키 생성
│           └── RedisPrayerCountAdapter.java - Redis 구현체
│
└── infrastructure/                  ◄── 인프라 (폴백, 설정 등)
    └── fallback/
        ├── InMemoryPrayerCountAdapter.java  - 인메모리 폴백
        └── FallbackManager.java             - 폴백 관리자
```

---

## 7. 테스트 전략

### 7.1. 단위 테스트 작성 규칙

```java
@Test
@DisplayName("UP_증가시_upCount가_증가한다")  // 한글로 행위 설명
void UP_증가시_upCount가_증가한다() {
    // Given (준비)
    PrayerCount count = new PrayerCount(10L, 5L);

    // When (실행)
    PrayerCount result = count.incrementUp(3L);

    // Then (검증)
    assertEquals(13L, result.upCount());
    assertEquals(5L, result.downCount());  // 다른 값은 변하지 않음
}
```

### 7.2. Mock을 사용한 테스트

```java
@ExtendWith(MockitoExtension.class)
class PrayerServiceTest {

    @Mock
    private FallbackManager countPort;  // 가짜 객체

    private PrayerService prayerService;

    @BeforeEach
    void setUp() {
        prayerService = new PrayerService(countPort);
    }

    @Test
    void 기도를_등록하고_Prayer를_반환한다() {
        // Given
        when(countPort.increment(Side.UP, 1L)).thenReturn(1L);  // 동작 정의

        // When
        Prayer result = prayerService.pray(Side.UP, "session-123");

        // Then
        assertNotNull(result);
        verify(countPort).increment(Side.UP, 1L);  // 호출 검증
    }
}
```

**Mock의 장점**:
- Redis 없이 PrayerService 테스트 가능
- 테스트 속도 향상
- 외부 시스템 장애와 무관

### 7.3. 테스트 현황

| 테스트 클래스 | 테스트 수 | 설명 |
|--------------|----------|------|
| `SideTest` | 7개 | enum 동작 검증 |
| `PrayerTest` | 6개 | 도메인 모델 생성 검증 |
| `PrayerCountTest` | 14개 | Value Object 동작 검증 |
| `PrayerStatsTest` | 7개 | 통계 VO 동작 검증 |
| `PrayerUseCaseTest` | 2개 | Port 인터페이스 검증 |
| `PrayerQueryTest` | 2개 | Port 인터페이스 검증 |
| `PrayerCountPortTest` | 4개 | Port 인터페이스 검증 |
| `RedisKeyGeneratorTest` | 6개 | 키 생성 로직 검증 |
| `RedisPrayerCountAdapterTest` | 7개 | Redis 연동 검증 (Mock) |
| `InMemoryPrayerCountAdapterTest` | 8개 | 인메모리 동작 검증 |
| `FallbackManagerTest` | 5개 | 폴백 로직 검증 |
| `PrayerServiceTest` | 5개 | 유스케이스 검증 |
| **총계** | **73개** | **모두 통과** |

---

## 8. 리뷰 필수 코드

> ⚠️ 아래 코드는 **보안, 성능, 정합성** 측면에서 반드시 검토가 필요합니다.

### 8.1. 동시성 처리 (성능)

| 파일 | 라인 | 검토 포인트 |
|------|------|-------------|
| `FallbackManager.java` | :21 | `AtomicBoolean` 폴백 상태 - 경합 조건 확인 |
| `FallbackManager.java` | :31-43 | `increment()` 폴백 전환 - 예외 발생 시 상태 전이 검증 |
| `InMemoryPrayerCountAdapter.java` | :13-14 | `AtomicLong` 카운터 - 오버플로우 가능성 검토 |
| `InMemoryPrayerCountAdapter.java` | :40-44 | `getAndReset()` - 원자적 읽기+초기화 정합성 |
| `PrayerService.java` | :60-61 | `ConcurrentLinkedQueue` - 메모리 누수 가능성 |
| `PrayerService.java` | :87-91 | `cleanOldEvents()` - 동시 수정 시 안전성 |

### 8.2. 외부 시스템 호출 (성능)

| 파일 | 라인 | 검토 포인트 |
|------|------|-------------|
| `RedisPrayerCountAdapter.java` | :30-40 | `increment()` - TTL 설정 조건 (`result == delta`) 정확성 |
| `RedisPrayerCountAdapter.java` | :43-59 | `getCount()` - MGET 결과 null 처리 |
| `RedisPrayerCountAdapter.java` | :73-83 | `isAvailable()` - PING 실패 시 예외 처리 |

### 8.3. 에러 복구 로직 (정합성)

| 파일 | 라인 | 검토 포인트 |
|------|------|-------------|
| `FallbackManager.java` | :72-91 | `checkAndRecover()` - 복구 중 새 요청 처리 동작 |
| `FallbackManager.java` | :81-86 | 인메모리→Redis 병합 - 데이터 유실 가능성 |
| `RedisPrayerCountAdapter.java` | :62-70 | `merge()` - 부분 실패 시 일관성 |

### 8.4. 알려진 제한사항

1. **RPM 계산기 메모리**: `RpmCalculator`의 이벤트 큐가 서버 재시작 시 초기화됨
2. **폴백 복구 지연**: Redis 복구 후 최대 30초까지 인메모리 모드 유지 가능
3. **TTL 설정 타이밍**: 동시 요청 시 여러 번 `expire()` 호출될 수 있음 (성능 영향 미미)

---

## 9. 핵심 설계 원칙

| 원칙 | 적용 예시 |
|------|----------|
| **단일 책임 원칙 (SRP)** | PrayerService는 조율만, 저장은 Adapter가 담당 |
| **의존성 역전 원칙 (DIP)** | Service → Port(인터페이스) ← Adapter |
| **불변 객체** | PrayerCount의 모든 메서드가 새 객체 반환 |
| **팩토리 메서드 패턴** | `Prayer.create()`, `PrayerCount.zero()` |
| **폴백 패턴** | Redis 장애 시 인메모리로 자동 전환 |
| **Command-Query 분리** | PrayerUseCase(변경) vs PrayerQuery(조회) |

---

## 10. 다음 단계 (Phase 2b)

Phase 2b에서는 **WebSocket & STOMP**를 구현합니다:

| 항목 | 설명 |
|------|------|
| WebSocket Handler | 클라이언트 연결 관리 (Adapter In) |
| Rate Limiter | 토큰 버킷 알고리즘으로 요청 제한 |
| 브로드캐스터 | 200ms마다 전체 클라이언트에 통계 전송 |
| STOMP 설정 | 메시지 브로커 설정 |

이를 통해 클라이언트와 **실시간 양방향 통신**이 가능해집니다.

---

## 부록: 용어 정리

| 용어 | 설명 |
|------|------|
| **헥사고날 아키텍처** | 비즈니스 로직을 외부 시스템으로부터 분리하는 아키텍처 패턴 |
| **Port** | 내부와 외부를 연결하는 인터페이스 |
| **Adapter** | Port 인터페이스의 구체적인 구현체 |
| **Value Object** | 값으로 동등성을 판단하는 불변 객체 |
| **RPM** | Requests Per Minute (분당 요청 수) |
| **TTL** | Time To Live (데이터 유효 기간) |
| **원자적 연산** | 중간 상태 없이 완전히 실행되거나 전혀 실행되지 않는 연산 |
| **폴백** | 주 시스템 장애 시 대체 시스템으로 전환 |
