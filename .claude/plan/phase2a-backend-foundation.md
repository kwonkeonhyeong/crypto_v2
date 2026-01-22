# Phase 2a: Backend 기반 (Redis, 도메인)

## 목표
헥사고날 아키텍처 기반으로 도메인 모델과 Redis 연동을 구현한다.

## 선행 의존성
- Phase 1: 프로젝트 셋업 완료

## 범위
- 헥사고날 아키텍처 패키지 구조 구현
- 도메인 모델 정의 (Prayer, Side, PrayerCount)
- Redis 연결 및 카운터 관리
- 인메모리 폴백 + 복구 시 Merge 전략

---

## 디렉토리 구조

```
backend/src/main/java/com/crypto/prayer/
├── PrayerApplication.java
├── domain/
│   └── model/
│       ├── Side.java                    # UP/DOWN enum
│       ├── Prayer.java                  # 기도 도메인 모델
│       ├── PrayerCount.java             # 카운트 Value Object
│       └── PrayerStats.java             # 통계 VO (RPM 포함)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── PrayerUseCase.java       # 기도 유스케이스 인터페이스
│   │   │   └── PrayerQuery.java         # 조회 유스케이스
│   │   └── out/
│   │       ├── PrayerCountPort.java     # 카운트 저장소 포트
│   │       └── PrayerEventPort.java     # 이벤트 발행 포트
│   └── service/
│       └── PrayerService.java           # 유스케이스 구현
├── adapter/
│   ├── in/
│   │   └── websocket/                   # Phase 2b에서 구현
│   └── out/
│       └── redis/
│           ├── RedisPrayerCountAdapter.java
│           ├── RedisConfig.java
│           └── RedisKeyGenerator.java
└── infrastructure/
    ├── config/
    │   └── AppConfig.java
    └── fallback/
        ├── InMemoryPrayerCountAdapter.java
        └── FallbackManager.java
```

---

## 상세 구현 단계

### 2a.1 도메인 모델

#### domain/model/Side.java
```java
package com.crypto.prayer.domain.model;

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

#### domain/model/Prayer.java
```java
package com.crypto.prayer.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Prayer(
    String id,
    Side side,
    String sessionId,
    Instant timestamp
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

#### domain/model/PrayerCount.java
```java
package com.crypto.prayer.domain.model;

public record PrayerCount(
    long upCount,
    long downCount
) {
    public static PrayerCount zero() {
        return new PrayerCount(0L, 0L);
    }

    public PrayerCount incrementUp(long delta) {
        return new PrayerCount(upCount + delta, downCount);
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

#### domain/model/PrayerStats.java
```java
package com.crypto.prayer.domain.model;

public record PrayerStats(
    PrayerCount count,
    double upRpm,      // 분당 상승 기도 수
    double downRpm,    // 분당 하락 기도 수
    long timestamp
) {
    public static PrayerStats create(PrayerCount count, double upRpm, double downRpm) {
        return new PrayerStats(count, upRpm, downRpm, System.currentTimeMillis());
    }

    public double totalRpm() {
        return upRpm + downRpm;
    }
}
```

### 2a.2 Application Layer (Ports)

#### application/port/in/PrayerUseCase.java
```java
package com.crypto.prayer.application.port.in;

import com.crypto.prayer.domain.model.Prayer;
import com.crypto.prayer.domain.model.Side;

public interface PrayerUseCase {

    /**
     * 기도를 등록하고 증가된 카운트를 반환
     */
    Prayer pray(Side side, String sessionId);

    /**
     * 배치로 기도 등록 (클라이언트 배칭 지원)
     */
    void prayBatch(Side side, String sessionId, int count);
}
```

#### application/port/in/PrayerQuery.java
```java
package com.crypto.prayer.application.port.in;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.PrayerStats;

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

#### application/port/out/PrayerCountPort.java
```java
package com.crypto.prayer.application.port.out;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;

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

### 2a.3 Redis Adapter

#### adapter/out/redis/RedisConfig.java
```java
package com.crypto.prayer.adapter.out.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

#### adapter/out/redis/RedisKeyGenerator.java
```java
package com.crypto.prayer.adapter.out.redis;

import com.crypto.prayer.domain.model.Side;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

#### adapter/out/redis/RedisPrayerCountAdapter.java
```java
package com.crypto.prayer.adapter.out.redis;

import com.crypto.prayer.application.port.out.PrayerCountPort;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
        String key = keyGenerator.generateKey(side);
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
            keyGenerator.getUpKey(),
            keyGenerator.getDownKey()
        );

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

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

