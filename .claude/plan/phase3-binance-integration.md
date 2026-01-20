# Phase 3: 바이낸스 연동

## 목표
바이낸스 Futures WebSocket API를 통해 실시간 청산 데이터와 BTC 시세를 수신한다.

## 선행 의존성
- Phase 2b: Backend WebSocket 완료

## 범위
- 청산 스트림 연동 (`!forceOrder@arr`)
- 시세 스트림 연동 (`btcusdt@ticker`)
- Exponential Backoff 재연결
- 데이터 파싱 및 도메인 변환
- 대형 청산 판별 ($100,000 이상)

---

## 디렉토리 구조

```
backend/src/main/java/com/crypto/prayer/
├── adapter/
│   └── out/
│       └── binance/
│           ├── BinanceConfig.java
│           ├── BinanceWebSocketClient.java
│           ├── LiquidationStreamHandler.java
│           ├── TickerStreamHandler.java
│           ├── dto/
│           │   ├── BinanceLiquidationEvent.java
│           │   ├── BinanceTickerEvent.java
│           │   └── BinanceStreamMessage.java
│           └── reconnect/
│               └── ExponentialBackoff.java
├── application/
│   ├── port/
│   │   └── out/
│           ├── LiquidationStreamPort.java
│           └── TickerStreamPort.java
│   └── service/
│       └── MarketDataService.java
└── domain/
    └── model/
        ├── Liquidation.java
        └── Ticker.java
```

---

## 상세 구현 단계

### 3.1 도메인 모델

#### domain/model/Liquidation.java
```java
package com.crypto.prayer.domain.model;

import java.time.Instant;

public record Liquidation(
    String symbol,
    LiquidationSide side,
    double quantity,
    double price,
    double usdValue,
    Instant timestamp
) {
    private static final double LARGE_THRESHOLD = 100_000.0;

    public enum LiquidationSide {
        LONG, SHORT
    }

    public static Liquidation of(
            String symbol,
            String side,
            double quantity,
            double price) {
        LiquidationSide liqSide = "SELL".equalsIgnoreCase(side)
            ? LiquidationSide.LONG   // 매도 청산 = 롱 포지션 청산
            : LiquidationSide.SHORT; // 매수 청산 = 숏 포지션 청산

        return new Liquidation(
            symbol,
            liqSide,
            quantity,
            price,
            quantity * price,
            Instant.now()
        );
    }

    public boolean isLarge() {
        return usdValue >= LARGE_THRESHOLD;
    }

    public String formattedValue() {
        if (usdValue >= 1_000_000) {
            return String.format("$%.2fM", usdValue / 1_000_000);
        } else if (usdValue >= 1_000) {
            return String.format("$%.1fK", usdValue / 1_000);
        }
        return String.format("$%.0f", usdValue);
    }
}
```

#### domain/model/Ticker.java
```java
package com.crypto.prayer.domain.model;

import java.time.Instant;

public record Ticker(
    String symbol,
    double price,
    double priceChange24h,   // 24시간 변동률 (%)
    double high24h,
    double low24h,
    double volume24h,
    Instant timestamp
) {
    public static Ticker of(
            String symbol,
            double price,
            double priceChangePercent) {
        return new Ticker(
            symbol,
            price,
            priceChangePercent,
            0, 0, 0,  // 필요시 확장
            Instant.now()
        );
    }

    public boolean isPositive() {
        return priceChange24h >= 0;
    }

    public String formattedPrice() {
        if (price >= 1000) {
            return String.format("$%.2f", price);
        }
        return String.format("$%.4f", price);
    }

    public String formattedChange() {
        String sign = priceChange24h >= 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, priceChange24h);
    }
}
```

### 3.2 바이낸스 DTO

#### adapter/out/binance/dto/BinanceLiquidationEvent.java
```java
package com.crypto.prayer.adapter.out.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 바이낸스 청산 이벤트
 * {
 *   "e": "forceOrder",
 *   "E": 1568014460893,
 *   "o": {
 *     "s": "BTCUSDT",
 *     "S": "SELL",
 *     "o": "LIMIT",
 *     "f": "IOC",
 *     "q": "0.014",
 *     "p": "9910",
 *     "ap": "9910",
 *     "X": "FILLED",
 *     "l": "0.014",
 *     "z": "0.014",
 *     "T": 1568014460893
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceLiquidationEvent(
    @JsonProperty("e") String eventType,
    @JsonProperty("E") long eventTime,
    @JsonProperty("o") Order order
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(
        @JsonProperty("s") String symbol,       // 심볼
        @JsonProperty("S") String side,         // SELL 또는 BUY
        @JsonProperty("q") String quantity,     // 수량
        @JsonProperty("p") String price,        // 가격
        @JsonProperty("ap") String avgPrice,    // 평균 체결가
        @JsonProperty("X") String status,       // FILLED
        @JsonProperty("z") String filledQty     // 체결 수량
    ) {}

    public double getQuantity() {
        return Double.parseDouble(order.filledQty());
    }

    public double getPrice() {
        return Double.parseDouble(order.avgPrice());
    }

    public String getSymbol() {
        return order.symbol();
    }

    public String getSide() {
        return order.side();
    }
}
```

