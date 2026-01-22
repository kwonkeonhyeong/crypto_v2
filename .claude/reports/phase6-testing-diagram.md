# Phase 6: 테스트 구조 다이어그램

## 1. Backend 테스트 구조

```mermaid
graph TB
    subgraph "Unit Tests"
        subgraph Domain["Domain Layer"]
            DM1[PrayerCountTest]
            DM2[SideTest]
            DM3[PrayerTest]
            DM4[PrayerStatsTest]
            DM5[LiquidationTest]
            DM6[TickerTest]
        end

        subgraph Application["Application Layer"]
            AP1[PrayerServiceTest]
            AP2[PrayerUseCaseTest]
            AP3[PrayerQueryTest]
            AP4[PrayerCountPortTest]
        end

        subgraph AdapterIn["Adapter In"]
            AI1[TokenBucketRateLimiterTest]
        end

        subgraph AdapterOut["Adapter Out"]
            AO1[RedisKeyGeneratorTest]
            AO2[RedisPrayerCountAdapterTest]
            AO3[BinanceConfigTest]
            AO4[BinanceWebSocketClientTest]
            AO5[BinanceLiquidationEventTest]
            AO6[BinanceTickerEventTest]
            AO7[ExponentialBackoffTest]
            AO8[LiquidationStreamHandlerTest]
            AO9[TickerStreamHandlerTest]
        end

        subgraph Infrastructure["Infrastructure"]
            IF1[FallbackManagerTest]
            IF2[InMemoryPrayerCountAdapterTest]
        end
    end

    subgraph "Integration Tests"
        IT1[WebSocketIntegrationTest]
        IT2[RedisIntegrationTest]
    end

    IT1 --> |"@SpringBootTest"| Spring[Spring Context]
    IT2 --> |"Testcontainers"| Redis[(Redis Container)]
```

## 2. Frontend 테스트 구조

```mermaid
graph TB
    subgraph "Store Tests"
        ST1[prayerStore.test.ts]
        ST2[themeStore.test.ts]
        ST3[toastStore.test.ts]
    end

    subgraph "Util Tests"
        UT1[exponentialBackoff.test.ts]
    end

    subgraph "Component Tests"
        CT1[PrayerButton.test.tsx]
    end

    subgraph "Hook Tests"
        HT1[useTheme.test.ts]
    end

    CT1 --> |uses| ST1
    HT1 --> |uses| ST2
```

## 3. WebSocket 통합 테스트 시퀀스

```mermaid
sequenceDiagram
    participant Test as TestClient
    participant WS as WebSocket Server
    participant Broker as Message Broker
    participant Controller as WebSocketController
    participant Service as PrayerService

    Test->>WS: CONNECT /ws
    WS-->>Test: CONNECTED

    Test->>Broker: SUBSCRIBE /topic/ticker
    Broker-->>Test: SUBSCRIBED

    Test->>Controller: SEND /app/prayer {"side":"up","count":1}
    Controller->>Service: prayBatch(UP, sessionId, 1)
    Service-->>Controller: void

    Note over Controller: Rate Limit 초과 시
    Controller-->>Test: ERROR /user/queue/errors

    Test->>WS: DISCONNECT
    WS-->>Test: DISCONNECTED
```

## 4. Redis 통합 테스트 시퀀스

```mermaid
sequenceDiagram
    participant Test as RedisIntegrationTest
    participant Adapter as RedisPrayerCountAdapter
    participant Redis as Redis Container
    participant KeyGen as RedisKeyGenerator

    Note over Test,Redis: setUp() - Clear all keys

    Test->>Adapter: increment(UP, 1L)
    Adapter->>KeyGen: generateKey(UP)
    KeyGen-->>Adapter: "prayer:20260120:up"
    Adapter->>Redis: INCRBY key 1
    Redis-->>Adapter: 1
    Adapter->>Redis: EXPIRE key TTL
    Adapter-->>Test: 1L

    Test->>Adapter: getCount()
    Adapter->>KeyGen: getUpKey(), getDownKey()
    Adapter->>Redis: MGET [upKey, downKey]
    Redis-->>Adapter: ["1", null]
    Adapter-->>Test: PrayerCount(1, 0)

    Test->>Adapter: isAvailable()
    Adapter->>Redis: PING
    Redis-->>Adapter: PONG
    Adapter-->>Test: true
```

## 5. PrayerButton 컴포넌트 테스트 구조

```mermaid
graph LR
    subgraph "Test Cases"
        TC1[렌더링 테스트]
        TC2[클릭 이벤트 테스트]
        TC3[disabled 상태 테스트]
        TC4[시각적 요소 테스트]
    end

    subgraph "Mocks"
        M1[framer-motion mock]
        M2[react-i18next mock]
    end

    subgraph "Assertions"
        A1[screen.getByText]
        A2[fireEvent.click]
        A3[expect.toHaveBeenCalled]
        A4[expect.toBeDisabled]
    end

    TC1 --> A1
    TC2 --> A2
    TC2 --> A3
    TC3 --> A4
    TC4 --> A1

    TC1 --> M2
    TC2 --> M1
```

## 6. useTheme 훅 테스트 구조

```mermaid
graph TB
    subgraph "Test Setup"
        S1[createStore - Jotai]
        S2[mockMatchMedia]
        S3[localStorage.clear]
    end

    subgraph "Test Cases"
        TC1[기본값 테스트]
        TC2[시스템 테마 감지]
        TC3[setTheme 테스트]
        TC4[toggleTheme 테스트]
        TC5[DOM 클래스 적용]
    end

    subgraph "Atoms"
        AT1[themePreferenceAtom]
        AT2[systemThemeAtom]
        AT3[resolvedThemeAtom]
        AT4[isDarkModeAtom]
    end

    S1 --> TC1
    S2 --> TC2
    TC1 --> AT1
    TC2 --> AT2
    TC3 --> AT1
    TC4 --> AT1
    TC5 --> AT4
```

## 7. 테스트 계층 구조

```mermaid
graph BT
    subgraph "Test Pyramid"
        E2E["E2E Tests<br/>(미구현)"]
        Integration["Integration Tests<br/>(WebSocket, Redis)"]
        Unit["Unit Tests<br/>(Domain, Service, Adapter)"]
    end

    Unit --> Integration
    Integration --> E2E

    style E2E fill:#ccc,stroke:#666
    style Integration fill:#90EE90,stroke:#228B22
    style Unit fill:#90EE90,stroke:#228B22
```

## 8. 테스트 의존성 관계

```mermaid
graph LR
    subgraph Backend
        JUnit5[JUnit 5]
        Mockito[Mockito]
        AssertJ[AssertJ]
        SpringTest[spring-boot-starter-test]
        Testcontainers[Testcontainers]

        SpringTest --> JUnit5
        SpringTest --> Mockito
        SpringTest --> AssertJ
        Testcontainers --> JUnit5
    end

    subgraph Frontend
        Vitest[Vitest]
        TestingLibrary[Testing Library]
        JSDOM[jsdom]

        TestingLibrary --> Vitest
        Vitest --> JSDOM
    end
```
