# Phase 4: Frontend 코어

## 목표
React 앱의 핵심 인프라(상태 관리, WebSocket, i18n, 테마)를 구축한다.

## 선행 의존성
- Phase 1: 프로젝트 셋업 완료
- Phase 2b: Backend WebSocket 완료 (API 스펙 확정)

## 범위
- Jotai 상태 관리 스토어
- usePrayerSocket 훅 (STOMP client)
- Exponential Backoff 재연결
- i18n 다국어 지원
- 다크모드 (시스템 테마 감지)
- 기본 레이아웃 컴포넌트

---

## 디렉토리 구조

```
frontend/src/
├── components/
│   ├── layout/
│   │   ├── Layout.tsx
│   │   ├── Header.tsx
│   │   └── ThemeToggle.tsx
│   └── ui/
│       ├── Toast.tsx
│       └── ToastContainer.tsx
├── hooks/
│   ├── usePrayerSocket.ts
│   ├── useTheme.ts
│   ├── useToast.ts
│   └── useExponentialBackoff.ts
├── stores/
│   ├── prayerStore.ts
│   ├── websocketStore.ts
│   ├── themeStore.ts
│   ├── soundStore.ts
│   └── toastStore.ts
├── types/
│   ├── prayer.ts
│   ├── websocket.ts
│   ├── ticker.ts
│   └── liquidation.ts
├── i18n/
│   ├── index.ts
│   ├── locales/
│   │   ├── ko.json
│   │   └── en.json
│   └── LanguageDetector.ts
├── lib/
│   ├── stomp.ts
│   └── exponentialBackoff.ts
├── App.tsx
├── main.tsx
└── index.css
```

---

## 상세 구현 단계

### 4.1 타입 정의

#### types/prayer.ts
```typescript
export type Side = 'up' | 'down';

export interface PrayerCount {
  upCount: number;
  downCount: number;
  upRpm: number;
  downRpm: number;
  upRatio: number;
  downRatio: number;
  timestamp: number;
}

export interface PrayerRequest {
  side: Side;
  count: number;
}
```

#### types/websocket.ts
```typescript
export type ConnectionStatus =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'reconnecting';

export interface WebSocketState {
  status: ConnectionStatus;
  error: string | null;
  reconnectAttempt: number;
}

export interface StompMessage<T = unknown> {
  type: string;
  payload: T;
  timestamp: number;
}
```

#### types/ticker.ts
```typescript
export interface Ticker {
  symbol: string;
  price: number;
  priceChange24h: number;
  timestamp: number;
}
```

#### types/liquidation.ts
```typescript
export type LiquidationSide = 'LONG' | 'SHORT';

export interface Liquidation {
  id: string;
  symbol: string;
  side: LiquidationSide;
  quantity: number;
  price: number;
  usdValue: number;
  isLarge: boolean;
  timestamp: number;
}
```

### 4.2 Jotai 스토어

#### stores/prayerStore.ts
```typescript
import { atom } from 'jotai';
import type { PrayerCount, Side } from '@/types/prayer';

// 기도 카운트 상태
export const prayerCountAtom = atom<PrayerCount>({
  upCount: 0,
  downCount: 0,
  upRpm: 0,
  downRpm: 0,
  upRatio: 0.5,
  downRatio: 0.5,
  timestamp: Date.now(),
});

// 낙관적 업데이트용 로컬 카운트 (배칭)
interface PendingPrayer {
  side: Side;
  count: number;
  timestamp: number;
}

export const pendingPrayersAtom = atom<PendingPrayer[]>([]);

// 로컬 카운트 (서버 카운트 + 펜딩)
export const localCountAtom = atom((get) => {
  const serverCount = get(prayerCountAtom);
  const pending = get(pendingPrayersAtom);

  const pendingUp = pending
    .filter((p) => p.side === 'up')
    .reduce((sum, p) => sum + p.count, 0);
  const pendingDown = pending
    .filter((p) => p.side === 'down')
    .reduce((sum, p) => sum + p.count, 0);

  const upCount = serverCount.upCount + pendingUp;
  const downCount = serverCount.downCount + pendingDown;
  const total = upCount + downCount;

  return {
    ...serverCount,
    upCount,
    downCount,
    upRatio: total > 0 ? upCount / total : 0.5,
    downRatio: total > 0 ? downCount / total : 0.5,
  };
});
```

