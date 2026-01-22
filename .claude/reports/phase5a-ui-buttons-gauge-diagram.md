# Phase 5a: UI 기도 버튼 & 게이지 다이어그램

## 1. 컴포넌트 구조도

```mermaid
graph TB
    subgraph App["App.tsx"]
        Layout["Layout"]
        PrayerPage["PrayerPage"]
    end

    subgraph PrayerPage["PrayerPage.tsx"]
        PC["ParticleContainer"]
        CD["CounterDisplay"]
        RI["RpmIndicator"]
        GB["GaugeBar"]
        PBP["PrayerButtonPair"]
    end

    subgraph ParticleSystem["Particle System"]
        PC --> NP["NumberParticle"]
        NP --> FM1["Framer Motion Animation"]
    end

    subgraph ButtonSystem["Button System"]
        PBP --> PB1["PrayerButton (Up)"]
        PBP --> PB2["PrayerButton (Down)"]
        PB1 --> FM2["Framer Motion Animation"]
        PB2 --> FM3["Framer Motion Animation"]
    end

    subgraph GaugeSystem["Gauge System"]
        GB --> FM4["Framer Motion Animation"]
        RI --> FM5["Framer Motion Animation"]
    end

    subgraph CounterSystem["Counter System"]
        CD --> AN1["AnimatedNumber (Total)"]
        CD --> AN2["AnimatedNumber (Up)"]
        CD --> AN3["AnimatedNumber (Down)"]
        AN1 --> FM6["Framer Motion Animation"]
        AN2 --> FM7["Framer Motion Animation"]
        AN3 --> FM8["Framer Motion Animation"]
    end

    Layout --> PrayerPage
```

## 2. 상태 관리 흐름

```mermaid
graph LR
    subgraph Jotai["Jotai Store"]
        PA["particlesAtom"]
        PCA["prayerCountAtom"]
        PPA["pendingPrayersAtom"]
        LCA["localCountAtom"]
        WS["isConnectedAtom"]
    end

    subgraph Hooks["Hooks"]
        UP["useParticles"]
        UPS["usePrayerSocket"]
    end

    subgraph Components["Components"]
        PBP["PrayerButtonPair"]
        PC["ParticleContainer"]
        GB["GaugeBar"]
        CD["CounterDisplay"]
    end

    UP --> PA
    UPS --> PCA
    UPS --> PPA

    PCA --> LCA
    PPA --> LCA

    PA --> PC
    LCA --> GB
    LCA --> CD
    WS --> PBP
```

## 3. 사용자 클릭 시퀀스

```mermaid
sequenceDiagram
    participant User
    participant PBP as PrayerButtonPair
    participant PB as PrayerButton
    participant UP as useParticles
    participant PS as particleStore
    participant PC as ParticleContainer
    participant NP as NumberParticle
    participant UPS as usePrayerSocket
    participant PP as prayerStore

    User->>PB: Click
    PB->>PB: Pulse Animation
    PB->>PBP: onPray(side, event)

    par Particle Effect
        PBP->>UP: spawnParticle(side, x, y)
        UP->>PS: addParticle()
        PS->>PC: particles updated
        PC->>NP: render particle
        NP->>NP: Float-up animation (0.8s)
        Note over PS: setTimeout 1s
        PS->>PS: removeParticle()
    and Optimistic Update
        PBP->>UPS: pray(side)
        UPS->>PP: pendingPrayers += 1
        PP->>PP: localCount = server + pending
    end
```

## 4. 파티클 라이프사이클

```mermaid
stateDiagram-v2
    [*] --> Created: Click Event
    Created --> Animating: Render
    Animating --> Fading: 0.8s elapsed
    Fading --> Removing: Animation complete
    Removing --> [*]: 1s timeout

    note right of Created
        id: timestamp + random
        x, y: click position + offset
        value: 1
        side: up | down
    end note

    note right of Animating
        scale: 0.5 → 1.5
        opacity: 1 → 0
        y: 0 → -80
    end note
```

## 5. 게이지 바 비율 계산

```mermaid
flowchart TD
    SC["Server Count (prayerCountAtom)"]
    PP["Pending Prayers (pendingPrayersAtom)"]

    SC --> LC["Local Count (localCountAtom)"]
    PP --> LC

    LC --> UP["upCount = server.up + pending.up"]
    LC --> DOWN["downCount = server.down + pending.down"]
    LC --> TOTAL["total = upCount + downCount"]

    UP --> RATIO_UP["upRatio = upCount / total"]
    DOWN --> RATIO_DOWN["downRatio = downCount / total"]
    TOTAL --> RATIO_UP
    TOTAL --> RATIO_DOWN

    RATIO_UP --> GB["GaugeBar Width %"]
    RATIO_DOWN --> GB
```

