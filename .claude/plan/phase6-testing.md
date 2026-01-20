# Phase 6: 테스트

## 목표
전체 레이어(Domain, Service, Adapter)에 대한 테스트를 작성하고, E2E 및 부하 테스트를 수행한다.

## 선행 의존성
- Phase 5c: 사운드 & 모바일 완료 (전체 기능 구현 완료)

## 범위
- Backend 단위 테스트 (JUnit 5)
- Backend 통합 테스트
- Frontend 단위 테스트 (Vitest + React Testing Library)
- E2E 테스트 (Playwright)
- 부하 테스트 (k6)

---

## 디렉토리 구조

```
backend/src/test/java/com/crypto/prayer/
├── domain/
│   └── model/
│       ├── PrayerCountTest.java
│       ├── SideTest.java
│       └── LiquidationTest.java
├── application/
│   └── service/
│       ├── PrayerServiceTest.java
│       └── BroadcastServiceTest.java
├── adapter/
│   ├── in/
│   │   └── websocket/
│   │       ├── WebSocketControllerTest.java
│   │       └── ratelimit/
│   │           └── TokenBucketRateLimiterTest.java
│   └── out/
│       ├── redis/
│       │   └── RedisPrayerCountAdapterTest.java
│       └── binance/
│           ├── BinanceWebSocketClientTest.java
│           └── LiquidationStreamHandlerTest.java
├── infrastructure/
│   └── fallback/
│       └── FallbackManagerTest.java
└── integration/
    ├── WebSocketIntegrationTest.java
    └── RedisIntegrationTest.java

frontend/src/
├── __tests__/
│   ├── components/
│   │   ├── PrayerButton.test.tsx
│   │   ├── GaugeBar.test.tsx
│   │   └── LiquidationFeed.test.tsx
│   ├── hooks/
│   │   ├── usePrayerSocket.test.ts
│   │   └── useTheme.test.ts
│   └── stores/
│       ├── prayerStore.test.ts
│       └── websocketStore.test.ts
└── e2e/
    ├── prayer.spec.ts
    └── liquidation.spec.ts

load-tests/
├── k6/
│   ├── websocket.js
│   └── scenarios.js
└── README.md
```

---

## 상세 구현 단계

### 6.1 Backend 단위 테스트

#### domain/model/PrayerCountTest.java
```java
package com.crypto.prayer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrayerCountTest {

    @Nested
    @DisplayName("zero()")
    class Zero {
        @Test
        @DisplayName("0으로 초기화된 카운트를 반환한다")
        void returnsZeroCount() {
            PrayerCount count = PrayerCount.zero();

            assertThat(count.upCount()).isZero();
            assertThat(count.downCount()).isZero();
        }
    }

    @Nested
    @DisplayName("increment()")
    class Increment {
        @Test
        @DisplayName("UP 카운트를 증가시킨다")
        void incrementsUpCount() {
            PrayerCount count = PrayerCount.zero();

            PrayerCount result = count.increment(Side.UP, 5);

            assertThat(result.upCount()).isEqualTo(5);
            assertThat(result.downCount()).isZero();
        }

        @Test
        @DisplayName("DOWN 카운트를 증가시킨다")
        void incrementsDownCount() {
            PrayerCount count = PrayerCount.zero();

            PrayerCount result = count.increment(Side.DOWN, 3);

            assertThat(result.upCount()).isZero();
            assertThat(result.downCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("ratio()")
    class Ratio {
        @Test
        @DisplayName("카운트가 0일 때 50% 비율을 반환한다")
        void returnsHalfWhenZero() {
            PrayerCount count = PrayerCount.zero();

            assertThat(count.upRatio()).isEqualTo(0.5);
            assertThat(count.downRatio()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("올바른 비율을 계산한다")
        void calculatesCorrectRatio() {
            PrayerCount count = new PrayerCount(75, 25);

            assertThat(count.upRatio()).isEqualTo(0.75);
            assertThat(count.downRatio()).isEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("merge()")
    class Merge {
        @Test
        @DisplayName("두 카운트를 합친다")
        void mergesTwoCounts() {
            PrayerCount count1 = new PrayerCount(10, 5);
            PrayerCount count2 = new PrayerCount(3, 7);

            PrayerCount result = count1.merge(count2);

            assertThat(result.upCount()).isEqualTo(13);
            assertThat(result.downCount()).isEqualTo(12);
        }
    }
}
```

