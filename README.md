# AlgoVerse

> **The world's most advanced AI-powered Data Structures, Algorithms & System Design learning platform.**

AlgoVerse is a next-generation engineering education platform that combines cinematic UX, real-time algorithm visualizations, AST-based execution tracing, and an AI mentor — all in a single, beautifully crafted experience. Think LeetCode reimagined from first principles, with a Bayesian personalization engine that adapts every problem recommendation to your exact knowledge state.

---

## What Is AlgoVerse?

AlgoVerse is built for software engineers who want to go beyond grinding problems. It is an intelligent learning environment where:

- **The IDE teaches you** — every code submission is traced at the AST level, generating step-by-step memory and stack visualizations so you can *see* your algorithm run.
- **An AI mentor guides you** — context-aware hints, automated code review, and natural-language explanations powered by Ollama (open-source LLM — no API key required), streamed in real time.
- **Your learning adapts** — a Bayesian Knowledge Tracing (BKT) engine models your mastery per topic and serves problems calibrated to your current skill level.
- **The UI feels alive** — cinematic transitions, GPU-accelerated animations, and an obsessive attention to visual hierarchy make studying feel less like work.

---

## Core Features (Roadmap)

| Feature | Status |
|---|---|
| Authentication (JWT RS256 + rotating refresh tokens) | ✅ Done |
| OAuth2 social login (Google, GitHub, LinkedIn) | ✅ Done |
| OAuth2 callback page (frontend) | ✅ Done |
| Problem browser with search, filter, pagination | ✅ Done |
| Monaco-based code editor (multi-language) | ✅ Done |
| Sandboxed code execution (Docker + gVisor) | ✅ Done |
| Real-time submission feedback over WebSocket (STOMP) | ✅ Done |
| AI mentor — hints, code review, explanations (Ollama open-source LLM) | ✅ Done |
| Bayesian Knowledge Tracing personalization engine | ✅ Done |
| Gamification — XP, streaks, badges, leaderboard | ✅ Done |
| Analytics — Kafka-driven platform metrics dashboard | ✅ Done |
| System Design module (problem catalog + AI feedback) | ✅ Done |
| **Collaborative coding rooms (OT + WebSocket STOMP)** | ✅ Done |
| **GitHub Actions CI/CD (per-service build+test, GHCR push)** | ✅ Done |
| **Notification service (in-app + SES email + FCM push)** | ✅ Done |
| **AST-based execution tracing (Python sys.settrace + Node vm)** | ✅ Done |
| **Unit tests — notification-service (dispatch routing, idempotency, REST slice)** | ✅ Done |
| **Testcontainers integration tests — notification-service + visualization-service** | ✅ Done |
| **data-testid attributes — SysdesignListPage, CollaboratePage, SysdesignProblemPage** | ✅ Done |
| **Shared type consolidation — SupportedLanguage + toApiLanguage() in shared-types** | ✅ Done |
| **Nginx API Gateway — single :80 entry point routing all 11 microservices** | ✅ Done |
| **Sysdesign seed data — 13 problems across Storage/Compute/Messaging/Search/Social/Financial** | ✅ Done |
| **Badge emoji mapping — per-slug emojis (🩸🥉🥈🥇🔥👑💯⚡) in DashboardPage** | ✅ Done |
| **OT client — proper Operational Transform engine replacing naive diff in CollabRoomPage** | ✅ Done |
| **SysdesignProblemPage — AI feedback migrated from queryClient.fetchQuery to useMutation** | ✅ Done |
| Dark/light theme with CSS variable design tokens | ✅ Done |
| **Unit tests — analytics-service, submission-service, sysdesign-service (service + Kafka + REST slice)** | ✅ Done |
| **Maven integration profile (`-P integration`) — analytics, submission, sysdesign services (Testcontainers)** | ✅ Done |
| **Maven integration profile + Testcontainers integration tests — auth, collaboration, execution, problem, gamification** | ✅ Done |
| **gamification-service Spring Boot upgrade 3.2.5 → 3.3.0** | ✅ Done |
| **Null-safe JWT filter — execution-service + problem-service (dev/test mode when key not set)** | ✅ Done |
| **Cinematic landing page — matrix rain canvas, typewriter DSA snippets, animated feature badges** | ✅ Done |
| **AI service migrated from Anthropic Claude → Ollama (open-source LLM, no API key required)** | ✅ Done |
| Three.js algorithm visualizations | Planned |
| Admin panel — problem authoring, analytics | Planned |