#### stores/websocketStore.ts
```typescript
import { atom } from 'jotai';
import type { ConnectionStatus, WebSocketState } from '@/types/websocket';

export const websocketStateAtom = atom<WebSocketState>({
  status: 'disconnected',
  error: null,
  reconnectAttempt: 0,
});

export const connectionStatusAtom = atom(
  (get) => get(websocketStateAtom).status
);

export const isConnectedAtom = atom(
  (get) => get(websocketStateAtom).status === 'connected'
);
```

#### stores/themeStore.ts
```typescript
import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

export type Theme = 'light' | 'dark' | 'system';

// 사용자 선택 테마 (localStorage 저장)
export const themePreferenceAtom = atomWithStorage<Theme>('theme', 'system');

// 실제 적용되는 테마
export const resolvedThemeAtom = atom((get) => {
  const preference = get(themePreferenceAtom);

  if (preference !== 'system') {
    return preference;
  }

  // 시스템 테마 감지
  if (typeof window !== 'undefined') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  }

  return 'light';
});

// 다크모드 여부
export const isDarkModeAtom = atom((get) => get(resolvedThemeAtom) === 'dark');
```

#### stores/soundStore.ts
```typescript
import { atomWithStorage } from 'jotai/utils';

// 사운드 활성화 여부 (localStorage 저장)
export const soundEnabledAtom = atomWithStorage('soundEnabled', true);

// BGM 활성화 여부
export const bgmEnabledAtom = atomWithStorage('bgmEnabled', false);
```

#### stores/toastStore.ts
```typescript
import { atom } from 'jotai';

export interface Toast {
  id: string;
  message: string;
  type: 'info' | 'success' | 'error' | 'warning';
  duration?: number;
}

export const toastsAtom = atom<Toast[]>([]);

// 토스트 추가 액션
export const addToastAtom = atom(
  null,
  (get, set, toast: Omit<Toast, 'id'>) => {
    const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const newToast: Toast = { ...toast, id };

    set(toastsAtom, [...get(toastsAtom), newToast]);

    // 자동 제거
    const duration = toast.duration ?? 3000;
    setTimeout(() => {
      set(toastsAtom, (prev) => prev.filter((t) => t.id !== id));
    }, duration);
  }
);

// 토스트 제거 액션
export const removeToastAtom = atom(null, (get, set, id: string) => {
  set(toastsAtom, (prev) => prev.filter((t) => t.id !== id));
});
```

### 4.3 Exponential Backoff

#### lib/exponentialBackoff.ts
```typescript
export interface BackoffConfig {
  initialDelayMs: number;
  maxDelayMs: number;
  multiplier: number;
  jitterFactor: number;
}

const defaultConfig: BackoffConfig = {
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  multiplier: 2,
  jitterFactor: 0.1,
};

export class ExponentialBackoff {
  private config: BackoffConfig;
  private attempt = 0;

  constructor(config: Partial<BackoffConfig> = {}) {
    this.config = { ...defaultConfig, ...config };
  }

  nextDelayMs(): number {
    const { initialDelayMs, maxDelayMs, multiplier, jitterFactor } = this.config;

    let delay = initialDelayMs * Math.pow(multiplier, this.attempt);
    delay = Math.min(delay, maxDelayMs);

    // Jitter 추가 (±10%)
    const jitter = delay * jitterFactor * (Math.random() * 2 - 1);
    delay = Math.max(0, delay + jitter);

    this.attempt++;
    return delay;
  }

  reset(): void {
    this.attempt = 0;
  }

  getAttempt(): number {
    return this.attempt;
  }
}
```