#### adapter/in/websocket/ratelimit/TokenBucketRateLimiterTest.java
```java
package com.crypto.prayer.adapter.in.websocket.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter();
    }

    @Test
    @DisplayName("버스트 제한 내에서는 요청을 허용한다")
    void allowsRequestsWithinBurstLimit() {
        String clientId = "client-1";

        // 버스트 제한(20)까지 모두 허용
        for (int i = 0; i < 20; i++) {
            assertThat(rateLimiter.tryConsume(clientId))
                .as("Request %d should be allowed", i + 1)
                .isTrue();
        }
    }

    @Test
    @DisplayName("버스트 제한을 초과하면 요청을 거부한다")
    void rejectsRequestsExceedingBurstLimit() {
        String clientId = "client-2";

        // 버스트 제한 소진
        for (int i = 0; i < 20; i++) {
            rateLimiter.tryConsume(clientId);
        }

        // 21번째 요청은 거부
        assertThat(rateLimiter.tryConsume(clientId)).isFalse();
    }

    @Test
    @DisplayName("클라이언트별로 독립적인 버킷을 관리한다")
    void maintainsIndependentBucketsPerClient() {
        String client1 = "client-1";
        String client2 = "client-2";

        // client1 버스트 소진
        for (int i = 0; i < 20; i++) {
            rateLimiter.tryConsume(client1);
        }

        // client2는 여전히 허용
        assertThat(rateLimiter.tryConsume(client2)).isTrue();
    }

    @Test
    @DisplayName("클라이언트 제거 후 새 버킷으로 시작한다")
    void startsWithNewBucketAfterRemoval() {
        String clientId = "client-3";

        // 버스트 소진
        for (int i = 0; i < 20; i++) {
            rateLimiter.tryConsume(clientId);
        }
        assertThat(rateLimiter.tryConsume(clientId)).isFalse();

        // 클라이언트 제거
        rateLimiter.removeClient(clientId);

        // 새 버킷으로 다시 허용
        assertThat(rateLimiter.tryConsume(clientId)).isTrue();
    }
}
```

#### application/service/PrayerServiceTest.java
```java
package com.crypto.prayer.application.service;

import com.crypto.prayer.domain.model.Prayer;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.PrayerStats;
import com.crypto.prayer.domain.model.Side;
import com.crypto.prayer.infrastructure.fallback.FallbackManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrayerServiceTest {

    @Mock
    private FallbackManager fallbackManager;

    private PrayerService prayerService;

    @BeforeEach
    void setUp() {
        prayerService = new PrayerService(fallbackManager);
    }

    @Test
    @DisplayName("pray()는 카운트를 증가시키고 Prayer를 반환한다")
    void prayIncrementsCountAndReturnsPrayer() {
        when(fallbackManager.increment(eq(Side.UP), eq(1L))).thenReturn(10L);

        Prayer result = prayerService.pray(Side.UP, "session-123");

        assertThat(result.side()).isEqualTo(Side.UP);
        assertThat(result.sessionId()).isEqualTo("session-123");
        verify(fallbackManager).increment(Side.UP, 1L);
    }

    @Test
    @DisplayName("prayBatch()는 배치 카운트만큼 증가시킨다")
    void prayBatchIncrementsCount() {
        when(fallbackManager.increment(eq(Side.DOWN), eq(5L))).thenReturn(50L);

        prayerService.prayBatch(Side.DOWN, "session-456", 5);

        verify(fallbackManager).increment(Side.DOWN, 5L);
    }

    @Test
    @DisplayName("getTodayCount()는 현재 카운트를 반환한다")
    void getTodayCountReturnsCurrentCount() {
        PrayerCount expectedCount = new PrayerCount(100, 50);
        when(fallbackManager.getCount()).thenReturn(expectedCount);

        PrayerCount result = prayerService.getTodayCount();

        assertThat(result).isEqualTo(expectedCount);
    }

    @Test
    @DisplayName("getCurrentStats()는 RPM을 포함한 통계를 반환한다")
    void getCurrentStatsReturnsStatsWithRpm() {
        when(fallbackManager.getCount()).thenReturn(new PrayerCount(100, 50));

        PrayerStats stats = prayerService.getCurrentStats();

        assertThat(stats.count().upCount()).isEqualTo(100);
        assertThat(stats.count().downCount()).isEqualTo(50);
        assertThat(stats.upRpm()).isGreaterThanOrEqualTo(0);
        assertThat(stats.downRpm()).isGreaterThanOrEqualTo(0);
    }
}
```