---

## Architecture Overview

AlgoVerse is a **microservices monorepo** built for production-grade scale.

```
AlgoVerse
├── apps/
│   └── web/                    # React 18 + Vite 5 + TypeScript frontend
├── libs/
│   └── shared-types/           # Shared TypeScript types (events, API shapes)
├── services/
│   ├── auth-service/           # Java 21 · Spring Boot 3 · JWT RS256
│   ├── problem-service/        # Java 21 · Spring Boot 3 · JPA + Redis cache
│   ├── execution-service/      # Java 21 · Spring Boot 3 · Docker sandbox + STOMP
│   ├── gamification-service/   # Java 21 · Spring Boot 3 · XP, streaks, badges
│   ├── analytics-service/      # Java 21 · Spring Boot 3 · event-driven analytics
│   ├── collaboration-service/  # Java 21 · Spring Boot 3 · WebRTC signalling
│   ├── notification-service/   # Java 21 · Spring Boot 3 · email / push
│   ├── sysdesign-service/      # Java 21 · Spring Boot 3 · system design module
│   ├── visualization-service/  # Java 21 · Spring Boot 3 · trace generation
│   └── ai-service/             # Python · FastAPI · Ollama (open-source LLM) · BKT
└── infrastructure/             # Terraform (EKS, RDS, ElastiCache, MSK, S3)
```

### Key Technology Decisions

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite 5, TypeScript 5.5, TailwindCSS 3.4, Framer Motion, Three.js, Monaco Editor |
| State | Zustand 4.5 (persist), React Query 5.51 |
| API client | Axios with JWT injection + 401 auto-refresh queue |
| Routing | React Router 6.24 with lazy loading + auth guards |
| Backend | Java 21, Spring Boot 3.3, Spring Security 6, Spring Data JPA |
| Auth | JWT RS256 — 15-min access tokens, BCrypt-hashed rotating refresh tokens |
| Cache | Redis (token blocklist, quota, @Cacheable problem cache) |
| Messaging | Apache Kafka (13 topics + DLQs) |
| Execution sandbox | Docker + gVisor (runsc), --cap-drop=ALL, --network=none, RAM-backed /tmp |
| Real-time | STOMP over SockJS WebSocket |
| AI | FastAPI, Ollama REST API (streaming SSE), BKT personalization |
| Database | PostgreSQL (transactional), MongoDB (traces/analytics), Elasticsearch (search) |
| Observability | Micrometer + Prometheus + Grafana + Loki |
| Infrastructure | Terraform, AWS EKS, GitHub Actions CI/CD |

---

## Local Development

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Docker | 24.x (with Docker Compose v2) |
| Java | 21 |
| Node.js | 20.x |
| Python | 3.12+ |
| Maven | 3.9 |

### Quick Start — Infra Only (recommended for daily development)

Run infrastructure in Docker and application services locally for fast iteration:

```bash
# 1. Copy and configure environment variables
cp .env.example .env
# Edit .env — set OLLAMA_BASE_URL if not using default localhost:11434

# 2. Start all infrastructure (postgres, redis, kafka, elasticsearch, mongodb)
make infra

# 3. Generate RSA keys for JWT (auth-service, one-time)
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem

# 4. Run each application service (separate terminals)
cd services/auth-service && mvn spring-boot:run
cd services/problem-service && mvn spring-boot:run
cd services/submission-service && mvn spring-boot:run
cd services/gamification-service && mvn spring-boot:run
# AI service — requires Ollama running locally (no API key needed)
# Install: brew install ollama  |  Pull model: ollama pull qwen2.5-coder:7b
ollama serve &
cd services/ai-service && uvicorn main:app --reload --port 8090

# 5. Start the frontend
make web
# or: cd apps/web && npm run dev
```

> **Kafka note:** When running services locally (outside Docker), use `localhost:29092` as the Kafka bootstrap server. Inside Docker, use `kafka:9092`.

### Full Stack with Docker

Run everything — infra and all application services — in Docker:

```bash
cp .env.example .env   # configure as needed
make up                # starts full stack
make logs s=auth-service   # tail logs for a specific service
make down              # stop everything
make clean             # remove containers + volumes (destructive)
```

