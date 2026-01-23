# Phase 2a 데이터 흐름 다이어그램 (Mermaid)

> 작성일: 2026-01-20
> 형식: Mermaid (GitHub, Notion 렌더링 지원)

---

## 목차

1. [아키텍처 레이어](#1-아키텍처-레이어)
2. [컴포넌트 관계도](#2-컴포넌트-관계도)
3. [시퀀스 다이어그램](#3-시퀀스-다이어그램)
4. [상태 다이어그램](#4-상태-다이어그램)
5. [코드 위치 참조](#5-코드-위치-참조)

---

## 1. 아키텍처 레이어

### 헥사고날 아키텍처 구조

```mermaid
flowchart TB
    subgraph External["외부 세계"]
        Client["WebSocket Client<br/>(Phase 2b)"]
    end

    subgraph AdapterIn["Adapter (In) Layer"]
        WSHandler["WebSocketHandler<br/>(미구현)"]
    end

    subgraph Ports["Ports Layer"]
        subgraph DrivingPorts["Driving Ports (In)"]
            PrayerUseCase["PrayerUseCase<br/>- pray()<br/>- prayBatch()"]
            PrayerQuery["PrayerQuery<br/>- getTodayCount()<br/>- getCurrentStats()"]
        end
        subgraph DrivenPorts["Driven Ports (Out)"]
            PrayerCountPort["PrayerCountPort<br/>- increment()<br/>- getCount()<br/>- merge()<br/>- isAvailable()"]
        end
    end

    subgraph Application["Application Layer"]
        PrayerService["PrayerService<br/>+ RpmCalculator"]
    end

    subgraph Infrastructure["Infrastructure Layer"]
        FallbackManager["FallbackManager"]
        subgraph Adapters["Adapters"]
            RedisAdapter["RedisPrayerCountAdapter"]
            InMemoryAdapter["InMemoryPrayerCountAdapter"]
        end
    end

    subgraph ExternalSystems["외부 시스템"]
        Redis[("Redis 7.x<br/>prayer:yyyyMMdd:up<br/>prayer:yyyyMMdd:down")]
    end

    Client --> WSHandler
    WSHandler --> PrayerUseCase
    WSHandler --> PrayerQuery
    PrayerUseCase --> PrayerService
    PrayerQuery --> PrayerService
    PrayerService --> PrayerCountPort
    PrayerCountPort --> FallbackManager
    FallbackManager --> RedisAdapter
    FallbackManager --> InMemoryAdapter
    RedisAdapter --> Redis
```

---

## 2. 컴포넌트 관계도

### 클래스 의존성

```mermaid
classDiagram
    direction TB

    class PrayerUseCase {
        <<interface>>
        +pray(Side, String) Prayer
        +prayBatch(Side, String, int) void
    }

    class PrayerQuery {
        <<interface>>
        +getTodayCount() PrayerCount
        +getCurrentStats() PrayerStats
    }

    class PrayerCountPort {
        <<interface>>
        +increment(Side, long) long
        +getCount() PrayerCount
        +merge(PrayerCount) void
        +isAvailable() boolean
    }

    class PrayerService {
        -FallbackManager countPort
        -RpmCalculator rpmCalculator
        +pray(Side, String) Prayer
        +prayBatch(Side, String, int) void
        +getTodayCount() PrayerCount
        +getCurrentStats() PrayerStats
    }

    class FallbackManager {
        -RedisPrayerCountAdapter redisAdapter
        -InMemoryPrayerCountAdapter inMemoryAdapter
        -AtomicBoolean usingFallback
        +increment(Side, long) long
        +getCount() PrayerCount
        +checkAndRecover() void
    }

    class RedisPrayerCountAdapter {
        -StringRedisTemplate redisTemplate
        -RedisKeyGenerator keyGenerator
        +increment(Side, long) long
        +getCount() PrayerCount
        +merge(PrayerCount) void
        +isAvailable() boolean
    }

    class InMemoryPrayerCountAdapter {
        -AtomicLong upCount
        -AtomicLong downCount
        +increment(Side, long) long
        +getCount() PrayerCount
        +getAndReset() PrayerCount
        +hasData() boolean
    }

    class RedisKeyGenerator {
        +generateKey(Side) String
        +getUpKey() String
        +getDownKey() String
        +getTtlSeconds() long
    }

    PrayerUseCase <|.. PrayerService : implements
    PrayerQuery <|.. PrayerService : implements
    PrayerCountPort <|.. FallbackManager : implements
    PrayerCountPort <|.. RedisPrayerCountAdapter : implements
    PrayerCountPort <|.. InMemoryPrayerCountAdapter : implements

    PrayerService --> FallbackManager : uses
    FallbackManager --> RedisPrayerCountAdapter : primary
    FallbackManager --> InMemoryPrayerCountAdapter : fallback
    RedisPrayerCountAdapter --> RedisKeyGenerator : uses
```

### 도메인 모델

```mermaid
classDiagram
    direction LR

    class Side {
        <<enumeration>>
        UP
        DOWN
        +getKey() String
        +getDisplayName() String
        +fromKey(String) Side
    }

    class Prayer {
        <<record>>
        +String id
        +Side side
        +String sessionId
        +Instant timestamp
        +create(Side, String) Prayer
    }

    class PrayerCount {
        <<record>>
        +long upCount
        +long downCount
        +zero() PrayerCount
        +incrementUp(long) PrayerCount
        +incrementDown(long) PrayerCount
        +increment(Side, long) PrayerCount
        +total() long
        +upRatio() double
        +merge(PrayerCount) PrayerCount
    }

    class PrayerStats {
        <<record>>
        +PrayerCount count
        +double upRpm
        +double downRpm
        +long timestamp
        +create(PrayerCount, double, double) PrayerStats
        +totalRpm() double
    }

    Prayer --> Side : has
    PrayerStats --> PrayerCount : contains
```

---

## 3. 시퀀스 다이어그램

### Flow 1: 단일 기도 등록 (pray)

```mermaid
sequenceDiagram
    autonumber
    participant Client as External Client
    participant PS as PrayerService
    participant FM as FallbackManager
    participant Redis as RedisPrayerCountAdapter
    participant IM as InMemoryAdapter
    participant DB as Redis DB

    Client->>PS: pray(UP, "session-123")

    PS->>PS: Prayer.create(UP, "session-123")
    Note right of PS: UUID 생성, 타임스탬프 기록

    PS->>FM: increment(UP, 1)

    alt usingFallback == false (정상)
        FM->>Redis: increment(UP, 1)
        Redis->>Redis: generateKey(UP)
        Note right of Redis: "prayer:20260120:up"
        Redis->>DB: INCRBY key 1
        DB-->>Redis: newValue
        opt result == delta (최초 생성)
            Redis->>DB: EXPIRE key 48h
        end
        Redis-->>FM: newValue
    else usingFallback == true (폴백)
        FM->>IM: increment(UP, 1)
        IM->>IM: upCount.addAndGet(1)
        IM-->>FM: newValue
    end

    FM-->>PS: newValue

    PS->>PS: rpmCalculator.record(UP)
    Note right of PS: 이벤트 큐에 타임스탬프 추가

    PS-->>Client: Prayer
```

### Flow 2: 배치 기도 등록 (prayBatch)

```mermaid
sequenceDiagram
    autonumber
    participant Client as External Client
    participant PS as PrayerService
    participant FM as FallbackManager
    participant DB as Redis DB

    Client->>PS: prayBatch(UP, "session-123", 5)

    PS->>FM: increment(UP, 5)
    Note right of FM: 한 번의 Redis 호출로 5 증가
    FM->>DB: INCRBY prayer:20260120:up 5
    DB-->>FM: newValue
    FM-->>PS: newValue

    loop 5회 반복
        PS->>PS: rpmCalculator.record(UP)
        Note right of PS: 개별 타임스탬프 기록
    end

    PS-->>Client: void
```

### Flow 3: 카운트 조회 (getTodayCount)

```mermaid
sequenceDiagram
    autonumber
    participant Client as External Client
    participant PS as PrayerService
    participant FM as FallbackManager
    participant Redis as RedisPrayerCountAdapter
    participant DB as Redis DB

    Client->>PS: getTodayCount()

    PS->>FM: getCount()

    alt usingFallback == false
        FM->>Redis: getCount()
        Redis->>Redis: getUpKey(), getDownKey()
        Redis->>DB: MGET prayer:20260120:up prayer:20260120:down
        DB-->>Redis: ["100", "50"]
        Redis->>Redis: parseCount() x2
        Redis-->>FM: PrayerCount(100, 50)
    else usingFallback == true
        FM->>FM: inMemoryAdapter.getCount()
        Note right of FM: AtomicLong에서 직접 조회
    end

    FM-->>PS: PrayerCount
    PS-->>Client: PrayerCount(100, 50)
```

### Flow 4: 통계 조회 (getCurrentStats)

```mermaid
sequenceDiagram
    autonumber
    participant Client as External Client
    participant PS as PrayerService
    participant FM as FallbackManager
    participant Rpm as RpmCalculator

    Client->>PS: getCurrentStats()

    PS->>FM: getCount()
    FM-->>PS: PrayerCount(100, 50)

    PS->>Rpm: getRpm(UP)
    Rpm->>Rpm: cleanOldEvents(now)
    Note right of Rpm: 60초 이전 이벤트 제거
    Rpm-->>PS: 500.0 (upRpm)

    PS->>Rpm: getRpm(DOWN)
    Rpm-->>PS: 300.0 (downRpm)

    PS->>PS: PrayerStats.create(count, 500, 300)

    PS-->>Client: PrayerStats
```

### Flow 5: 폴백 복구 (checkAndRecover)

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Spring Scheduler
    participant FM as FallbackManager
    participant IM as InMemoryAdapter
    participant Redis as RedisPrayerCountAdapter
    participant DB as Redis DB

    Note over Scheduler: 30초마다 실행

    Scheduler->>FM: checkAndRecover()

    alt usingFallback == false
        FM-->>Scheduler: return (아무것도 안함)
    else usingFallback == true
        FM->>Redis: isAvailable()
        Redis->>DB: PING

        alt Redis 복구됨
            DB-->>Redis: PONG
            Redis-->>FM: true

            FM->>IM: hasData()
            IM-->>FM: true

            FM->>IM: getAndReset()
            Note right of IM: 원자적 읽기 + 0으로 초기화
            IM-->>FM: PrayerCount(25, 15)

            FM->>Redis: merge(PrayerCount)
            Redis->>DB: INCRBY up 25
            Redis->>DB: INCRBY down 15

            FM->>FM: usingFallback.set(false)
            Note right of FM: 정상 모드로 복귀

        else Redis 아직 장애
            DB-->>Redis: Exception
            Redis-->>FM: false
            Note right of FM: 다음 30초 후 재시도
        end
    end

    FM-->>Scheduler: 완료
```

---

## 4. 상태 다이어그램

### 폴백 상태 전이

```mermaid
stateDiagram-v2
    [*] --> Normal: 서버 시작

    Normal: 정상 모드
    Normal: usingFallback = false
    Normal: 모든 요청 → Redis

    Fallback: 폴백 모드
    Fallback: usingFallback = true
    Fallback: 모든 요청 → InMemory

    Recovery: 복구 중
    Recovery: 데이터 병합 진행

    Normal --> Fallback: Redis 예외 발생
    Fallback --> Recovery: isAvailable() = true
    Recovery --> Normal: merge() 완료
    Fallback --> Fallback: isAvailable() = false<br/>(30초 후 재시도)
```

### 장애 시나리오 타임라인

```mermaid
gantt
    title 폴백 시나리오 타임라인
    dateFormat ss
    axisFormat %S초

    section 정상 상태
    Redis 처리     :normal1, 00, 3s

    section 장애 발생
    Redis 장애!     :crit, fault, after normal1, 1s
    폴백 전환       :milestone, after fault, 0s

    section 폴백 모드
    InMemory 처리   :fallback, after fault, 27s
    복구 시도 (실패) :milestone, 30, 0s
    InMemory 계속   :fallback2, 30, 30s
    복구 시도 (실패) :milestone, 60, 0s

    section 복구
    Redis 복구됨    :milestone, 65, 0s
    복구 시도 (성공) :done, recover, 90, 1s
    데이터 병합     :done, merge, after recover, 1s

    section 정상 복귀
    Redis 처리     :normal2, after merge, 10s
```

---

## 5. 코드 위치 참조

### 파일별 핵심 메서드

```mermaid
mindmap
  root((Phase 2a<br/>코드 구조))
    Application
      PrayerService.java
        :26 pray
        :34 prayBatch
        :42 getTodayCount
        :47 getCurrentStats
        :57-94 RpmCalculator
    Infrastructure
      FallbackManager.java
        :31 increment
        :46 getCount
        :72 checkAndRecover
      InMemoryPrayerCountAdapter.java
        :17 increment
        :25 getCount
        :40 getAndReset
    Adapter
      RedisPrayerCountAdapter.java
        :30 increment
        :43 getCount
        :62 merge
        :73 isAvailable
      RedisKeyGenerator.java
        generateKey
        getUpKey
        getDownKey
```

### 포트 인터페이스 위치

| 포트 | 파일 경로 | 방향 |
|------|-----------|------|
| `PrayerUseCase` | `application/port/in/PrayerUseCase.java` | Driving (In) |
| `PrayerQuery` | `application/port/in/PrayerQuery.java` | Driving (In) |
| `PrayerCountPort` | `application/port/out/PrayerCountPort.java` | Driven (Out) |

### 도메인 모델 위치

| 모델 | 파일 경로 | 설명 |
|------|-----------|------|
| `Side` | `domain/model/Side.java` | UP/DOWN enum |
| `Prayer` | `domain/model/Prayer.java` | 기도 액션 record |
| `PrayerCount` | `domain/model/PrayerCount.java` | 카운트 Value Object |
| `PrayerStats` | `domain/model/PrayerStats.java` | 통계 Value Object |

---

## 요약

이 다이어그램 문서는 Phase 2a에서 구현된 코드의 구조와 데이터 흐름을 Mermaid로 시각화합니다:

1. **아키텍처 레이어**: 헥사고날 아키텍처의 각 레이어 구성
2. **컴포넌트 관계도**: 클래스/인터페이스 의존성 및 도메인 모델
3. **시퀀스 다이어그램**: 5개 주요 유스케이스의 호출 흐름
4. **상태 다이어그램**: 폴백 모드 전이 및 복구 시나리오

다음 Phase 2b에서 WebSocket Handler가 추가되면 시퀀스 다이어그램의 진입점이 완성됩니다.