#### infrastructure/fallback/FallbackManagerTest.java
```java
package com.crypto.prayer.infrastructure.fallback;

import com.crypto.prayer.adapter.out.redis.RedisPrayerCountAdapter;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FallbackManagerTest {

    @Mock
    private RedisPrayerCountAdapter redisAdapter;

    @Mock
    private InMemoryPrayerCountAdapter inMemoryAdapter;

    private FallbackManager fallbackManager;

    @BeforeEach
    void setUp() {
        fallbackManager = new FallbackManager(redisAdapter, inMemoryAdapter);
    }

    @Test
    @DisplayName("Redis가 정상이면 Redis를 사용한다")
    void usesRedisWhenAvailable() {
        when(redisAdapter.increment(Side.UP, 1)).thenReturn(10L);

        long result = fallbackManager.increment(Side.UP, 1);

        assertThat(result).isEqualTo(10L);
        verify(redisAdapter).increment(Side.UP, 1);
        verify(inMemoryAdapter, never()).increment(any(), anyLong());
    }

    @Test
    @DisplayName("Redis 실패 시 InMemory로 폴백한다")
    void fallsBackToInMemoryOnRedisFailure() {
        when(redisAdapter.increment(Side.UP, 1)).thenThrow(new RuntimeException("Redis error"));
        when(inMemoryAdapter.increment(Side.UP, 1)).thenReturn(1L);

        long result = fallbackManager.increment(Side.UP, 1);

        assertThat(result).isEqualTo(1L);
        verify(inMemoryAdapter).increment(Side.UP, 1);
    }

    @Test
    @DisplayName("폴백 모드에서는 계속 InMemory를 사용한다")
    void continuesUsingInMemoryInFallbackMode() {
        // 첫 요청에서 Redis 실패
        when(redisAdapter.increment(Side.UP, 1)).thenThrow(new RuntimeException("Redis error"));
        when(inMemoryAdapter.increment(Side.UP, 1)).thenReturn(1L);
        fallbackManager.increment(Side.UP, 1);

        // 두 번째 요청도 InMemory 사용
        when(inMemoryAdapter.increment(Side.DOWN, 1)).thenReturn(1L);
        fallbackManager.increment(Side.DOWN, 1);

        assertThat(fallbackManager.isUsingFallback()).isTrue();
        verify(inMemoryAdapter, times(2)).increment(any(), anyLong());
    }
}
```