### 4.4 STOMP 클라이언트

#### lib/stomp.ts
```typescript
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { ExponentialBackoff } from './exponentialBackoff';

export interface StompConfig {
  brokerURL: string;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: string) => void;
  onReconnecting?: (attempt: number) => void;
}

export class StompClient {
  private client: Client;
  private backoff: ExponentialBackoff;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private config: StompConfig;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(config: StompConfig) {
    this.config = config;
    this.backoff = new ExponentialBackoff();

    this.client = new Client({
      brokerURL: config.brokerURL,
      reconnectDelay: 0, // 수동 재연결 관리
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => this.handleConnect(),
      onDisconnect: () => this.handleDisconnect(),
      onStompError: (frame) => this.handleError(frame.body),
      onWebSocketError: () => this.handleError('WebSocket error'),
    });
  }

  connect(): void {
    if (!this.client.active) {
      this.client.activate();
    }
  }

  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.client.deactivate();
  }

  subscribe(
    destination: string,
    callback: (message: IMessage) => void
  ): () => void {
    if (this.subscriptions.has(destination)) {
      this.subscriptions.get(destination)?.unsubscribe();
    }

    const subscription = this.client.subscribe(destination, callback);
    this.subscriptions.set(destination, subscription);

    return () => {
      subscription.unsubscribe();
      this.subscriptions.delete(destination);
    };
  }

  send(destination: string, body: object): void {
    this.client.publish({
      destination,
      body: JSON.stringify(body),
    });
  }

  private handleConnect(): void {
    this.backoff.reset();
    this.config.onConnect?.();
  }

  private handleDisconnect(): void {
    this.config.onDisconnect?.();
    this.scheduleReconnect();
  }

  private handleError(error: string): void {
    this.config.onError?.(error);
    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;

    const delay = this.backoff.nextDelayMs();
    this.config.onReconnecting?.(this.backoff.getAttempt());

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  isConnected(): boolean {
    return this.client.connected;
  }
}
```

### 4.5 WebSocket 훅