### Makefile Targets

| Target | Description |
|---|---|
| `make infra` | Start infrastructure only |
| `make up` | Start full stack |
| `make down` | Stop all services |
| `make logs s=<name>` | Tail logs for a service |
| `make ps` | Show running containers |
| `make clean` | Remove containers + volumes |
| `make kafka-topics` | Create required Kafka topics |
| `make web` | Start frontend Vite dev server |

### Service Port Reference

| Service | Port | Notes |
|---|---|---|
| **Nginx API Gateway** | **80** | **Single entry point — routes all services by path prefix** |
| Frontend (Vite) | 5173 | Run locally with `make web` |
| auth-service | 8081 | Spring Boot |
| problem-service | 8082 | Spring Boot |
| submission-service | 8083 | Spring Boot |
| gamification-service | 8084 | Spring Boot |
| analytics-service | 8085 | Spring Boot |
| sysdesign-service | 8086 | Spring Boot |
| execution-service | 8087 | Spring Boot (Docker sandbox) |
| collaboration-service | 8088 | Spring Boot (OT + STOMP WebSocket) |
| Kafka UI | 8089 | http://localhost:8089 |
| ai-service (FastAPI) | 8090 | Python |
| notification-service | 8091 | Spring Boot (in-app, SES email, FCM push) |
| visualization-service | 8092 | Spring Boot (Python + Node.js AST tracers) |
| PostgreSQL | 5432 | |
| Redis | 6379 | |
| Kafka (Docker internal) | 9092 | Use `kafka:9092` inside Docker |
| Kafka (host-accessible) | 29092 | Use `localhost:29092` outside Docker |
| Zookeeper | 2181 | |
| Elasticsearch | 9200 | |
| MongoDB | 27017 | |

---

## Testing

### Unit Tests (Mockito — no infra required)
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl services/analytics-service
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl services/submission-service
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl services/sysdesign-service
# notification-service — dispatch routing, idempotency, REST controller slice
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -pl services/notification-service
```

### Integration Tests (Testcontainers — Docker required)
```bash
# Spins up real databases/caches automatically via Docker — all 9 services covered
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/analytics-service     -P integration
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/submission-service    -P integration
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/sysdesign-service     -P integration
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/notification-service  -P integration
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/visualization-service -P integration

# New in this release — full register→login→/me end-to-end (PostgreSQL + Redis)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/auth-service          -P integration
# collaboration-service — context + security (PostgreSQL + Redis, JWT disabled in test mode)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/collaboration-service -P integration
# execution-service — context + security (PostgreSQL + Redis, JWT disabled in test mode)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/execution-service     -P integration
# problem-service — public GET endpoints + security (PostgreSQL + MongoDB + Redis + Elasticsearch 8)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/problem-service       -P integration
# gamification-service — XP award + badge + leaderboard (upgraded to Spring Boot 3.3.0)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -pl services/gamification-service  -P integration
```

### Load Tests (k6)
```bash
# Requires k6 (brew install k6) and a running collaboration-service
k6 run testing/load/scripts/collaboration.js
# Or against staging:
BASE_URL=http://staging.algoverse.io:8088 WS_URL=ws://staging.algoverse.io:8088 \
  k6 run testing/load/scripts/collaboration.js
```

### E2E Tests (Playwright)
```bash
cd testing/e2e
npm install
# Requires full stack running (make up)
npx playwright test tests/collaboration/collaboration.spec.ts
npx playwright test --ui  # interactive mode
```

---

## Project Structure — Frontend

```
apps/web/src/
├── App.tsx               # Router config + auth guards
├── main.tsx              # React Query client + root render
├── index.css             # Design tokens + Tailwind utilities
├── lib/
│   ├── api.ts            # Axios instance + JWT refresh interceptor
│   └── queryKeys.ts      # Typed React Query key factory
├── stores/
│   └── auth-store.ts     # Zustand auth store (persisted)
├── pages/                # Route-level components (lazy-loaded)
├── components/           # Shared UI components
└── hooks/                # Custom React hooks
```

---

## Contributing

This is an active solo-build project. Contributions, ideas, and bug reports are welcome via GitHub Issues.

---

## License

MIT © Piyush Kumar Mandal