#### adapter/out/binance/dto/BinanceTickerEvent.java
```java
package com.crypto.prayer.adapter.out.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 바이낸스 24시간 티커
 * {
 *   "e": "24hrTicker",
 *   "E": 1672515782136,
 *   "s": "BTCUSDT",
 *   "p": "100.00000000",
 *   "P": "0.50",
 *   "c": "20100.00000000",
 *   "h": "20500.00000000",
 *   "l": "19800.00000000",
 *   "v": "100000.00000000",
 *   "q": "2000000000.00000000"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTickerEvent(
    @JsonProperty("e") String eventType,
    @JsonProperty("E") long eventTime,
    @JsonProperty("s") String symbol,
    @JsonProperty("c") String closePrice,      // 현재가
    @JsonProperty("P") String priceChangePercent, // 24시간 변동률
    @JsonProperty("h") String highPrice,       // 24시간 최고가
    @JsonProperty("l") String lowPrice,        // 24시간 최저가
    @JsonProperty("v") String volume           // 24시간 거래량
) {
    public double getPrice() {
        return Double.parseDouble(closePrice);
    }

    public double getPriceChangePercent() {
        return Double.parseDouble(priceChangePercent);
    }
}
```

### 3.3 Exponential Backoff

#### adapter/out/binance/reconnect/ExponentialBackoff.java
```java
package com.crypto.prayer.adapter.out.binance.reconnect;

import java.util.concurrent.ThreadLocalRandom;

public class ExponentialBackoff {

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final double jitterFactor;

    private int attempt = 0;

    public ExponentialBackoff() {
        this(1000, 30000, 2.0, 0.1);
    }

    public ExponentialBackoff(
            long initialDelayMs,
            long maxDelayMs,
            double multiplier,
            double jitterFactor) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
        this.jitterFactor = jitterFactor;
    }

    /**
     * 다음 재시도까지 대기 시간 (ms)
     */
    public long nextDelayMs() {
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt));
        delay = Math.min(delay, maxDelayMs);

        // Jitter 추가 (±10%)
        double jitter = delay * jitterFactor * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
        delay = (long) (delay + jitter);

        attempt++;
        return Math.max(delay, 0);
    }

    /**
     * 성공 시 리셋
     */
    public void reset() {
        attempt = 0;
    }

    public int getAttempt() {
        return attempt;
    }
}
```

### 3.4 바이낸스 설정

#### adapter/out/binance/BinanceConfig.java
```java
package com.crypto.prayer.adapter.out.binance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "binance")
public class BinanceConfig {

    private String liquidationStreamUrl = "wss://fstream.binance.com/ws/!forceOrder@arr";
    private String tickerStreamUrl = "wss://fstream.binance.com/ws/btcusdt@ticker";

    private int reconnectInitialDelayMs = 1000;
    private int reconnectMaxDelayMs = 30000;

    // Getters and Setters
    public String getLiquidationStreamUrl() {
        return liquidationStreamUrl;
    }

    public void setLiquidationStreamUrl(String liquidationStreamUrl) {
        this.liquidationStreamUrl = liquidationStreamUrl;
    }

    public String getTickerStreamUrl() {
        return tickerStreamUrl;
    }

    public void setTickerStreamUrl(String tickerStreamUrl) {
        this.tickerStreamUrl = tickerStreamUrl;
    }

    public int getReconnectInitialDelayMs() {
        return reconnectInitialDelayMs;
    }

    public void setReconnectInitialDelayMs(int reconnectInitialDelayMs) {
        this.reconnectInitialDelayMs = reconnectInitialDelayMs;
    }

    public int getReconnectMaxDelayMs() {
        return reconnectMaxDelayMs;
    }

    public void setReconnectMaxDelayMs(int reconnectMaxDelayMs) {
        this.reconnectMaxDelayMs = reconnectMaxDelayMs;
    }
}
```

### 3.5 WebSocket 클라이언트