#### hooks/usePrayerSocket.ts
```typescript
import { useEffect, useRef, useCallback } from 'react';
import { useSetAtom, useAtomValue } from 'jotai';
import { StompClient } from '@/lib/stomp';
import { prayerCountAtom, pendingPrayersAtom } from '@/stores/prayerStore';
import { websocketStateAtom } from '@/stores/websocketStore';
import { addToastAtom } from '@/stores/toastStore';
import type { PrayerCount, PrayerRequest, Side } from '@/types/prayer';
import type { Ticker } from '@/types/ticker';
import type { Liquidation } from '@/types/liquidation';

const BATCH_INTERVAL = 500; // 500ms 배칭

interface UsePrayerSocketOptions {
  onTicker?: (ticker: Ticker) => void;
  onLiquidation?: (liquidation: Liquidation) => void;
}

export function usePrayerSocket(options: UsePrayerSocketOptions = {}) {
  const clientRef = useRef<StompClient | null>(null);
  const batchRef = useRef<{ up: number; down: number }>({ up: 0, down: 0 });
  const batchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const setPrayerCount = useSetAtom(prayerCountAtom);
  const setPendingPrayers = useSetAtom(pendingPrayersAtom);
  const setWebsocketState = useSetAtom(websocketStateAtom);
  const addToast = useSetAtom(addToastAtom);

  // 배치 전송
  const flushBatch = useCallback(() => {
    const client = clientRef.current;
    if (!client?.isConnected()) return;

    const { up, down } = batchRef.current;

    if (up > 0) {
      client.send('/app/prayer', { side: 'up', count: up });
    }
    if (down > 0) {
      client.send('/app/prayer', { side: 'down', count: down });
    }

    batchRef.current = { up: 0, down: 0 };
    batchTimerRef.current = null;
  }, []);

  // 기도 전송 (배칭)
  const pray = useCallback(
    (side: Side) => {
      // 낙관적 업데이트
      setPendingPrayers((prev) => [
        ...prev,
        { side, count: 1, timestamp: Date.now() },
      ]);

      // 배치에 추가
      batchRef.current[side]++;

      // 배치 타이머 설정
      if (!batchTimerRef.current) {
        batchTimerRef.current = setTimeout(flushBatch, BATCH_INTERVAL);
      }
    },
    [flushBatch, setPendingPrayers]
  );

  // WebSocket 연결
  useEffect(() => {
    const wsUrl =
      import.meta.env.VITE_WS_URL ||
      `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;

    const client = new StompClient({
      brokerURL: wsUrl,
      onConnect: () => {
        setWebsocketState({ status: 'connected', error: null, reconnectAttempt: 0 });
      },
      onDisconnect: () => {
        setWebsocketState((prev) => ({ ...prev, status: 'disconnected' }));
      },
      onError: (error) => {
        setWebsocketState((prev) => ({ ...prev, error }));
      },
      onReconnecting: (attempt) => {
        setWebsocketState((prev) => ({
          ...prev,
          status: 'reconnecting',
          reconnectAttempt: attempt,
        }));
      },
    });

    clientRef.current = client;

    setWebsocketState({ status: 'connecting', error: null, reconnectAttempt: 0 });
    client.connect();

    // 구독
    const unsubPrayer = client.subscribe('/topic/prayer', (message) => {
      const data: PrayerCount = JSON.parse(message.body);
      setPrayerCount(data);

      // 서버 응답 받으면 펜딩 클리어
      setPendingPrayers([]);
    });

    const unsubTicker = client.subscribe('/topic/ticker', (message) => {
      const data: Ticker = JSON.parse(message.body);
      options.onTicker?.(data);
    });

    const unsubLiquidation = client.subscribe('/topic/liquidation', (message) => {
      const data = JSON.parse(message.body);
      const liquidation: Liquidation = {
        ...data,
        id: `${data.timestamp}-${Math.random().toString(36).substr(2, 9)}`,
      };
      options.onLiquidation?.(liquidation);
    });

    // 개인 에러 큐 구독
    const unsubErrors = client.subscribe('/user/queue/errors', (message) => {
      const error = JSON.parse(message.body);
      if (error.code === 'RATE_LIMIT_EXCEEDED') {
        addToast({
          message: 'Too fast! Please slow down.',
          type: 'warning',
        });
        // 펜딩 롤백
        setPendingPrayers([]);
      }
    });

    return () => {
      unsubPrayer();
      unsubTicker();
      unsubLiquidation();
      unsubErrors();
      client.disconnect();

      if (batchTimerRef.current) {
        clearTimeout(batchTimerRef.current);
      }
    };
  }, [options.onTicker, options.onLiquidation, setPrayerCount, setPendingPrayers, setWebsocketState, addToast]);

  return { pray };
}
```

### 4.6 테마 훅

#### hooks/useTheme.ts
```typescript
import { useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
  themePreferenceAtom,
  resolvedThemeAtom,
  isDarkModeAtom,
  type Theme,
} from '@/stores/themeStore';

export function useTheme() {
  const [preference, setPreference] = useAtom(themePreferenceAtom);
  const resolvedTheme = useAtomValue(resolvedThemeAtom);
  const isDarkMode = useAtomValue(isDarkModeAtom);

  // DOM에 테마 클래스 적용
  useEffect(() => {
    const root = document.documentElement;

    if (isDarkMode) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [isDarkMode]);

  // 시스템 테마 변경 감지
  useEffect(() => {
    if (preference !== 'system') return;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => {
      // 강제 리렌더링 트리거
      setPreference('system');
    };

    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, [preference, setPreference]);

  const setTheme = (theme: Theme) => {
    setPreference(theme);
  };

  const toggleTheme = () => {
    if (preference === 'system') {
      setPreference(isDarkMode ? 'light' : 'dark');
    } else {
      setPreference(preference === 'dark' ? 'light' : 'dark');
    }
  };

  return {
    theme: resolvedTheme,
    preference,
    isDarkMode,
    setTheme,
    toggleTheme,
  };
}
```

### 4.7 i18n 설정

#### i18n/index.ts
```typescript
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import ko from './locales/ko.json';
import en from './locales/en.json';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      ko: { translation: ko },
      en: { translation: en },
    },
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['navigator', 'localStorage', 'htmlTag'],
      caches: ['localStorage'],
    },
  });

