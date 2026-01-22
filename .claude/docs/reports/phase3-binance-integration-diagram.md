# Phase 3: Binance Integration - 다이어그램

## 1. 컴포넌트 관계도

```mermaid
classDiagram
    direction TB

    %% Domain Models
    class Liquidation {
        <<record>>
        +String symbol
        +LiquidationSide side
        +double quantity
        +double price
        +double usdValue
        +Instant timestamp
        +of(symbol, side, qty, price) Liquidation
        +isLarge() boolean
        +formattedValue() String
    }

    class Ticker {
        <<record>>
        +String symbol
        +double price
        +double priceChange24h
        +double high24h
        +double low24h
        +double volume24h
        +Instant timestamp
        +of(symbol, price, change) Ticker
        +isPositive() boolean
        +formattedPrice() String
        +formattedChange() String
    }

    class LiquidationSide {
        <<enumeration>>
        LONG
        SHORT
    }

    %% DTOs
    class BinanceLiquidationEvent {
        <<record>>
        +String eventType
        +long eventTime
        +Order order
        +getSymbol() String
        +getSide() String
        +getQuantity() double
        +getPrice() double
    }

    class BinanceTickerEvent {
        <<record>>
        +String eventType
        +long eventTime
        +String symbol
        +String closePrice
        +String priceChangePercent
        +getPrice() double
        +getPriceChangePercent() double
    }

    %% Reconnect
    class ExponentialBackoff {
        -long initialDelayMs
        -long maxDelayMs
        -double multiplier
        -double jitterFactor
        -int attempt
        +nextDelayMs() long
        +reset() void
        +getAttempt() int
    }

    %% Config
    class BinanceConfig {
        -String liquidationStreamUrl
        -String tickerStreamUrl
        -int reconnectInitialDelayMs
        -int reconnectMaxDelayMs
        +getters/setters
    }

    %% WebSocket Client
    class BinanceWebSocketClient {
        -BinanceConfig config
        -HttpClient httpClient
        -ScheduledExecutorService scheduler
        -ConcurrentHashMap connections
        +connect(streamName, url, handler) void
        +disconnect(streamName) void
        +isConnected(streamName) boolean
    }

    class WebSocketConnection {
        <<inner class>>
        -String streamName
        -String url
        -Consumer messageHandler
        -ExponentialBackoff backoff
        -WebSocket webSocket
        +connect() void
        +close() void
        +isConnected() boolean
    }

    %% Handlers
    class LiquidationStreamHandler {
        -BinanceWebSocketClient webSocketClient
        -BinanceConfig config
        -BroadcastPort broadcastPort
        -ObjectMapper objectMapper
        +start() void
        +handleMessage(message) void
    }

    class TickerStreamHandler {
        -BinanceWebSocketClient webSocketClient
        -BinanceConfig config
        -BroadcastPort broadcastPort
        -ObjectMapper objectMapper
        -AtomicReference latestTicker
        +start() void
        +handleMessage(message) void
        +getLatestTicker() Ticker
    }

    %% Port (from Phase 2b)
    class BroadcastPort {
        <<interface>>
        +broadcastLiquidation(LiquidationMessage) void
        +broadcastTicker(TickerMessage) void
    }

    %% Relationships
    Liquidation --> LiquidationSide

    BinanceWebSocketClient --> BinanceConfig
    BinanceWebSocketClient --> WebSocketConnection
    WebSocketConnection --> ExponentialBackoff

    LiquidationStreamHandler --> BinanceWebSocketClient
    LiquidationStreamHandler --> BinanceConfig
    LiquidationStreamHandler --> BroadcastPort
    LiquidationStreamHandler ..> BinanceLiquidationEvent : uses
    LiquidationStreamHandler ..> Liquidation : creates

    TickerStreamHandler --> BinanceWebSocketClient
    TickerStreamHandler --> BinanceConfig
    TickerStreamHandler --> BroadcastPort
    TickerStreamHandler ..> BinanceTickerEvent : uses
    TickerStreamHandler ..> Ticker : creates
```

## 2. 청산 스트림 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant Binance as Binance WebSocket<br/>(forceOrder@arr)
    participant Client as BinanceWebSocketClient
    participant LiqHandler as LiquidationStreamHandler
    participant Mapper as ObjectMapper
    participant Domain as Liquidation
    participant Broadcast as BroadcastPort
    participant STOMP as STOMP Broker<br/>(/topic/liquidation)

    Note over Client: Application Startup
    LiqHandler->>Client: connect("liquidation", url, handler)
    Client->>Binance: WebSocket Connect
    Binance-->>Client: Connection Established

    loop Real-time Stream
        Binance->>Client: forceOrder Event (JSON)
        Client->>LiqHandler: handleMessage(json)
        LiqHandler->>Mapper: readValue(json, BinanceLiquidationEvent)
        Mapper-->>LiqHandler: event
        LiqHandler->>Domain: Liquidation.of(symbol, side, qty, price)
        Domain-->>LiqHandler: liquidation

        alt isLarge() == true
            LiqHandler->>LiqHandler: log.info("Large liquidation")
        end

        LiqHandler->>Broadcast: broadcastLiquidation(message)
        Broadcast->>STOMP: convertAndSend("/topic/liquidation", message)
    end

    Note over Client: Connection Lost
    Binance--xClient: Connection Closed
    Client->>Client: scheduleReconnect()
    Note over Client: Wait (1s, 2s, 4s, ...)
    Client->>Binance: Reconnect
    Binance-->>Client: Connection Restored