#### adapter/out/binance/BinanceWebSocketClient.java
```java
package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.out.binance.reconnect.ExponentialBackoff;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class BinanceWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private final BinanceConfig config;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, WebSocketConnection> connections;

    public BinanceWebSocketClient(BinanceConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.connections = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        log.info("BinanceWebSocketClient initialized");
    }

    @PreDestroy
    public void destroy() {
        connections.values().forEach(WebSocketConnection::close);
        scheduler.shutdown();
        log.info("BinanceWebSocketClient destroyed");
    }

    /**
     * 스트림 연결
     */
    public void connect(String streamName, String url, Consumer<String> messageHandler) {
        WebSocketConnection connection = new WebSocketConnection(
            streamName, url, messageHandler);
        connections.put(streamName, connection);
        connection.connect();
    }

    /**
     * 스트림 연결 해제
     */
    public void disconnect(String streamName) {
        WebSocketConnection connection = connections.remove(streamName);
        if (connection != null) {
            connection.close();
        }
    }

    private class WebSocketConnection implements WebSocket.Listener {

        private final String streamName;
        private final String url;
        private final Consumer<String> messageHandler;
        private final ExponentialBackoff backoff;
        private final StringBuilder messageBuffer;

        private volatile WebSocket webSocket;
        private volatile boolean closed = false;

        WebSocketConnection(String streamName, String url, Consumer<String> messageHandler) {
            this.streamName = streamName;
            this.url = url;
            this.messageHandler = messageHandler;
            this.backoff = new ExponentialBackoff(
                config.getReconnectInitialDelayMs(),
                config.getReconnectMaxDelayMs(),
                2.0, 0.1
            );
            this.messageBuffer = new StringBuilder();
        }

        void connect() {
            if (closed) return;

            log.info("Connecting to {} stream: {}", streamName, url);

            httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    backoff.reset();
                    log.info("Connected to {} stream", streamName);
                })
                .exceptionally(ex -> {
                    log.error("Failed to connect to {} stream: {}",
                        streamName, ex.getMessage());
                    scheduleReconnect();
                    return null;
                });
        }

        void close() {
            closed = true;
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown");
            }
        }

        private void scheduleReconnect() {
            if (closed) return;

            long delay = backoff.nextDelayMs();
            log.info("Scheduling reconnect for {} in {}ms (attempt {})",
                streamName, delay, backoff.getAttempt());

            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            log.debug("{} WebSocket opened", streamName);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);

            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);

                try {
                    messageHandler.accept(message);
                } catch (Exception e) {
                    log.error("Error processing {} message: {}",
                        streamName, e.getMessage());
                }
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("{} WebSocket closed: {} - {}",
                streamName, statusCode, reason);

            if (!closed) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("{} WebSocket error: {}",
                streamName, error.getMessage());

            if (!closed) {
                scheduleReconnect();
            }
        }
    }
}
```

### 3.6 청산 스트림 핸들러