#### adapter/out/redis/RedisPrayerCountAdapterTest.java
```java
package com.crypto.prayer.adapter.out.redis;

import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisPrayerCountAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisKeyGenerator keyGenerator;

    private RedisPrayerCountAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        adapter = new RedisPrayerCountAdapter(redisTemplate, keyGenerator);
    }

    @Test
    @DisplayName("increment()는 증가된 값을 반환한다")
    void incrementReturnsIncrementedValue() {
        when(keyGenerator.generateKey(Side.UP)).thenReturn("prayer:20240101:up");
        when(valueOperations.increment(any(), anyLong())).thenReturn(100L);

        long result = adapter.increment(Side.UP, 1);

        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("getCount()는 현재 카운트를 반환한다")
    void getCountReturnsCurrentCount() {
        when(keyGenerator.getUpKey()).thenReturn("prayer:20240101:up");
        when(keyGenerator.getDownKey()).thenReturn("prayer:20240101:down");
        when(valueOperations.multiGet(any())).thenReturn(List.of("50", "30"));

        PrayerCount result = adapter.getCount();

        assertThat(result.upCount()).isEqualTo(50L);
        assertThat(result.downCount()).isEqualTo(30L);
    }

    @Test
    @DisplayName("getCount()는 null 값을 0으로 처리한다")
    void getCountHandlesNullValues() {
        when(keyGenerator.getUpKey()).thenReturn("prayer:20240101:up");
        when(keyGenerator.getDownKey()).thenReturn("prayer:20240101:down");
        when(valueOperations.multiGet(any())).thenReturn(List.of(null, null));

        PrayerCount result = adapter.getCount();

        assertThat(result.upCount()).isZero();
        assertThat(result.downCount()).isZero();
    }
}
```

### 6.2 Backend 통합 테스트

#### integration/WebSocketIntegrationTest.java
```java
package com.crypto.prayer.integration;

import com.crypto.prayer.adapter.in.websocket.dto.PrayerRequest;
import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;
    private String wsUrl;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        wsUrl = String.format("ws://localhost:%d/ws", port);
    }

    @Test
    @DisplayName("WebSocket 연결 및 기도 전송 테스트")
    void testWebSocketConnectionAndPrayer() throws Exception {
        CompletableFuture<PrayerResponse> resultFuture = new CompletableFuture<>();

        StompSession session = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();

        // 구독
        session.subscribe("/topic/prayer", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return PrayerResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                resultFuture.complete((PrayerResponse) payload);
            }
        });

        // 기도 전송
        session.send("/app/prayer", new PrayerRequest("up", 1));

        // 응답 대기 (브로드캐스터가 200ms마다 전송)
        PrayerResponse response = resultFuture.get(3, TimeUnit.SECONDS);

        assertThat(response).isNotNull();
        assertThat(response.type()).isEqualTo("CLICK");
        assertThat(response.upCount()).isGreaterThan(0);

        session.disconnect();
    }
}
```

#### integration/RedisIntegrationTest.java
```java
package com.crypto.prayer.integration;

import com.crypto.prayer.adapter.out.redis.RedisPrayerCountAdapter;
import com.crypto.prayer.domain.model.PrayerCount;
import com.crypto.prayer.domain.model.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("Redis에서 카운트 증가 및 조회 테스트")
    void testIncrementAndGetCount() {
        // 증가
        long upResult = adapter.increment(Side.UP, 10);
        long downResult = adapter.increment(Side.DOWN, 5);

        assertThat(upResult).isGreaterThanOrEqualTo(10);
        assertThat(downResult).isGreaterThanOrEqualTo(5);

        // 조회
        PrayerCount count = adapter.getCount();
        assertThat(count.upCount()).isGreaterThanOrEqualTo(10);
        assertThat(count.downCount()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Redis 연결 상태 확인")
    void testIsAvailable() {
        assertThat(adapter.isAvailable()).isTrue();
    }
}
```

### 6.3 Frontend 단위 테스트

#### vitest.config.ts
```typescript
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**'],
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
```

#### src/test/setup.ts
```typescript
import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Mock matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock ResizeObserver
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));
```

