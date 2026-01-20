# Phase 1: 프로젝트 셋업

## 목표
모노레포 구조로 Backend(Spring Boot)와 Frontend(React)를 구성하고, 개발 환경을 완성한다.

## 선행 의존성
- 없음 (최초 Phase)

## 범위
- 모노레포 디렉토리 구조 생성
- Backend: Spring Boot 3.4 + Java 21 프로젝트 초기화
- Frontend: Vite 6 + React 19 + TypeScript 프로젝트 초기화
- Docker Compose: Redis 7.x 로컬 개발 환경
- 공통 설정 파일 작성

---

## 디렉토리 구조

```
crypto_v2/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/crypto/prayer/
│   │   │   │   ├── PrayerApplication.java
│   │   │   │   ├── adapter/
│   │   │   │   │   ├── in/
│   │   │   │   │   │   └── websocket/
│   │   │   │   │   └── out/
│   │   │   │   │       ├── redis/
│   │   │   │   │       └── binance/
│   │   │   │   ├── application/
│   │   │   │   │   ├── port/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   └── out/
│   │   │   │   │   └── service/
│   │   │   │   ├── domain/
│   │   │   │   │   └── model/
│   │   │   │   └── infrastructure/
│   │   │   │       └── config/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── application-local.yml
│   │   └── test/
│   │       └── java/com/crypto/prayer/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── stores/
│   │   ├── types/
│   │   ├── i18n/
│   │   ├── assets/
│   │   │   ├── sounds/
│   │   │   └── images/
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   └── index.css
│   ├── public/
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.js
│   └── Dockerfile
├── docker/
│   ├── docker-compose.yml
│   └── docker-compose.prod.yml
├── docs/
│   └── plan/
├── .editorconfig
├── .gitignore
└── README.md
```

---

## 상세 구현 단계

### 1.1 루트 디렉토리 설정

#### .editorconfig
```ini
root = true

[*]
charset = utf-8
end_of_line = lf
indent_style = space
indent_size = 2
insert_final_newline = true
trim_trailing_whitespace = true

[*.{java,kt,kts}]
indent_size = 4

[*.md]
trim_trailing_whitespace = false
```

#### .gitignore
```gitignore
# IDE
.idea/
*.iml
.vscode/
*.swp
*.swo

# Build outputs
build/
dist/
target/
node_modules/

# Environment
.env
.env.local
.env.*.local

# Logs
*.log
logs/

# OS
.DS_Store
Thumbs.db

# Backend
*.jar
*.class

# Frontend
.pnpm-store/
```

### 1.2 Backend 프로젝트 초기화

#### settings.gradle.kts
```kotlin
rootProject.name = "prayer-backend"
```

#### build.gradle.kts
```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.crypto"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Lettuce (Redis client)
    implementation("io.lettuce:lettuce-core")

    // WebSocket
    implementation("org.webjars:webjars-locator-core")
    implementation("org.webjars:sockjs-client:1.5.1")
    implementation("org.webjars:stomp-websocket:2.3.4")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Virtual Threads 활성화
tasks.withType<JavaExec> {
    jvmArgs = listOf("--enable-preview")
}
```

#### src/main/resources/application.yml
```yaml
spring:
  application:
    name: prayer-backend
  profiles:
    active: local
  threads:
    virtual:
      enabled: true

server:
  port: 8080

logging:
  level:
    root: INFO
    com.crypto.prayer: DEBUG
```