#### adapter/out/binance/LiquidationStreamHandler.java
```java
package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.in.websocket.dto.LiquidationMessage;
import com.crypto.prayer.adapter.out.binance.dto.BinanceLiquidationEvent;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.crypto.prayer.domain.model.Liquidation;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LiquidationStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(LiquidationStreamHandler.class);

    private final BinanceWebSocketClient webSocketClient;
    private final BinanceConfig config;
    private final BroadcastPort broadcastPort;
    private final ObjectMapper objectMapper;

    public LiquidationStreamHandler(
            BinanceWebSocketClient webSocketClient,
            BinanceConfig config,
            BroadcastPort broadcastPort,
            ObjectMapper objectMapper) {
        this.webSocketClient = webSocketClient;
        this.config = config;
        this.broadcastPort = broadcastPort;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        webSocketClient.connect(
            "liquidation",
            config.getLiquidationStreamUrl(),
            this::handleMessage
        );
        log.info("Liquidation stream handler started");
    }

    private void handleMessage(String message) {
        try {
            BinanceLiquidationEvent event = objectMapper.readValue(
                message, BinanceLiquidationEvent.class);

            // 도메인 모델로 변환
            Liquidation liquidation = Liquidation.of(
                event.getSymbol(),
                event.getSide(),
                event.getQuantity(),
                event.getPrice()
            );

            // 브로드캐스트 DTO로 변환
            LiquidationMessage liqMessage = LiquidationMessage.of(
                liquidation.symbol(),
                liquidation.side().name(),
                liquidation.quantity(),
                liquidation.price()
            );

            broadcastPort.broadcastLiquidation(liqMessage);

            if (liquidation.isLarge()) {
                log.info("Large liquidation detected: {} {} {}",
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

### 3.7 시세 스트림 핸들러

#### adapter/out/binance/TickerStreamHandler.java
```java
package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.in.websocket.dto.TickerMessage;
import com.crypto.prayer.adapter.out.binance.dto.BinanceTickerEvent;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.crypto.prayer.domain.model.Ticker;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TickerStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(TickerStreamHandler.class);
    private static final long BROADCAST_INTERVAL_MS = 1000; // 1초마다 브로드캐스트

    private final BinanceWebSocketClient webSocketClient;
    private final BinanceConfig config;
    private final BroadcastPort broadcastPort;
    private final ObjectMapper objectMapper;

    private final AtomicReference<Ticker> latestTicker = new AtomicReference<>();
    private volatile long lastBroadcastTime = 0;

    public TickerStreamHandler(
            BinanceWebSocketClient webSocketClient,
            BinanceConfig config,
            BroadcastPort broadcastPort,
            ObjectMapper objectMapper) {
        this.webSocketClient = webSocketClient;
        this.config = config;
        this.broadcastPort = broadcastPort;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        webSocketClient.connect(
            "ticker",
            config.getTickerStreamUrl(),
            this::handleMessage
        );
        log.info("Ticker stream handler started");
    }

    private void handleMessage(String message) {
        try {
            BinanceTickerEvent event = objectMapper.readValue(
                message, BinanceTickerEvent.class);

            // 도메인 모델로 변환
            Ticker ticker = Ticker.of(
                event.symbol(),
                event.getPrice(),
                event.getPriceChangePercent()
            );

            latestTicker.set(ticker);

            // 스로틀링: 1초마다만 브로드캐스트
            long now = System.currentTimeMillis();
            if (now - lastBroadcastTime >= BROADCAST_INTERVAL_MS) {
                TickerMessage tickerMessage = TickerMessage.of(
                    ticker.symbol(),
                    ticker.price(),
                    ticker.priceChange24h()
                );

                broadcastPort.broadcastTicker(tickerMessage);
                lastBroadcastTime = now;
            }

        } catch (Exception e) {
            log.error("Failed to parse ticker message: {}", e.getMessage());
        }
    }

    public Ticker getLatestTicker() {
        return latestTicker.get();
    }
}
```

### 3.8 Application 설정 추가

#### application.yml 추가
```yaml
binance:
  liquidation-stream-url: wss://fstream.binance.com/ws/!forceOrder@arr
  ticker-stream-url: wss://fstream.binance.com/ws/btcusdt@ticker
  reconnect-initial-delay-ms: 1000
  reconnect-max-delay-ms: 30000
```

---

## 바이낸스 API 정보

### 청산 스트림
- **URL**: `wss://fstream.binance.com/ws/!forceOrder@arr`
- **설명**: 모든 심볼의 청산 주문 실시간 수신
- **Rate Limit**: 없음 (구독 스트림)

### 24시간 티커 스트림
- **URL**: `wss://fstream.binance.com/ws/btcusdt@ticker`
- **설명**: BTCUSDT 24시간 티커 실시간 수신
- **Rate Limit**: 없음 (구독 스트림)

### 재연결 전략
| 시도 | 대기 시간 |
|------|----------|
| 1 | 1초 |
| 2 | 2초 |
| 3 | 4초 |
| 4 | 8초 |
| 5 | 16초 |
| 6+ | 30초 (최대) |

---

## 체크리스트

- [ ] 도메인 모델 구현
  - [ ] Liquidation record
  - [ ] Ticker record
- [ ] 바이낸스 DTO 구현
  - [ ] BinanceLiquidationEvent
  - [ ] BinanceTickerEvent
- [ ] Exponential Backoff 구현
  - [ ] 지수 증가 딜레이
  - [ ] Jitter 추가
  - [ ] 최대 딜레이 제한
- [ ] BinanceWebSocketClient 구현
  - [ ] Java HttpClient WebSocket
  - [ ] 자동 재연결
  - [ ] 연결 상태 관리
- [ ] 스트림 핸들러 구현
  - [ ] LiquidationStreamHandler
  - [ ] TickerStreamHandler
  - [ ] 스로틀링 (티커 1초)
- [ ] 대형 청산 판별
  - [ ] $100,000 이상 감지
  - [ ] 로깅
- [ ] 통합 테스트
  - [ ] Mock WebSocket 테스트
  - [ ] 재연결 테스트
  - [ ] 메시지 파싱 테스트

---

## 검증 명령어

```bash
# 바이낸스 연동 테스트
cd backend && ./gradlew test --tests "*Binance*"

# 수동 테스트 (wscat으로 직접 확인)
wscat -c wss://fstream.binance.com/ws/btcusdt@ticker

# 로그 확인
tail -f logs/application.log | grep -E "(Liquidation|Ticker)"
```

---

## 다음 Phase
→ [Phase 4: Frontend 코어](phase4-frontend-core.md)