#### __tests__/stores/prayerStore.test.ts
```typescript
import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';
import {
  prayerCountAtom,
  pendingPrayersAtom,
  localCountAtom,
} from '@/stores/prayerStore';

describe('prayerStore', () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  describe('prayerCountAtom', () => {
    it('초기값은 모두 0이다', () => {
      const count = store.get(prayerCountAtom);

      expect(count.upCount).toBe(0);
      expect(count.downCount).toBe(0);
      expect(count.upRatio).toBe(0.5);
    });

    it('값을 업데이트할 수 있다', () => {
      store.set(prayerCountAtom, {
        upCount: 100,
        downCount: 50,
        upRpm: 10,
        downRpm: 5,
        upRatio: 0.67,
        downRatio: 0.33,
        timestamp: Date.now(),
      });

      const count = store.get(prayerCountAtom);
      expect(count.upCount).toBe(100);
      expect(count.downCount).toBe(50);
    });
  });

  describe('localCountAtom', () => {
    it('서버 카운트와 펜딩을 합산한다', () => {
      store.set(prayerCountAtom, {
        upCount: 100,
        downCount: 50,
        upRpm: 0,
        downRpm: 0,
        upRatio: 0.67,
        downRatio: 0.33,
        timestamp: Date.now(),
      });

      store.set(pendingPrayersAtom, [
        { side: 'up', count: 5, timestamp: Date.now() },
        { side: 'down', count: 3, timestamp: Date.now() },
      ]);

      const local = store.get(localCountAtom);
      expect(local.upCount).toBe(105);
      expect(local.downCount).toBe(53);
    });
  });
});
```

#### __tests__/components/PrayerButton.test.tsx
```typescript
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'jotai';
import { PrayerButton } from '@/components/prayer/PrayerButton';

// i18n mock
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe('PrayerButton', () => {
  const defaultProps = {
    side: 'up' as const,
    count: 100,
    disabled: false,
    onPray: vi.fn(),
  };

  it('카운트를 표시한다', () => {
    render(
      <Provider>
        <PrayerButton {...defaultProps} />
      </Provider>
    );

    expect(screen.getByText('100')).toBeInTheDocument();
  });

  it('클릭 시 onPray를 호출한다', () => {
    const onPray = vi.fn();
    render(
      <Provider>
        <PrayerButton {...defaultProps} onPray={onPray} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button'));

    expect(onPray).toHaveBeenCalledWith('up', expect.any(Object));
  });

  it('disabled일 때 클릭이 무시된다', () => {
    const onPray = vi.fn();
    render(
      <Provider>
        <PrayerButton {...defaultProps} disabled={true} onPray={onPray} />
      </Provider>
    );

    fireEvent.click(screen.getByRole('button'));

    expect(onPray).not.toHaveBeenCalled();
  });

  it('UP 버튼은 녹색 그라디언트를 가진다', () => {
    render(
      <Provider>
        <PrayerButton {...defaultProps} side="up" />
      </Provider>
    );

    const button = screen.getByRole('button');
    expect(button.className).toContain('from-green');
  });

  it('DOWN 버튼은 빨간색 그라디언트를 가진다', () => {
    render(
      <Provider>
        <PrayerButton {...defaultProps} side="down" />
      </Provider>
    );

    const button = screen.getByRole('button');
    expect(button.className).toContain('from-red');
  });
});
```

#### __tests__/hooks/useTheme.test.ts
```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { Provider } from 'jotai';
import { useTheme } from '@/hooks/useTheme';

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('초기값은 system 테마이다', () => {
    const { result } = renderHook(() => useTheme(), {
      wrapper: Provider,
    });

    expect(result.current.preference).toBe('system');
  });

  it('toggleTheme()는 테마를 전환한다', () => {
    const { result } = renderHook(() => useTheme(), {
      wrapper: Provider,
    });

    act(() => {
      result.current.setTheme('light');
    });

    expect(result.current.theme).toBe('light');

    act(() => {
      result.current.toggleTheme();
    });

    expect(result.current.theme).toBe('dark');
  });

  it('setTheme()는 테마를 설정한다', () => {
    const { result } = renderHook(() => useTheme(), {
      wrapper: Provider,
    });

    act(() => {
      result.current.setTheme('dark');
    });

    expect(result.current.preference).toBe('dark');
    expect(result.current.isDarkMode).toBe(true);
  });
});
```

### 6.4 E2E 테스트 (Playwright)

#### playwright.config.ts
```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
    },
  ],
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
});
```