## 6. 컴포넌트 의존성

```mermaid
graph TD
    subgraph Stores["stores/"]
        particleStore["particleStore.ts"]
        prayerStore["prayerStore.ts"]
        websocketStore["websocketStore.ts"]
    end

    subgraph Hooks["hooks/"]
        useParticles["useParticles.ts"]
        usePrayerSocket["usePrayerSocket.ts"]
    end

    subgraph Prayer["components/prayer/"]
        NumberParticle["NumberParticle.tsx"]
        ParticleContainer["ParticleContainer.tsx"]
        PrayerButton["PrayerButton.tsx"]
        PrayerButtonPair["PrayerButtonPair.tsx"]
    end

    subgraph Gauge["components/gauge/"]
        GaugeBar["GaugeBar.tsx"]
        RpmIndicator["RpmIndicator.tsx"]
    end

    subgraph Counter["components/counter/"]
        AnimatedNumber["AnimatedNumber.tsx"]
        CounterDisplay["CounterDisplay.tsx"]
    end

    subgraph Pages["pages/"]
        PrayerPage["PrayerPage.tsx"]
    end

    particleStore --> useParticles
    particleStore --> ParticleContainer

    useParticles --> PrayerButtonPair

    ParticleContainer --> NumberParticle

    prayerStore --> PrayerButtonPair
    prayerStore --> GaugeBar
    prayerStore --> RpmIndicator
    prayerStore --> CounterDisplay

    websocketStore --> PrayerButtonPair

    usePrayerSocket --> PrayerPage

    PrayerButtonPair --> PrayerButton

    CounterDisplay --> AnimatedNumber

    PrayerPage --> ParticleContainer
    PrayerPage --> CounterDisplay
    PrayerPage --> RpmIndicator
    PrayerPage --> GaugeBar
    PrayerPage --> PrayerButtonPair
```

## 7. 반응형 레이아웃

```mermaid
graph TD
    subgraph Mobile["Mobile (< 768px)"]
        M_BTN["Button: h-32"]
        M_TXT["Text: text-lg"]
        M_GAP["Gap: gap-4"]
    end

    subgraph Tablet["Tablet (768px - 1024px)"]
        T_BTN["Button: h-40"]
        T_TXT["Text: text-xl"]
        T_GAP["Gap: gap-6"]
    end

    subgraph Desktop["Desktop (> 1024px)"]
        D_BTN["Button: h-48"]
        D_TXT["Text: text-xl"]
        D_GAP["Gap: gap-8"]
    end

    CSS["Tailwind CSS Breakpoints"]
    CSS --> Mobile
    CSS --> Tablet
    CSS --> Desktop
```

## 8. 색상 스키마

```mermaid
graph LR
    subgraph Up["Up (상승)"]
        U_BTN["Button: red-400 → red-600"]
        U_TXT["Text: red-500"]
        U_GAUGE["Gauge: red-400 → red-500"]
    end

    subgraph Down["Down (하락)"]
        D_BTN["Button: blue-400 → blue-600"]
        D_TXT["Text: blue-500"]
        D_GAUGE["Gauge: blue-400 → blue-500"]
    end

    subgraph Theme["Theme"]
        Light["Light Mode: bg-white"]
        Dark["Dark Mode: bg-gray-900"]
    end
```

## 9. 애니메이션 타이밍

```mermaid
gantt
    title Animation Timeline
    dateFormat X
    axisFormat %L

    section Button Click
    Pulse Effect           :0, 150

    section Particle
    Scale Up (0.5→1.5)     :0, 800
    Fade Out (1→0)         :0, 800
    Float Up (0→-80)       :0, 800
    Remove from DOM        :1000, 1000

    section Gauge
    Width Transition       :0, 300

    section Counter
    Number Slide           :0, 200
```

## 10. 데이터 흐름 요약

```mermaid
flowchart TB
    subgraph User["User Interaction"]
        Click["Button Click"]
    end

    subgraph Immediate["Immediate Feedback"]
        Particle["Particle Effect"]
        Optimistic["Optimistic Count"]
        ButtonAnim["Button Animation"]
    end

    subgraph Update["UI Update"]
        Gauge["Gauge Bar"]
        Counter["Counter Display"]
        RPM["RPM Indicator"]
    end

    subgraph Server["Server (Async)"]
        WS["WebSocket Send"]
        Response["Server Response"]
    end

    Click --> Particle
    Click --> Optimistic
    Click --> ButtonAnim
    Click --> WS

    Optimistic --> Gauge
    Optimistic --> Counter
    Optimistic --> RPM

    WS --> Response
    Response --> |Sync| Counter
    Response --> |Sync| Gauge
    Response --> |Sync| RPM
```
