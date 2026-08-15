# Payment Transfer & Digital Banking System

A digital banking system for account management and money transfers between users, with two-factor confirmation on every transfer. Built as four cooperating services (three Spring Boot backends plus a React frontend) instead of a single monolith, to demonstrate a realistic edge/identity/ledger split end to end.

---

## Features

- Register / log in / change password with JWT-based stateless auth
- View account balance, IBAN and status
- Send money to another account, confirmed by a one-time code (SCA-style two-step transfer)
- Full transaction history, filterable by sent / received
- Field-level encryption of PII at rest, structured audit logging, correlation IDs across services

---

## Architecture

Four services, one MySQL database:

```
Browser
   |
   v
frontend (nginx)
   |  REST, JWT bearer
   v
gateway-service (WebFlux, :8080)   <- public edge: routing, JWT validation, OTP, transfer orchestration
   |                      \
   | proxy /auth/*         \ direct read (documented shortcut, see Trade-offs)
   v                        v
auth-service (WebFlux, :8082)    payment-core-service (MVC, :8081)
   |  internal REST                  |
   +----------------------------------+
                    |
                    v
                 MySQL 8
```

- **frontend** talks only to `gateway-service`, over plain REST with a `Bearer` JWT.
- **gateway-service** is the only public-facing service. It validates the JWT on every route except `/auth/register`, `/auth/login` and `/actuator/**`, proxies `/api/v1/auth/*` straight through to `auth-service`, and owns the transfer flow itself (OTP issuance, idempotency, then a call into `payment-core-service`'s internal API to actually move money).
- **auth-service** owns registration, login and password changes, and issues JWTs. It has **no database of its own** — every read/write goes through `payment-core-service`'s internal `/internal/users` API.
- **payment-core-service** is the only service that touches the ledger. It owns the MySQL schema (via Liquibase), exposes `/internal/*` endpoints consumed only by the other two services, and performs the actual balance debit/credit under a pessimistic lock.
- `gateway-service` also reads accounts/transactions directly off the shared database for its own read endpoints (`/accounts/me`, `/accounts/{id}`, `/accounts/{id}/transactions`) rather than round-tripping every read through `payment-core-service` — a deliberate, documented shortcut (see Trade-offs).

---

## Tech Stack

### Backend
| Technology | Purpose |
|---|---|
| Java 21 | Language |
| Spring Boot 3.3.5 | Application framework |
| Spring WebFlux | `gateway-service` and `auth-service` — non-blocking edge/identity services |
| Spring MVC | `payment-core-service` — blocking, transactional ledger service |
| Spring Data JPA + Hibernate | Database access (`ddl-auto: validate` — schema is owned by Liquibase, not Hibernate) |
| MySQL 8.0 | Database, owned exclusively by `payment-core-service` |
| Liquibase | Schema migrations, YAML changelogs |
| jjwt | JWT issuance (`auth-service`) and validation (`gateway-service`) |
| Caffeine | In-memory OTP-challenge cache and transfer-idempotency cache |
| Logback | Rolling file logs plus a separate structured audit-log appender |
| Micrometer Context Propagation | Carries MDC (userId) across WebFlux's reactive thread-hops |
| JaCoCo | Coverage reporting, enforced ≥70% in CI |

### Frontend
| Technology | Purpose |
|---|---|
| React 18 + TypeScript | UI |
| Vite | Dev server / build |
| React Router 7 | Client-side routing |
| Axios | HTTP client, JWT + idempotency-key interceptors |
| Tailwind CSS 4 | Styling |
| Vitest + Testing Library | Unit tests |
| Nginx | Static file serving in Docker/Kubernetes |

### Infrastructure
| Technology | Purpose |
|---|---|
| Docker / Docker Compose | Local multi-service run |
| Kubernetes (Minikube) | Cluster deployment |
| GitHub Actions | CI/CD |

---

## Prerequisites

| Tool | Mode 1 (Local) | Mode 2 (Docker) | Mode 3 (Kubernetes) |
|---|:---:|:---:|:---:|
| Java 21+ | ✅ | | |
| Maven 3.9+ | ✅ | | |
| Node.js 20+ | ✅ | | |
| MySQL 8.0 | ✅ | | |
| Docker | | ✅ | ✅ |
| Minikube | | | ✅ |
| kubectl | | | ✅ |

---

## Mode 1 — Local Development

Run all four services as separate processes.

### 1. Start MySQL

A local MySQL 8 instance on `localhost:3306`, database `banking`, user `banking`/`bankingpass` (or override via the `DB_*` env vars used below). Apply the schema once, from `payment-core-service`:

```bash
cd payment-core-service
mvn liquibase:update
```

### 2. Start payment-core-service

```bash
mvn spring-boot:run
```

Listens on `:8081`.

### 3. Start auth-service

```bash
cd auth-service
mvn spring-boot:run
```

Listens on `:8082`. Needs `payment-core-service` already up.

### 4. Start gateway-service

```bash
cd gateway-service
CORS_ALLOWED_ORIGINS=http://localhost:5173 mvn spring-boot:run
```

Listens on `:8080`. Needs both services above up. `CORS_ALLOWED_ORIGINS` must match wherever the frontend dev server actually runs — Vite's default is `5173`, not the `3000` the Docker/Kubernetes modes below use.

### 5. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

### 6. Open the application

```
http://localhost:5173
```

All three backend services share the same `JWT_SECRET` and `ENCRYPTION_KEY` — either leave both on their (documented, non-secret) dev-only defaults, or set matching env vars for every service.

---

## Mode 2 — Docker

```bash
docker compose up -d --build
```

Builds and starts all five containers (MySQL + 4 services) on one network, healthchecked and dependency-ordered.

Open the application:

```
http://localhost:3000
```

Backend ports are also published to the host for direct inspection: `gateway-service` on `:8080`, `auth-service` on `:8082`, `payment-core-service` on `:8081`, MySQL on `:3307`.

Tear down:

```bash
docker compose down -v
```

---

## Mode 3 — Kubernetes (Minikube)

```bash
minikube start --driver=docker

eval $(minikube docker-env)
docker build -t payment-core-service:latest ./payment-core-service
docker build -t auth-service:latest ./auth-service
docker build -t gateway-service:latest ./gateway-service
docker build -t frontend:latest ./frontend
eval $(minikube docker-env -u)

kubectl apply -f k8s/
kubectl get pods -n banking -w
```

`payment-core-service`/`gateway-service` will restart once or twice while waiting for MySQL — expected, Kubernetes' default restart policy handles it without extra wiring.

Access:

```bash
minikube service frontend -n banking --url
minikube service gateway-service -n banking --url
```

(`minikube service` resolves the actual reachable URL, since the cluster's internal IP usually isn't directly routable from the host under the Docker driver.) The frontend's API base URL and `gateway-service`'s `CORS_ALLOWED_ORIGINS` are both fixed at build/deploy time, so they won't automatically track whatever random port `minikube service` hands out — see Known Limitations.

Tear down:

```bash
kubectl delete namespace banking
minikube delete
```

Full manifest layout and details: `k8s/README.md`.

---

## API

All public traffic goes through `gateway-service` (`:8080`). Errors are RFC 7807 `application/problem+json`.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | – | Create an account |
| POST | `/api/v1/auth/login` | – | Get a JWT |
| POST | `/api/v1/auth/change-password` | Bearer | Change password |
| GET | `/api/v1/accounts/me` | Bearer | Resolve the caller's own account |
| GET | `/api/v1/accounts/{id}` | Bearer | Balance / IBAN / status |
| GET | `/api/v1/accounts/{id}/transactions` | Bearer | Transaction history |
| POST | `/api/v1/transfers/initiate` | Bearer | Validate the transfer, send an OTP challenge |
| POST | `/api/v1/transfers/confirm` | Bearer | Confirm the OTP, execute the transfer |

`payment-core-service`'s `/internal/*` endpoints are not public — they're only ever called service-to-service.

---

## Testing & Coverage

```bash
# each backend service
mvn verify

# frontend
npm test
```

`mvn verify` runs JaCoCo alongside the tests and fails the build if line coverage drops below 70% (`jacoco-maven-plugin`, all three backend services). CI runs the same command, so a change that drops coverage below the threshold won't pass.

---

## CI/CD

GitHub Actions (`.github/workflows/ci-cd.yml`), on every push/PR to `main`:

1. **core-service-test / gateway-service-test / auth-service-test** — `mvn verify` per service (coverage-gated), against a real MySQL service container where needed.
2. **frontend-build** — `npm test`, then `npm run build`.
3. **docker-build** — builds all 4 images, after the tests above pass.
4. **smoke-test** — brings up the full `docker compose` stack, polls until every container reports healthy, then hits each service's `/actuator/health` and the frontend's root route.

---

## Design Patterns & Notable Implementation Details

- **TCA-style state management** (`frontend/src/store/`) — the send-money wizard is driven by a pure reducer, `transferReducer(state, action) => [state, effect]`, modeled after The Composable Architecture: all business logic (which action is legal in which step, what side effect it triggers) lives in one pure, unit-tested function, while `useTransferStore.ts` is a thin effect-runner hook that executes the returned effect (an API call) and dispatches the result back in. Actions and effects are typed discriminated unions (`transferActions.ts`, `transferEffects.ts`).
- **Pessimistic locking with deterministic lock order** (`payment-core-service`) — `TransferService.executeTransfer` always locks the lower account ID first (`AccountRepository.findByIdForUpdate`, `@Lock(PESSIMISTIC_WRITE)`), which keeps opposite-direction concurrent transfers between the same two accounts deadlock-free.
- **RFC 7807 problem details** — every service maps its domain exceptions to `ProblemDetail` via its own `GlobalExceptionHandler`, so error responses stay uniform (`type`/`title`/`status`/`detail`) end to end, including across the gateway's proxy hops to `auth-service` and `payment-core-service`.
- **SCA-style OTP challenge** (`OtpChallengeService`) — a 6-digit code, 3-minute TTL, max 3 attempts, bcrypt-hashed at rest in the cache; delivery is mocked to a log line rather than a real SMS/email provider.
- **Idempotency keys** (`TransferIdempotencyService`) — `/transfers/confirm` requires an `X-Idempotency-Key`; a retried request with the same key returns the original response instead of re-spending the OTP challenge.
- **Field-level encryption** — `User.fullName` is AES-GCM encrypted at the JPA layer through an `AttributeConverter` (`EncryptedStringConverter`), transparent to the rest of the code.
- **Reactive MDC correlation** — in the two WebFlux services, request-scoped values (like `userId`) are written explicitly into Reactor `Context` via `.contextWrite(...)` rather than relying on automatic ThreadLocal capture, which doesn't reliably survive a `Schedulers.boundedElastic()` hop; a registered `ContextRegistry` accessor restores them into MDC for logging.
- **Structured audit logging** — a separate, non-additive `AUDIT` Logback logger writes one JSON line per security-relevant event (register/login/password-change/transfer outcomes) to its own rotated file, independent of the application log.
- **WebClient error-mapping wrappers** — `PaymentCoreClient`, `AuthServiceClient` and `CoreUserClient` each translate a downstream service's HTTP error responses into typed exceptions at the call site, so callers never handle a raw `WebClientResponseException`.

---

## Project Structure

```
gateway-service/         Spring WebFlux, :8080 — public edge, auth proxy, transfer orchestration, JWT validation
auth-service/             Spring WebFlux, :8082 — register/login/change-password, JWT issuance, no DB of its own
payment-core-service/     Spring MVC, :8081 — ledger, Liquibase schema, internal APIs
frontend/                 React 18 + TS, TCA-style transfer wizard
k8s/                      Kubernetes manifests — namespace, secrets, one Deployment+Service per component
scripts/e2e-flow.sh       End-to-end smoke test against a live stack
docker-compose.yml
.github/workflows/ci-cd.yml
```

---

## Trade-offs & Known Limitations

- **`gateway-service` reads accounts/transactions directly off the shared database** instead of routing every read through `payment-core-service` — a deliberate shortcut, documented in `gateway-service`'s `domain/package-info.java`. Writes always go through `payment-core-service`.
- **`User.email` is stored in plaintext**, unlike `fullName`. It backs the unique-email lookup/constraint, and AES-GCM's random IV means the same plaintext encrypts differently every time — which breaks equality lookups. A real fix needs a separate deterministic blind-index column (e.g. an HMAC of the email); out of scope here.
- **`JWT_SECRET` and `ENCRYPTION_KEY` ship with dev-only default values** in `application.yml`, `docker-compose.yml` and the Kubernetes secrets — clearly not real secrets, meant to be overridden before any real deployment.
- **Logs are local-disk only**, rotated but never shipped anywhere (no ELK/Loki/etc.) — fine for a local demo, not for production observability.
- **Audit logging excludes `payment-core-service`** — it never sees attempt-level failures (a wrong password, an expired OTP) that fail before reaching it; only `gateway-service` and `auth-service` emit audit events.
- **CORS origin and the frontend's API base URL are both fixed at build/deploy time.** This works cleanly for Docker Compose (`http://localhost:3000` on both sides) but not for Kubernetes on Minikube's Docker driver, where `minikube service --url` hands out a random local port per session — the k8s manifests ship a best-effort default (`CORS_ALLOWED_ORIGINS=http://localhost:30080`) that needs overriding to match whatever port you actually get.
- **The transfer-idempotency cache is single-instance and in-memory** (Caffeine, not Redis) — fine for one replica; two concurrent requests bearing the same brand-new idempotency key could both proceed. A shared, atomic-check-and-set cache would be needed to close that gap under multiple replicas.
- **JaCoCo enforces ≥70% line coverage per backend service in CI** — the unit-test suite focuses on business logic (services, controllers, WebClient error-mapping, exception handling); the full request/response cycle, OTP flow, concurrency and encryption round-trip are additionally verified live against a real running stack by `scripts/e2e-flow.sh`.
