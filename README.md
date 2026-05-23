# AlgoVerse

> **The world's most advanced AI-powered Data Structures, Algorithms & System Design learning platform.**

AlgoVerse is a next-generation engineering education platform that combines cinematic UX, real-time algorithm visualizations, AST-based execution tracing, and an AI mentor — all in a single, beautifully crafted experience. Think LeetCode reimagined from first principles, with a Bayesian personalization engine that adapts every problem recommendation to your exact knowledge state.

---

## What Is AlgoVerse?

AlgoVerse is built for software engineers who want to go beyond grinding problems. It is an intelligent learning environment where:

- **The IDE teaches you** — every code submission is traced at the AST level, generating step-by-step memory and stack visualizations so you can *see* your algorithm run.
- **An AI mentor guides you** — context-aware hints, automated code review, and natural-language explanations powered by Anthropic Claude, streamed in real time.
- **Your learning adapts** — a Bayesian Knowledge Tracing (BKT) engine models your mastery per topic and serves problems calibrated to your current skill level.
- **The UI feels alive** — cinematic transitions, GPU-accelerated animations, and an obsessive attention to visual hierarchy make studying feel less like work.

---

## Core Features (Roadmap)

| Feature | Status |
|---|---|
| Authentication (JWT RS256 + rotating refresh tokens) | In Progress |
| Problem browser with search, filter, pagination | In Progress |
| Monaco-based code editor (multi-language) | Planned |
| Sandboxed code execution (Docker + gVisor) | In Progress |
| Real-time submission feedback over WebSocket (STOMP) | In Progress |
| AST-based execution tracing & memory visualization | Planned |
| Three.js algorithm visualizations | Planned |
| AI mentor — hints, code review, explanations (Claude) | Planned |
| Bayesian Knowledge Tracing personalization engine | Planned |
| Gamification — XP, streaks, badges, leaderboard | Planned |
| Collaborative coding rooms (WebRTC + CRDT) | Planned |
| System Design module | Planned |
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
│   └── ai-service/             # Python · FastAPI · Anthropic Claude SDK · BKT
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
| AI | FastAPI, Anthropic Claude SDK (streaming SSE), BKT personalization |
| Database | PostgreSQL (transactional), MongoDB (traces/analytics), Elasticsearch (search) |
| Observability | Micrometer + Prometheus + Grafana + Loki |
| Infrastructure | Terraform, AWS EKS, GitHub Actions CI/CD |

---

## Running Locally

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Node.js | 20.x |
| Java | 21 |
| Python | 3.11 |
| Docker | 24.x (with Docker Compose v2) |
| Maven | 3.9 |

### 1. Clone the repo

```bash
git clone https://github.com/piyushkmrmandal/algo-verse.git
cd algo-verse
git checkout develop
```

### 2. Start infrastructure (databases, Redis, Kafka)

```bash
docker compose -f docker-compose.dev.yml up -d
```

This starts: PostgreSQL, Redis, Apache Kafka + Zookeeper, Elasticsearch, MongoDB.

> **Note:** `docker-compose.dev.yml` is coming soon. In the meantime, start each dependency manually using the ports documented below.

### 3. Generate RSA keys for JWT (auth-service)

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

### 4. Configure environment variables

Each service reads configuration from environment variables. Copy the example and fill in your values:

```bash
# auth-service
export AUTH_DB_URL=jdbc:postgresql://localhost:5432/algoverse_auth
export REDIS_HOST=localhost
export REDIS_PASSWORD=
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export JWT_PRIVATE_KEY="$(cat private.pem)"
export JWT_PUBLIC_KEY="$(cat public.pem)"

# problem-service
export PROBLEM_DB_URL=jdbc:postgresql://localhost:5432/algoverse_problems

# execution-service
export EXECUTION_DB_URL=jdbc:postgresql://localhost:5432/algoverse_execution
```

### 5. Start Spring Boot services

Run each service in a separate terminal from its directory:

```bash
# Terminal 1
cd services/auth-service && mvn spring-boot:run

# Terminal 2
cd services/problem-service && mvn spring-boot:run

# Terminal 3
cd services/execution-service && mvn spring-boot:run
```

Default ports: auth → `8081`, problems → `8082`, execution → `8083`.

### 6. Start the AI service

```bash
cd services/ai-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=your_key_here
uvicorn main:app --reload --port 8090
```

### 7. Start the frontend

```bash
# Install dependencies (from repo root)
npm install

# Start dev server
npm run dev -w apps/web
```

The frontend dev server runs on `http://localhost:5173` and proxies API calls to the backend services automatically.

### Default port map

| Service | Port |
|---|---|
| Frontend (Vite) | 5173 |
| auth-service | 8081 |
| problem-service | 8082 |
| execution-service | 8083 |
| ai-service (FastAPI) | 8090 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Elasticsearch | 9200 |
| MongoDB | 27017 |

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