```

## 3. 시세 스트림 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant Binance as Binance WebSocket<br/>(btcusdt@ticker)
    participant Client as BinanceWebSocketClient
    participant TickerHandler as TickerStreamHandler
    participant Mapper as ObjectMapper
    participant Domain as Ticker
    participant Broadcast as BroadcastPort
    participant STOMP as STOMP Broker<br/>(/topic/ticker)

    Note over Client: Application Startup
    TickerHandler->>Client: connect("ticker", url, handler)
    Client->>Binance: WebSocket Connect
    Binance-->>Client: Connection Established

    loop Real-time Stream (~1초 간격)
        Binance->>Client: 24hrTicker Event (JSON)
        Client->>TickerHandler: handleMessage(json)
        TickerHandler->>Mapper: readValue(json, BinanceTickerEvent)
        Mapper-->>TickerHandler: event
        TickerHandler->>Domain: Ticker.of(symbol, price, change)
        Domain-->>TickerHandler: ticker
        TickerHandler->>TickerHandler: latestTicker.set(ticker)
        TickerHandler->>Broadcast: broadcastTicker(message)
        Broadcast->>STOMP: convertAndSend("/topic/ticker", message)
    end
```

## 4. 재연결 전략 플로우차트

```mermaid
flowchart TD
    A[WebSocket Connected] --> B{Connection Lost?}
    B -->|No| A
    B -->|Yes| C[Mark as Disconnected]
    C --> D{closed flag?}
    D -->|Yes| E[Stop - Manual Shutdown]
    D -->|No| F[Calculate Next Delay]
    F --> G[delay = initial * 2^attempt]
    G --> H{delay > maxDelay?}
    H -->|Yes| I[delay = maxDelay]
    H -->|No| J[Add Jitter ±10%]
    I --> J
    J --> K[Schedule Reconnect]
    K --> L[Wait delay ms]
    L --> M[Attempt Reconnect]
    M --> N{Success?}
    N -->|Yes| O[Reset backoff]
    O --> A
    N -->|No| P[Increment attempt]
    P --> D

    style A fill:#90EE90
    style E fill:#FFB6C1
```

## 5. 데이터 변환 플로우

```mermaid
flowchart LR
    subgraph Binance
        B1[forceOrder JSON]
        B2[24hrTicker JSON]
    end

    subgraph DTO Layer
        D1[BinanceLiquidationEvent]
        D2[BinanceTickerEvent]
    end

    subgraph Domain Layer
        M1[Liquidation]
        M2[Ticker]
    end

    subgraph Broadcast Layer
        C1[LiquidationMessage]
        C2[TickerMessage]
    end

    subgraph Client
        W1[WebSocket /topic/liquidation]
        W2[WebSocket /topic/ticker]
    end

    B1 -->|Jackson| D1
    B2 -->|Jackson| D2
    D1 -->|of()| M1
    D2 -->|of()| M2
    M1 -->|convert| C1
    M2 -->|convert| C2
    C1 -->|STOMP| W1
    C2 -->|STOMP| W2
```

## 6. 청산 방향 변환 로직

```mermaid
flowchart LR
    subgraph Binance API
        S1[SELL Order]
        S2[BUY Order]
    end

    subgraph Domain Mapping
        L1[LONG Liquidation<br/>롱 포지션 청산]
        L2[SHORT Liquidation<br/>숏 포지션 청산]
    end

    subgraph Explanation
        E1[롱 포지션 보유자가<br/>강제 매도당함]
        E2[숏 포지션 보유자가<br/>강제 매수당함]
    end

    S1 --> L1
    S2 --> L2
    L1 --> E1
    L2 --> E2

    style L1 fill:#FF6B6B
    style L2 fill:#4ECDC4
```

## 7. 대형 청산 감지

```mermaid
flowchart TD
    A[청산 이벤트 수신] --> B[USD 가치 계산]
    B --> C{usdValue >= $100,000?}
    C -->|Yes| D[isLarge = true]
    C -->|No| E[isLarge = false]
    D --> F[로그 기록]
    F --> G[브로드캐스트]
    E --> G

    style D fill:#FFD700
    style F fill:#FFA500
```

## 8. 전체 아키텍처 컨텍스트

```mermaid
flowchart TB
    subgraph External
        BIN[Binance Futures API]
    end

    subgraph Backend
        subgraph Adapter Layer
            WSC[BinanceWebSocketClient]
            LSH[LiquidationStreamHandler]
            TSH[TickerStreamHandler]
        end

        subgraph Domain Layer
            LIQ[Liquidation Model]
            TIC[Ticker Model]
        end

        subgraph Application Layer
            BP[BroadcastPort]
            BS[BroadcastService]
        end

        subgraph Infrastructure
            STOMP[STOMP Broker]
        end
    end

    subgraph Frontend
        WS[WebSocket Client]
        UI[React UI]
    end

    BIN -->|forceOrder| WSC
    BIN -->|btcusdt@ticker| WSC

    WSC --> LSH
    WSC --> TSH

    LSH --> LIQ
    TSH --> TIC

    LIQ --> BP
    TIC --> BP

    BP --> BS
    BS --> STOMP

    STOMP -->|/topic/liquidation| WS
    STOMP -->|/topic/ticker| WS

    WS --> UI
```