#### src/main/resources/application-local.yml
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 1
```

#### PrayerApplication.java
```java
package com.crypto.prayer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrayerApplication.class, args);
    }
}
```

### 1.3 Frontend 프로젝트 초기화

#### package.json
```json
{
  "name": "prayer-frontend",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "preview": "vite preview",
    "test": "vitest",
    "test:ui": "vitest --ui"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "jotai": "^2.10.3",
    "@stomp/stompjs": "^7.0.0",
    "framer-motion": "^11.15.0",
    "howler": "^2.2.4",
    "react-i18next": "^15.1.3",
    "i18next": "^24.2.0",
    "i18next-browser-languagedetector": "^8.0.2",
    "clsx": "^2.1.1"
  },
  "devDependencies": {
    "@types/react": "^19.0.2",
    "@types/react-dom": "^19.0.2",
    "@types/howler": "^2.2.12",
    "@vitejs/plugin-react": "^4.3.4",
    "typescript": "^5.7.2",
    "vite": "^6.0.5",
    "vitest": "^2.1.8",
    "@testing-library/react": "^16.1.0",
    "@testing-library/jest-dom": "^6.6.3",
    "jsdom": "^25.0.1",
    "tailwindcss": "^3.4.17",
    "postcss": "^8.4.49",
    "autoprefixer": "^10.4.20",
    "eslint": "^9.17.0",
    "@eslint/js": "^9.17.0",
    "eslint-plugin-react-hooks": "^5.1.0",
    "globals": "^15.14.0",
    "typescript-eslint": "^8.18.2"
  }
}
```

#### vite.config.ts
```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
})
```

#### tsconfig.json
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noUncheckedSideEffectImports": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src"]
}
```

#### tailwind.config.js
```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // 커스텀 색상 정의
        prayer: {
          up: '#22c55e',    // 상승 기도 (green-500)
          down: '#ef4444',  // 하락 기도 (red-500)
        },
        liquidation: {
          long: '#ef4444',  // 롱 청산 (red)
          short: '#22c55e', // 숏 청산 (green)
        },
      },
      animation: {
        'fade-in': 'fadeIn 0.3s ease-in-out',
        'slide-up': 'slideUp 0.3s ease-out',
        'pulse-fast': 'pulse 0.5s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { transform: 'translateY(10px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
      },
    },
  },
  plugins: [],
}
```

#### postcss.config.js
```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

#### index.html
```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <meta name="theme-color" content="#000000" />
    <title>Crypto Prayer</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

#### src/main.tsx
```typescript
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'
import './i18n'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

#### src/index.css
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  --color-bg: #ffffff;
  --color-text: #1a1a1a;
}

.dark {
  --color-bg: #0a0a0a;
  --color-text: #f5f5f5;
}

html, body, #root {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  background-color: var(--color-bg);
  color: var(--color-text);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
```

#### src/App.tsx
```typescript
function App() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <h1 className="text-4xl font-bold">Crypto Prayer</h1>
    </div>
  )
}

export default App
```

### 1.4 Docker Compose 설정

#### docker/docker-compose.yml
```yaml
version: '3.8'

services:
  redis:
    image: redis:7.4-alpine
    container_name: prayer-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

volumes:
  redis_data:
```

### 1.5 Backend Dockerfile

#### backend/Dockerfile
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradlew ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 1.6 Frontend Dockerfile

#### frontend/Dockerfile
```dockerfile
FROM node:22-alpine AS builder

WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

---

## 체크리스트

- [ ] 루트 디렉토리 구조 생성
- [ ] .editorconfig 작성
- [ ] .gitignore 작성
- [ ] Backend Gradle 프로젝트 초기화
  - [ ] build.gradle.kts 작성
  - [ ] settings.gradle.kts 작성
  - [ ] application.yml 작성
  - [ ] PrayerApplication.java 작성
- [ ] Frontend Vite 프로젝트 초기화
  - [ ] package.json 작성
  - [ ] vite.config.ts 작성
  - [ ] tsconfig.json 작성
  - [ ] tailwind.config.js 작성
  - [ ] 기본 컴포넌트 작성
- [ ] Docker Compose 파일 작성
- [ ] Dockerfile 작성 (Backend, Frontend)
- [ ] Git 저장소 초기화
- [ ] 로컬 개발 환경 동작 확인
  - [ ] Redis 컨테이너 실행 확인
  - [ ] Backend 실행 확인 (port 8080)
  - [ ] Frontend 실행 확인 (port 5173)

---

## 검증 명령어

```bash
# Redis 실행
cd docker && docker-compose up -d

# Backend 실행
cd backend && ./gradlew bootRun

# Frontend 실행 (별도 터미널)
cd frontend && pnpm install && pnpm dev

# 헬스체크
curl http://localhost:8080/actuator/health
curl http://localhost:5173
```

---

## 다음 Phase
→ [Phase 2a: Backend 기반](phase2a-backend-foundation.md)
