# AlgoVerse Testing Strategy

## Layers

1. **Unit tests** (Vitest / JUnit) — fast, no I/O, run on every commit
2. **Integration tests** (Testcontainers) — real DB/Redis/Kafka in Docker, run on PR
3. **E2E tests** (Playwright) — full browser flows, run nightly
4. **Load tests** (k6) — performance baselines, run on release branches

## Running tests

### Backend

```bash
# Unit tests only
cd services/gamification-service && mvn test

# Unit + integration (Testcontainers spin up real DB/Redis)
cd services/gamification-service && mvn verify
```

### Frontend

```bash
# Unit tests
cd apps/web && npm test

# Unit tests with coverage report
cd apps/web && npm run test:coverage
```

### E2E

```bash
cd testing/e2e && npx playwright test
```

### Load

```bash
cd testing/load && k6 run scripts/gamification.js
```

## Integration test details

Integration tests live under `src/test/java/.../integration/` in each service and use:

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — full application context
- `@Testcontainers` — manages container lifecycle
- `PostgreSQLContainer<>` (`postgres:16-alpine`) — real relational DB
- `GenericContainer<>` (`redis:7-alpine`) — real Redis
- `@DynamicPropertySource` — injects container URLs into Spring properties at runtime
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` — lightweight JPA slice tests
- `@ActiveProfiles("test")` — activates `application-test.yml` overrides
- `@Transactional` + `@Rollback` on repository tests — keeps DB clean between tests
- `UUID.randomUUID()` for userId in every test — prevents cross-test data contamination

### application-test.yml overrides

Located at `src/test/resources/application-test.yml`. Key overrides:

- `spring.jpa.hibernate.ddl-auto: create-drop` — fresh schema per test run
- `spring.kafka.consumer.auto-startup: false` — no Kafka broker needed in test
- `spring.kafka.listener.auto-startup: false`

## Badge data

Integration tests rely on Flyway migration `V2__seed_badges.sql` which seeds the `badges` table. The `first-solve` badge slug is used in idempotency and badge-award tests.

## CI integration

Tests are split into two GitHub Actions jobs:

| Job | Trigger | Time budget |
|-----|---------|-------------|
| `unit-tests` | every push | < 2 min |
| `integration-tests` | PR to `develop` / `main` | < 10 min |

Docker must be available on the CI runner for Testcontainers to work. Use `ubuntu-latest` runners with Docker pre-installed (GitHub-hosted) or configure a self-hosted runner with Docker.