export default i18n;
```

#### i18n/locales/ko.json
```json
{
  "header": {
    "title": "크립토 기도",
    "subtitle": "BTC의 방향을 기도하세요"
  },
  "prayer": {
    "up": "상승 기도",
    "down": "하락 기도",
    "rpm": "분당 기도",
    "total": "총 기도 수"
  },
  "ticker": {
    "price": "현재가",
    "change24h": "24시간 변동"
  },
  "liquidation": {
    "long": "롱 청산",
    "short": "숏 청산",
    "large": "대형 청산!"
  },
  "settings": {
    "theme": "테마",
    "sound": "사운드",
    "language": "언어"
  },
  "connection": {
    "connected": "연결됨",
    "connecting": "연결 중...",
    "reconnecting": "재연결 중... (시도 {{attempt}})",
    "disconnected": "연결 끊김"
  },
  "errors": {
    "rateLimitExceeded": "너무 빨라요! 천천히 눌러주세요."
  }
}
```

#### i18n/locales/en.json
```json
{
  "header": {
    "title": "Crypto Prayer",
    "subtitle": "Pray for BTC direction"
  },
  "prayer": {
    "up": "Pray UP",
    "down": "Pray DOWN",
    "rpm": "Prayers/min",
    "total": "Total Prayers"
  },
  "ticker": {
    "price": "Price",
    "change24h": "24h Change"
  },
  "liquidation": {
    "long": "Long Liquidation",
    "short": "Short Liquidation",
    "large": "LARGE LIQUIDATION!"
  },
  "settings": {
    "theme": "Theme",
    "sound": "Sound",
    "language": "Language"
  },
  "connection": {
    "connected": "Connected",
    "connecting": "Connecting...",
    "reconnecting": "Reconnecting... (attempt {{attempt}})",
    "disconnected": "Disconnected"
  },
  "errors": {
    "rateLimitExceeded": "Too fast! Please slow down."
  }
}
```

### 4.8 레이아웃 컴포넌트

#### components/layout/Layout.tsx
```typescript
import { ReactNode } from 'react';
import { Header } from './Header';
import { ToastContainer } from '../ui/ToastContainer';

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  return (
    <div className="min-h-screen bg-white dark:bg-gray-900 transition-colors">
      <Header />
      <main className="relative">{children}</main>
      <ToastContainer />
    </div>
  );
}
```

#### components/layout/Header.tsx
```typescript
import { useTranslation } from 'react-i18next';
import { useAtomValue } from 'jotai';
import { connectionStatusAtom } from '@/stores/websocketStore';
import { ThemeToggle } from './ThemeToggle';

