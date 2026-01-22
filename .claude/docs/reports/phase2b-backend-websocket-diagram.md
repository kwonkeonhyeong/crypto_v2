# Phase 2b: Backend WebSocket & STOMP 다이어그램

## 1. 컴포넌트 관계도

```mermaid
graph TD
    subgraph "Adapter Layer (In)"
        WSC[WebSocketConfig]
        CTRL[WebSocketController]
        WSSL[WebSocketSessionListener]
        RL[TokenBucketRateLimiter]
    end

    subgraph "Application Layer"
        PUC[PrayerUseCase]
        PQ[PrayerQuery]
        BP[BroadcastPort]
        BS[BroadcastService]
    end

    subgraph "Infrastructure"
        SCH[BroadcastScheduler]
    end

    subgraph "DTOs"
        REQ[PrayerRequest]
        RES[PrayerResponse]
        TM[TickerMessage]
        LM[LiquidationMessage]
    end

    CTRL --> |Rate Check| RL
    CTRL --> |Pray| PUC
    CTRL --> |Uses| REQ

    WSSL --> |Remove Client| RL

    SCH --> |Query| PQ
    SCH --> |Broadcast| BP

    BP --> |Implements| BS
    BS --> |Uses| RES
    BS --> |Uses| TM
    BS --> |Uses| LM
```

## 2. WebSocket 연결 흐름

```mermaid
sequenceDiagram
    participant C as Client
    participant WSE as WebSocket Endpoint
    participant WSSL as SessionListener
    participant RL as RateLimiter

    C->>WSE: CONNECT /ws (SockJS)
    WSE->>WSSL: SessionConnectedEvent
    WSSL->>WSSL: connectedSessions++
    WSE-->>C: CONNECTED

    C->>WSE: SUBSCRIBE /topic/prayer
    C->>WSE: SUBSCRIBE /topic/ticker
    C->>WSE: SUBSCRIBE /topic/liquidation

    Note over C,WSE: Connection Established

    C->>WSE: DISCONNECT
    WSE->>WSSL: SessionDisconnectEvent
    WSSL->>RL: removeClient(sessionId)
    WSSL->>WSSL: connectedSessions--
```

## 3. 기도 요청 처리 흐름

```mermaid
sequenceDiagram
    participant C as Client
    participant CTRL as WebSocketController
    participant RL as RateLimiter
    participant PS as PrayerService
    participant FM as FallbackManager

    C->>CTRL: SEND /app/prayer {side:"up", count:1}
    CTRL->>RL: tryConsume(sessionId)

    alt Rate Limit OK
        RL-->>CTRL: true
        CTRL->>PS: pray(Side.UP, sessionId)
        PS->>FM: increment(UP, 1)
        FM-->>PS: newCount
    else Rate Limit Exceeded
        RL-->>CTRL: false
        CTRL-->>C: /user/queue/errors {RATE_LIMIT_EXCEEDED}
    end
```

## 4. 브로드캐스트 스케줄러 흐름

```mermaid
sequenceDiagram
    participant SCH as BroadcastScheduler
    participant PQ as PrayerQuery
    participant BS as BroadcastService
    participant SMP as SimpMessagingTemplate
    participant Clients as All Clients

    loop Every 200ms
        SCH->>PQ: getCurrentStats()
        PQ-->>SCH: PrayerStats

        alt Stats Changed
            SCH->>SCH: Create PrayerResponse
            SCH->>BS: broadcastPrayerStats(response)
            BS->>SMP: convertAndSend("/topic/prayer", response)
            SMP-->>Clients: MESSAGE /topic/prayer
        else No Change
            Note over SCH: Skip broadcast
        end
    end
```

## 5. Rate Limiter 토큰 버킷

```mermaid
graph LR
    subgraph "Token Bucket (per client)"
        MAX[Max: 20 tokens]
        RATE[Refill: 5/sec]
        BUCKET[(Bucket)]
    end

    REQ[Request] --> CHECK{tokens > 0?}
    CHECK -->|Yes| CONSUME[Consume Token]
    CONSUME --> ALLOW[Allow Request]
    CHECK -->|No| DENY[Deny Request]

    REFILL[Every 200ms] --> |+1 token| BUCKET
    BUCKET --> CHECK
```

## 6. 에러 처리 흐름

```mermaid
flowchart TD
    REQ[Prayer Request] --> RL{Rate Limit Check}
    RL -->|Pass| SIDE{Valid Side?}
    RL -->|Fail| RLERR[RateLimitExceededException]
    RLERR --> HANDLER[@MessageExceptionHandler]
    HANDLER --> QUEUE["/user/queue/errors"]

    SIDE -->|Valid| PROCESS[Process Prayer]
    SIDE -->|Invalid| ILLERR[IllegalArgumentException]
    ILLERR --> LOG[Log Error]
```

## 7. DTO 구조

```mermaid
classDiagram
    class PrayerRequest {
        +String side
        +int count
        +toSide() Side
    }

    class PrayerResponse {
        +String type = "PRAYER"
        +long upCount
        +long downCount
        +double upRpm
        +double downRpm
        +double upRatio
        +double downRatio
        +long timestamp
        +from()$ PrayerResponse
    }

    class TickerMessage {
        +String type = "TICKER"
        +String symbol
        +double price
        +double priceChange24h
        +long timestamp
        +of()$ TickerMessage
    }

    class LiquidationMessage {
        +String type = "LIQUIDATION"
        +String symbol
        +String side
        +double quantity
        +double price
        +double usdValue
        +boolean isLarge
        +long timestamp
        +of()$ LiquidationMessage
    }
```