### 2a.4 인메모리 폴백

#### infrastructure/fallback/InMemoryPrayerCountAdapter.java
```java
package com.crypto.prayer.infrastructure.fallback;

import com.crypto.prayer.application.port.out.PrayerCountPort;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryPrayerCountAdapter implements PrayerCountPort {

    private final AtomicLong upCount = new AtomicLong(0);
    private final AtomicLong downCount = new AtomicLong(0);

    @Override
    public long increment(Side side, long delta) {
        return switch (side) {
            case UP -> upCount.addAndGet(delta);
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
        return true; // 인메모리는 항상 사용 가능
    }

    public PrayerCount getAndReset() {
        long up = upCount.getAndSet(0);
        long down = downCount.getAndSet(0);
        return new PrayerCount(up, down);
    }

    public boolean hasData() {
        return upCount.get() > 0 || downCount.get() > 0;
    }
}
```

#### infrastructure/fallback/FallbackManager.java
```java
package com.crypto.prayer.infrastructure.fallback;

import com.crypto.prayer.adapter.out.redis.RedisPrayerCountAdapter;
import com.crypto.prayer.application.port.out.PrayerCountPort;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

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
            return inMemoryAdapter.increment(side, delta);
        }

        try {
            return redisAdapter.increment(side, delta);
        } catch (Exception e) {
            log.warn("Redis increment failed, switching to fallback: {}", e.getMessage());
            usingFallback.set(true);
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

### 2a.5 Application Service

#### application/service/PrayerService.java
```java
package com.crypto.prayer.application.service;

import com.crypto.prayer.application.port.in.PrayerQuery;
import com.crypto.prayer.application.port.in.PrayerUseCase;
import com.crypto.prayer.domain.model.Prayer;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.PrayerStats;
import com.crypto.prayer.domain.model.Side;
import com.crypto.prayer.infrastructure.fallback.FallbackManager;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

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
        Prayer prayer = Prayer.create(side, sessionId);
        countPort.increment(side, 1);
        rpmCalculator.record(side);
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
        PrayerCount count = countPort.getCount();
        double upRpm = rpmCalculator.getRpm(Side.UP);
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

            // 오래된 이벤트 정리 (60초 이전)
            cleanOldEvents(now);
        }

        double getRpm(Side side) {
            long now = System.currentTimeMillis();
            cleanOldEvents(now);

            ConcurrentLinkedQueue<TimestampedEvent> events = switch (side) {
                case UP -> upEvents;
                case DOWN -> downEvents;
            };

            return events.size(); // 60초 내 이벤트 수 = RPM
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

### 2a.6 Infrastructure Config

#### infrastructure/config/AppConfig.java
```java
package com.crypto.prayer.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {
    // 공통 설정
}
```

---

## 체크리스트

- [x] 도메인 모델 구현
  - [x] Side enum
  - [x] Prayer record
  - [x] PrayerCount record
  - [x] PrayerStats record
- [x] Application Port 정의
  - [x] PrayerUseCase 인터페이스
  - [x] PrayerQuery 인터페이스
  - [x] PrayerCountPort 인터페이스
- [x] Redis Adapter 구현
  - [x] RedisConfig
  - [x] RedisKeyGenerator
  - [x] RedisPrayerCountAdapter
- [x] 폴백 시스템 구현
  - [x] InMemoryPrayerCountAdapter
  - [x] FallbackManager (Merge 전략 포함)
- [x] PrayerService 구현
  - [x] 기도 등록 로직
  - [x] RPM 계산 로직 (60초 윈도우)
- [x] 단위 테스트 작성
  - [x] PrayerCount 테스트
  - [x] RpmCalculator 테스트
  - [x] FallbackManager 테스트

---

## 검증 명령어

```bash
# Redis 연결 테스트
cd backend && ./gradlew test --tests "*Redis*"

# 도메인 모델 테스트
./gradlew test --tests "*PrayerCount*"

# 폴백 테스트
./gradlew test --tests "*Fallback*"
```

---

## 다음 Phase
→ [Phase 2b: Backend WebSocket](phase2b-backend-websocket.md)