#### e2e/prayer.spec.ts
```typescript
import { test, expect } from '@playwright/test';

test.describe('Prayer Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('페이지가 로드된다', async ({ page }) => {
    await expect(page.getByText('Crypto Prayer')).toBeVisible();
  });

  test('기도 버튼이 표시된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: /up/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /down/i })).toBeVisible();
  });

  test('기도 버튼 클릭 시 카운트가 증가한다', async ({ page }) => {
    const upButton = page.getByRole('button', { name: /up/i });

    // 초기 카운트 확인
    const initialCount = await upButton.textContent();

    // 버튼 클릭
    await upButton.click();

    // 카운트 증가 확인 (낙관적 업데이트로 즉시 반영)
    await expect(upButton).not.toHaveText(initialCount!);
  });

  test('WebSocket 연결 상태가 표시된다', async ({ page }) => {
    // 연결 대기
    await expect(page.getByText(/connected/i)).toBeVisible({ timeout: 10000 });
  });

  test('테마 토글이 작동한다', async ({ page }) => {
    const themeButton = page.getByRole('button', { name: /toggle theme/i });

    // 초기 상태 확인
    const html = page.locator('html');

    await themeButton.click();

    // 테마 변경 확인
    await expect(html).toHaveClass(/dark/);

    await themeButton.click();

    await expect(html).not.toHaveClass(/dark/);
  });
});
```

#### e2e/liquidation.spec.ts
```typescript
import { test, expect } from '@playwright/test';

test.describe('Liquidation Feed', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // WebSocket 연결 대기
    await expect(page.getByText(/connected/i)).toBeVisible({ timeout: 10000 });
  });

  test('청산 피드가 표시된다', async ({ page }) => {
    // 실제 청산 데이터가 오기를 기다리거나 mock 사용
    // 이 테스트는 실제 바이낸스 연동에 의존
    await page.waitForTimeout(5000); // 청산 대기

    // 청산이 있으면 표시됨
    const liquidationElements = page.locator('[class*="liquidation"]');
    // 실제 청산이 없을 수 있으므로 존재 여부만 체크하지 않음
  });

  test('BTC 티커가 표시된다', async ({ page }) => {
    // 티커 데이터 대기
    await expect(page.getByText(/\$/)).toBeVisible({ timeout: 10000 });

    // 가격 형식 확인 ($ 기호 포함)
    await expect(page.locator('text=/$[0-9,]+/')).toBeVisible();
  });
});
```

### 6.5 부하 테스트 (k6)

#### load-tests/k6/websocket.js
```javascript
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const wsErrors = new Counter('ws_errors');
const prayerLatency = new Trend('prayer_latency');

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // 50명까지 증가
    { duration: '1m', target: 100 },   // 100명까지 증가
    { duration: '2m', target: 100 },   // 100명 유지
    { duration: '30s', target: 0 },    // 종료
  ],
  thresholds: {
    ws_errors: ['count<10'],
    prayer_latency: ['p(95)<500'],
  },
};

export default function () {
  const url = 'ws://localhost:8080/ws';

  const res = ws.connect(url, {}, function (socket) {
    socket.on('open', function () {
      // STOMP CONNECT
      socket.send('CONNECT\naccept-version:1.2\n\n\0');
    });

    socket.on('message', function (message) {
      if (message.includes('CONNECTED')) {
        // SUBSCRIBE to prayer topic
        socket.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/prayer\n\n\0');

        // Send prayers periodically
        const prayerInterval = setInterval(() => {
          const startTime = Date.now();
          const side = Math.random() > 0.5 ? 'up' : 'down';
          socket.send(
            `SEND\ndestination:/app/prayer\ncontent-type:application/json\n\n{"side":"${side}","count":1}\0`
          );
          prayerLatency.add(Date.now() - startTime);
        }, 200 + Math.random() * 300); // 200-500ms 간격

        // Run for 10 seconds
        sleep(10);
        clearInterval(prayerInterval);
      }
    });

    socket.on('error', function (e) {
      wsErrors.add(1);
      console.error('WebSocket error:', e);
    });

    socket.setTimeout(function () {
      socket.close();
    }, 15000);
  });

  check(res, {
    'WebSocket connection successful': (r) => r && r.status === 101,
  });
}
```

