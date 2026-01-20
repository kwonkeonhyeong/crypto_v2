# Phase 4: Frontend Core - 아키텍처 다이어그램

## 1. 컴포넌트 의존성 다이어그램

```mermaid
graph TB
    subgraph App["App.tsx"]
        Provider[Jotai Provider]
        AppContent[AppContent]
    end

    subgraph Layout["Layout Components"]
        LayoutC[Layout]
        Header[Header]
        ThemeToggle[ThemeToggle]
        LanguageToggle[LanguageToggle]
    end

    subgraph UI["UI Components"]
        ToastContainer[ToastContainer]
        Toast[Toast]
    end

    subgraph Hooks["Custom Hooks"]
        useTheme[useTheme]
        usePrayerSocket[usePrayerSocket]
        useToast[useToast]
    end

    subgraph Stores["Jotai Stores"]
        themeStore[themeStore]
        websocketStore[websocketStore]
        prayerStore[prayerStore]
        toastStore[toastStore]
        soundStore[soundStore]
    end

    subgraph Lib["Library"]
        StompClient[StompClient]
        ExponentialBackoff[ExponentialBackoff]
    end

    Provider --> AppContent
    AppContent --> LayoutC
    AppContent --> useTheme
    LayoutC --> Header
    LayoutC --> ToastContainer
    Header --> ThemeToggle
    Header --> LanguageToggle
    Header --> websocketStore
    ThemeToggle --> useTheme
    ToastContainer --> toastStore
    ToastContainer --> Toast
    Toast --> toastStore

    useTheme --> themeStore
    usePrayerSocket --> StompClient
    usePrayerSocket --> websocketStore
    usePrayerSocket --> prayerStore
    usePrayerSocket --> toastStore
    useToast --> toastStore

    StompClient --> ExponentialBackoff
```

## 2. Jotai 스토어 구조

```mermaid
graph LR
    subgraph prayerStore["prayerStore"]
        prayerCountAtom["prayerCountAtom<br/>(server count)"]
        pendingPrayersAtom["pendingPrayersAtom<br/>(optimistic)"]
        localCountAtom["localCountAtom<br/>(derived)"]
        prayerCountAtom --> localCountAtom
        pendingPrayersAtom --> localCountAtom
    end

    subgraph websocketStore["websocketStore"]
        websocketStateAtom["websocketStateAtom"]
        connectionStatusAtom["connectionStatusAtom<br/>(derived)"]
        isConnectedAtom["isConnectedAtom<br/>(derived)"]
        websocketStateAtom --> connectionStatusAtom
        websocketStateAtom --> isConnectedAtom
    end

    subgraph themeStore["themeStore"]
        themePreferenceAtom["themePreferenceAtom<br/>(localStorage)"]
        systemThemeAtom["systemThemeAtom"]
        resolvedThemeAtom["resolvedThemeAtom<br/>(derived)"]
        isDarkModeAtom["isDarkModeAtom<br/>(derived)"]
        themePreferenceAtom --> resolvedThemeAtom
        systemThemeAtom --> resolvedThemeAtom
        resolvedThemeAtom --> isDarkModeAtom
    end

    subgraph toastStore["toastStore"]
        toastsAtom["toastsAtom"]
        addToastAtom["addToastAtom<br/>(write-only)"]
        removeToastAtom["removeToastAtom<br/>(write-only)"]
        addToastAtom --> toastsAtom
        removeToastAtom --> toastsAtom
    end

    subgraph soundStore["soundStore"]
        soundEnabledAtom["soundEnabledAtom<br/>(localStorage)"]
        bgmEnabledAtom["bgmEnabledAtom<br/>(localStorage)"]
    end
```

## 3. WebSocket 연결 시퀀스

```mermaid
sequenceDiagram
    participant App as App
    participant Hook as usePrayerSocket
    participant Client as StompClient
    participant Backoff as ExponentialBackoff
    participant Store as websocketStore
    participant Server as Backend

    App->>Hook: mount
    Hook->>Store: status = 'connecting'
    Hook->>Client: new StompClient()
    Client->>Server: WebSocket connect

    alt Connection Success
        Server-->>Client: CONNECTED
        Client->>Backoff: reset()
        Client->>Store: status = 'connected'
        Client->>Server: SUBSCRIBE /topic/prayer
        Client->>Server: SUBSCRIBE /topic/ticker
        Client->>Server: SUBSCRIBE /topic/liquidation
        Client->>Server: SUBSCRIBE /user/queue/errors
    else Connection Failed
        Server-->>Client: ERROR
        Client->>Backoff: nextDelayMs()
        Client->>Store: status = 'reconnecting'
        Note over Client: Wait for delay
        Client->>Server: WebSocket reconnect
    end
```