export function Header() {
  const { t } = useTranslation();
  const status = useAtomValue(connectionStatusAtom);

  const statusColor = {
    connected: 'bg-green-500',
    connecting: 'bg-yellow-500',
    reconnecting: 'bg-yellow-500',
    disconnected: 'bg-red-500',
  }[status];

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm border-b border-gray-200 dark:border-gray-800">
      <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">
            {t('header.title')}
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {t('header.subtitle')}
          </p>
        </div>

        <div className="flex items-center gap-4">
          {/* 연결 상태 인디케이터 */}
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${statusColor}`} />
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {t(`connection.${status}`)}
            </span>
          </div>

          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
```

#### components/layout/ThemeToggle.tsx
```typescript
import { useTheme } from '@/hooks/useTheme';

export function ThemeToggle() {
  const { isDarkMode, toggleTheme } = useTheme();

  return (
    <button
      onClick={toggleTheme}
      className="p-2 rounded-lg bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
      aria-label="Toggle theme"
    >
      {isDarkMode ? (
        <SunIcon className="w-5 h-5 text-yellow-500" />
      ) : (
        <MoonIcon className="w-5 h-5 text-gray-600" />
      )}
    </button>
  );
}

function SunIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  );
}

function MoonIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
    </svg>
  );
}
```

### 4.9 Toast 컴포넌트

#### components/ui/ToastContainer.tsx
```typescript
import { useAtomValue } from 'jotai';
import { toastsAtom } from '@/stores/toastStore';
import { Toast } from './Toast';

export function ToastContainer() {
  const toasts = useAtomValue(toastsAtom);

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <Toast key={toast.id} toast={toast} />
      ))}
    </div>
  );
}
```

#### components/ui/Toast.tsx
```typescript
import { useSetAtom } from 'jotai';
import { removeToastAtom, type Toast as ToastType } from '@/stores/toastStore';
import { clsx } from 'clsx';

interface ToastProps {
  toast: ToastType;
}

export function Toast({ toast }: ToastProps) {
  const removeToast = useSetAtom(removeToastAtom);

  const bgColor = {
    info: 'bg-blue-500',
    success: 'bg-green-500',
    error: 'bg-red-500',
    warning: 'bg-yellow-500',
  }[toast.type];

  return (
    <div
      className={clsx(
        'px-4 py-3 rounded-lg text-white shadow-lg',
        'animate-slide-up',
        bgColor
      )}
    >
      <div className="flex items-center gap-2">
        <span>{toast.message}</span>
        <button
          onClick={() => removeToast(toast.id)}
          className="ml-2 opacity-70 hover:opacity-100"
        >
          &times;
        </button>
      </div>
    </div>
  );
}
```

### 4.10 App.tsx 업데이트

#### App.tsx
```typescript
import { Provider } from 'jotai';
import { Layout } from '@/components/layout/Layout';
import './i18n';

function App() {
  return (
    <Provider>
      <Layout>
        <div className="pt-20 min-h-screen flex items-center justify-center">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white">
            Crypto Prayer
          </h1>
          {/* Phase 5에서 UI 컴포넌트 추가 */}
        </div>
      </Layout>
    </Provider>
  );
}

export default App;
```

---

## 체크리스트

- [ ] 타입 정의
  - [ ] prayer.ts
  - [ ] websocket.ts
  - [ ] ticker.ts
  - [ ] liquidation.ts
- [ ] Jotai 스토어
  - [ ] prayerStore (카운트, 펜딩, 낙관적 업데이트)
  - [ ] websocketStore (연결 상태)
  - [ ] themeStore (테마 + 시스템 감지)
  - [ ] soundStore (사운드 설정)
  - [ ] toastStore (알림)
- [ ] 유틸리티
  - [ ] ExponentialBackoff
  - [ ] StompClient
- [ ] 훅
  - [ ] usePrayerSocket (배칭, 재연결)
  - [ ] useTheme (시스템 테마 감지)
- [ ] i18n
  - [ ] 한국어 번역
  - [ ] 영어 번역
  - [ ] 브라우저 언어 감지
- [ ] 레이아웃 컴포넌트
  - [ ] Layout
  - [ ] Header (연결 상태 표시)
  - [ ] ThemeToggle
- [ ] Toast 시스템
  - [ ] ToastContainer
  - [ ] Toast
- [ ] App.tsx 통합

---

## 검증 명령어

```bash
# 개발 서버 실행
cd frontend && pnpm dev

# 타입 체크
pnpm tsc --noEmit

# 테스트
pnpm test

# 빌드
pnpm build
```

---

## 다음 Phase
→ [Phase 5a: 기도 버튼 & 게이지](phase5a-ui-buttons-gauge.md)