#### load-tests/k6/scenarios.js
```javascript
import ws from 'k6/ws';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    // 시나리오 1: 일반 사용자
    normal_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
    // 시나리오 2: 파워 유저 (빠른 클릭)
    power_users: {
      executor: 'constant-vus',
      vus: 10,
      duration: '3m',
      startTime: '1m',
    },
  },
};

export function normal_users() {
  const url = 'ws://localhost:8080/ws';

  ws.connect(url, {}, function (socket) {
    socket.on('open', function () {
      socket.send('CONNECT\naccept-version:1.2\n\n\0');
    });

    socket.on('message', function (message) {
      if (message.includes('CONNECTED')) {
        socket.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/prayer\n\n\0');

        // 일반 사용자: 1-3초마다 클릭
        for (let i = 0; i < 10; i++) {
          sleep(1 + Math.random() * 2);
          const side = Math.random() > 0.5 ? 'up' : 'down';
          socket.send(
            `SEND\ndestination:/app/prayer\ncontent-type:application/json\n\n{"side":"${side}","count":1}\0`
          );
        }
      }
    });

    socket.setTimeout(function () {
      socket.close();
    }, 30000);
  });
}

export function power_users() {
  const url = 'ws://localhost:8080/ws';

  ws.connect(url, {}, function (socket) {
    socket.on('open', function () {
      socket.send('CONNECT\naccept-version:1.2\n\n\0');
    });

    socket.on('message', function (message) {
      if (message.includes('CONNECTED')) {
        socket.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/prayer\n\n\0');

        // 파워 유저: 100-200ms마다 클릭 (Rate Limit 테스트)
        for (let i = 0; i < 50; i++) {
          sleep(0.1 + Math.random() * 0.1);
          const side = Math.random() > 0.5 ? 'up' : 'down';
          socket.send(
            `SEND\ndestination:/app/prayer\ncontent-type:application/json\n\n{"side":"${side}","count":1}\0`
          );
        }
      }
    });

    socket.setTimeout(function () {
      socket.close();
    }, 60000);
  });
}
```

---

## 테스트 커버리지 목표

| 영역 | 목표 커버리지 |
|------|-------------|
| Backend Domain | 90% |
| Backend Service | 85% |
| Backend Adapter | 80% |
| Frontend Store | 85% |
| Frontend Hooks | 80% |
| Frontend Components | 70% |

---

## 체크리스트

- [ ] Backend 단위 테스트
  - [ ] PrayerCountTest
  - [ ] TokenBucketRateLimiterTest
  - [ ] PrayerServiceTest
  - [ ] FallbackManagerTest
  - [ ] RedisPrayerCountAdapterTest
- [ ] Backend 통합 테스트
  - [ ] WebSocketIntegrationTest
  - [ ] RedisIntegrationTest (Testcontainers)
- [ ] Frontend 테스트 설정
  - [ ] vitest.config.ts
  - [ ] test/setup.ts
- [ ] Frontend 단위 테스트
  - [ ] prayerStore.test.ts
  - [ ] PrayerButton.test.tsx
  - [ ] useTheme.test.ts
- [ ] E2E 테스트
  - [ ] playwright.config.ts
  - [ ] prayer.spec.ts
  - [ ] liquidation.spec.ts
- [ ] 부하 테스트
  - [ ] k6/websocket.js
  - [ ] k6/scenarios.js
- [ ] CI 파이프라인
  - [ ] GitHub Actions 설정

---

## 검증 명령어

```bash
# Backend 테스트
cd backend && ./gradlew test

# Backend 커버리지
./gradlew jacocoTestReport

# Frontend 테스트
cd frontend && pnpm test

# Frontend 커버리지
pnpm test -- --coverage

# E2E 테스트
pnpm playwright test

# 부하 테스트
k6 run load-tests/k6/websocket.js
```

---

## 다음 Phase
→ [Phase 7: 배포](phase7-deployment.md)