## 4. 기도 클릭 시퀀스 (배칭 + 낙관적 업데이트)

```mermaid
sequenceDiagram
    participant User as User
    participant Hook as usePrayerSocket
    participant Prayer as prayerStore
    participant Batch as BatchBuffer
    participant Client as StompClient
    participant Server as Backend

    User->>Hook: pray('up')
    Hook->>Prayer: add to pendingPrayers
    Note over Prayer: Optimistic update<br/>localCount 즉시 반영
    Hook->>Batch: batchRef.up++

    alt No timer running
        Hook->>Hook: setTimeout(500ms)
    end

    Note over User,Hook: ... more clicks within 500ms ...

    User->>Hook: pray('up')
    Hook->>Prayer: add to pendingPrayers
    Hook->>Batch: batchRef.up++

    Note over Batch: Timer fires after 500ms

    Batch->>Client: send(/app/prayer, {side:'up', count:N})
    Client->>Server: STOMP SEND
    Server-->>Client: /topic/prayer (PrayerCount)
    Client->>Prayer: set prayerCountAtom
    Prayer->>Prayer: clear pendingPrayers
    Note over Prayer: Server count replaces<br/>optimistic count
```

## 5. 테마 시스템 흐름

```mermaid
flowchart TD
    A[User Action] --> B{Toggle Theme}
    B -->|Current: system| C[Set to light/dark<br/>based on current]
    B -->|Current: light| D[Set to dark]
    B -->|Current: dark| E[Set to light]

    F[System Theme Change] --> G{preference == system?}
    G -->|Yes| H[Update systemThemeAtom]
    G -->|No| I[Ignore]

    H --> J[resolvedThemeAtom recalculates]
    C --> J
    D --> J
    E --> J

    J --> K[isDarkModeAtom updates]
    K --> L{isDarkMode?}
    L -->|Yes| M[Add 'dark' class to html]
    L -->|No| N[Remove 'dark' class]
```

## 6. 토스트 알림 시퀀스

```mermaid
sequenceDiagram
    participant Caller as Caller
    participant Action as addToastAtom
    participant Store as toastsAtom
    participant Timer as setTimeout
    participant UI as ToastContainer

    Caller->>Action: addToast({message, type, duration})
    Action->>Action: Generate unique ID
    Action->>Store: Push new toast
    Store->>UI: Render toast

    Action->>Timer: setTimeout(duration)

    Note over Timer: Wait for duration

    Timer->>Store: Remove toast by ID
    Store->>UI: Update (toast removed)
```

## 7. Exponential Backoff 알고리즘

```mermaid
flowchart LR
    A[Attempt 0] -->|1s| B[Attempt 1]
    B -->|2s| C[Attempt 2]
    C -->|4s| D[Attempt 3]
    D -->|8s| E[Attempt 4]
    E -->|16s| F[Attempt 5]
    F -->|30s| G[Attempt 6+]

    style G fill:#f9f,stroke:#333

    Note1["Max delay: 30s"]
    Note2["Jitter: +-10%"]
```

## 8. 디렉토리 구조

```mermaid
graph TD
    subgraph Frontend["frontend/src/"]
        Types["types/<br/>- prayer.ts<br/>- websocket.ts<br/>- ticker.ts<br/>- liquidation.ts"]
        Stores["stores/<br/>- prayerStore.ts<br/>- websocketStore.ts<br/>- themeStore.ts<br/>- soundStore.ts<br/>- toastStore.ts"]
        Lib["lib/<br/>- exponentialBackoff.ts<br/>- stomp.ts"]
        Hooks["hooks/<br/>- usePrayerSocket.ts<br/>- useTheme.ts<br/>- useToast.ts"]
        Components["components/<br/>- layout/<br/>- ui/"]
        i18n["i18n/<br/>- locales/ko.json<br/>- locales/en.json"]
    end

    Types --> Stores
    Types --> Hooks
    Stores --> Hooks
    Lib --> Hooks
    Hooks --> Components
    Stores --> Components
```
