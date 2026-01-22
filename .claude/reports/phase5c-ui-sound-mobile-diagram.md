# Phase 5c: UI 사운드 & 모바일 다이어그램

## 1. 사운드 시스템 컴포넌트 관계도

```mermaid
classDiagram
    class SoundManager {
        -sounds: Map~string, Howl~
        -enabled: boolean
        -bgmEnabled: boolean
        -initialized: boolean
        +register(name, config)
        +play(name)
        +playBgm()
        +stopBgm()
        +setEnabled(enabled)
        +setBgmEnabled(enabled)
    }

    class soundEnabledAtom {
        +atomWithStorage~boolean~
    }

    class bgmEnabledAtom {
        +atomWithStorage~boolean~
    }

    class useSound {
        +soundEnabled
        +bgmEnabled
        +toggleSound()
        +toggleBgm()
    }

    class useSoundEffect {
        +playClick()
        +playLiquidation()
        +playLargeLiquidation()
    }

    class SoundControl {
        +render()
    }

    class SoundToggle {
        +enabled: boolean
        +onToggle()
    }

    class BgmToggle {
        +enabled: boolean
        +onToggle()
    }

    SoundManager <-- useSound : syncs
    SoundManager <-- useSoundEffect : plays
    soundEnabledAtom <-- useSound : reads/writes
    bgmEnabledAtom <-- useSound : reads/writes
    soundEnabledAtom <-- useSoundEffect : reads
    useSound <-- SoundControl : uses
    SoundToggle <-- SoundControl : contains
    BgmToggle <-- SoundControl : contains
```

## 2. 모바일 레이아웃 컴포넌트 관계도

```mermaid
classDiagram
    class useIsMobile {
        +isMobile: boolean
    }

    class MobileLayout {
        +children: ReactNode
        +onPray(side)
    }

    class MobilePrayerButtons {
        +onPray(side)
    }

    class PrayerPage {
        +pray(side)
    }

    class PrayerButtonPair {
        +onPray(side)
    }

    useIsMobile <-- MobileLayout : uses
    useIsMobile <-- PrayerPage : uses
    MobilePrayerButtons <-- MobileLayout : renders (mobile)
    PrayerPage --> MobileLayout : wraps content
    PrayerPage --> PrayerButtonPair : renders (desktop)
```

## 3. 사운드 재생 시퀀스 다이어그램

### 3.1 클릭 사운드 재생

```mermaid
sequenceDiagram
    participant User
    participant PrayerButtonPair
    participant useSoundEffect
    participant SoundManager
    participant Howler

    User->>PrayerButtonPair: Click button
    PrayerButtonPair->>useSoundEffect: playClick()
    useSoundEffect->>useSoundEffect: Check soundEnabled
    alt Sound Enabled
        useSoundEffect->>SoundManager: play('click')
        SoundManager->>SoundManager: Check enabled
        SoundManager->>Howler: howl.play()
        Howler-->>User: Sound plays
    end
```

### 3.2 청산 사운드 재생

```mermaid
sequenceDiagram
    participant WebSocket
    participant PrayerPage
    participant useSoundEffect
    participant SoundManager
    participant Howler

    WebSocket->>PrayerPage: onLiquidation(data)
    PrayerPage->>PrayerPage: Check usdValue
    alt >= $100,000
        PrayerPage->>useSoundEffect: playLargeLiquidation()
    else < $100,000
        PrayerPage->>useSoundEffect: playLiquidation()
    end
    useSoundEffect->>SoundManager: play(soundName)
    SoundManager->>Howler: howl.play()
    Howler-->>PrayerPage: Sound plays
```

## 4. 모바일 레이아웃 렌더링 플로우

```mermaid
flowchart TD
    A[PrayerPage] --> B{useIsMobile?}
    B -->|true| C[MobileLayout]
    B -->|false| D[Desktop Layout]

    C --> E[Content Area<br/>pb-33vh]
    C --> F[MobilePrayerButtons<br/>Fixed Bottom 1/3]

    D --> G[Content Area<br/>pb-8]
    D --> H[PrayerButtonPair<br/>Inline]

    E --> I[ScreenShake]
    I --> J[LiquidationFeed]
    I --> K[TickerDisplay]
    I --> L[CounterDisplay]
    I --> M[GaugeBar]

    G --> I
```

## 5. 사운드 초기화 플로우

```mermaid
flowchart TD
    A[App Start] --> B[useSound hook mount]
    B --> C{isInitialized?}
    C -->|false| D[initializeSounds]
    C -->|true| E[Skip]

    D --> F[Register BGM<br/>loop: true, html5: true]
    D --> G[Register Click<br/>preload: true]
    D --> H[Register Liquidation<br/>preload: true]
    D --> I[Register Large Liquidation<br/>preload: true]

    F --> J[setInitialized true]
    G --> J
    H --> J
    I --> J

    J --> K[Sync with Jotai atoms]
    K --> L[setEnabled from soundEnabledAtom]
    K --> M[setBgmEnabled from bgmEnabledAtom]
```

## 6. 상태 관리 데이터 흐름

```mermaid
flowchart LR
    subgraph LocalStorage
        LS1[soundEnabled]
        LS2[bgmEnabled]
    end

    subgraph Jotai
        A1[soundEnabledAtom]
        A2[bgmEnabledAtom]
    end

    subgraph SoundManager
        SM[singleton instance]
    end

    subgraph Hooks
        H1[useSound]
        H2[useSoundEffect]
    end

    subgraph Components
        C1[SoundControl]
        C2[PrayerButtonPair]
        C3[PrayerPage]
    end

    LS1 <-->|atomWithStorage| A1
    LS2 <-->|atomWithStorage| A2

    A1 --> H1
    A2 --> H1
    A1 --> H2

    H1 -->|sync| SM
    H2 -->|play| SM

    H1 --> C1
    H2 --> C2
    H2 --> C3
```

## 7. CSS Safe Area 적용

```mermaid
flowchart TD
    A[index.css] --> B[CSS Variables]
    B --> C[--safe-area-inset-top]
    B --> D[--safe-area-inset-bottom]

    C --> E[.pt-safe class]
    D --> F[.pb-safe class]

    F --> G[MobilePrayerButtons]

    subgraph iOS Device
        H[env safe-area-inset-*]
        H --> C
        H --> D
    end
```
