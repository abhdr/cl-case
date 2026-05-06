# Secure Ticketing & Reservation API

Spring Boot 4 + Java 21 implementation of an event ticketing platform with role-based JWT
authentication, idempotent reservations, and atomic capacity management.

## Quick Start

### Prerequisites

- Java 21+ (Temurin or any JDK 21 distribution)
- Maven 3.9+
- (optional) Docker Desktop — only needed for the PostgreSQL profile

### Run with H2 (default)

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080` with the **`dev`** profile (H2 in-memory),
applies Flyway migrations automatically, and seeds three demo users (see below).

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI spec: <http://localhost:8080/v3/api-docs>
- H2 console: <http://localhost:8080/h2-console> (jdbc URL `jdbc:h2:mem:ticketing_dev`)
- Health: <http://localhost:8080/actuator/health>

### Run with PostgreSQL (optional)

```bash
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

`application-postgres.yml` connects to `localhost:5432/ticketing` with user `app/app`. The
schema is identical thanks to DB-agnostic migrations (no PostgreSQL-only features used).

### Test

```bash
mvn verify          # unit + integration + concurrency, runs Spotless and builds JaCoCo report
mvn test            # unit tests only
mvn spotless:apply  # auto-format Java sources before committing
```

JaCoCo HTML report: `target/site/jacoco/index.html`.

## Seed Users (dev profile only)

| Role      | Email                     | Password         |
|-----------|---------------------------|------------------|
| ADMIN     | admin@example.com         | `Admin123!`      |
| ORGANIZER | organizer@example.com     | `Organizer123!`  |
| CUSTOMER  | customer@example.com      | `Customer123!`   |

Loaded by a `CommandLineRunner` annotated `@Profile("dev")`; never created in production.

## Authentication Flow

1. `POST /api/auth/register` — body `{"email":"u@e.com","password":"min8chars"}` → returns
   `{accessToken, refreshToken, expiresIn}` and creates a CUSTOMER.
2. `POST /api/auth/login` — same body → returns a token pair.
3. Call protected endpoints with `Authorization: Bearer <accessToken>`.
4. When the access token expires, `POST /api/auth/refresh` with `{refreshToken}` rotates the
   pair (old refresh is revoked).

### Sample JWT payload

```json
{
  "sub": "8a4d3c1f-...-uuid",
  "iss": "ticketing-api",
  "iat": 1714959200,
  "exp": 1714960100,
  "jti": "request-unique-id",
  "email": "admin@example.com",
  "roles": ["ADMIN"]
}
```

- **Access TTL**: 15 min (`app.jwt.access-ttl: PT15M`)
- **Refresh TTL**: 7 days (`app.jwt.refresh-ttl: P7D`)
- Refresh tokens are stored as SHA-256 hashes in `refresh_tokens`; the raw value never lives in the DB.
- Algorithm: HS256, secret via `JWT_SECRET` env var (min 256-bit; falls back to a dev value).

## Endpoint Reference

| Method | Path                                       | Auth     | Roles                | Notes                            |
|--------|--------------------------------------------|----------|----------------------|----------------------------------|
| POST   | `/api/auth/register`                       | public   | —                    | creates a CUSTOMER               |
| POST   | `/api/auth/login`                          | public   | —                    | issues access + refresh          |
| POST   | `/api/auth/refresh`                        | public   | —                    | rotates refresh token            |
| POST   | `/api/events`                              | required | ORGANIZER / ADMIN    | creates draft event              |
| PUT    | `/api/events/{id}`                         | required | ORGANIZER / ADMIN    | owner OR admin                   |
| POST   | `/api/events/{id}/publish`                 | required | ORGANIZER / ADMIN    | owner OR admin                   |
| GET    | `/api/events?ownerId=&page=&size=`         | required | any                  | management list                  |
| GET    | `/api/events/{id}`                         | required | any                  | event detail                     |
| GET    | `/api/events/public?from=&to=&q=`          | public   | —                    | published events search          |
| POST   | `/api/events/{id}/reservations`            | required | CUSTOMER / ADMIN     | **`Idempotency-Key` required**   |
| POST   | `/api/reservations/{id}/confirm`           | required | reservation owner / ADMIN |                                  |
| POST   | `/api/reservations/{id}/cancel`            | required | reservation owner / ADMIN | idempotent                       |
| GET    | `/api/reservations`                        | required | any                  | caller's reservations            |
| GET    | `/api/reservations/{id}`                   | required | reservation owner / ADMIN |                                  |

All errors are returned as `application/problem+json` (RFC 7807) with a `traceId` extension.

## Architectural Reasoning

### Layered Design

```
controller → service (interface + impl) → repository (Spring Data JPA) → DB (Flyway-managed)
```

- **Service interfaces** decouple HTTP layer from business logic and make mocking trivial.
- **Constructor injection** with `final` fields, no field-level `@Autowired`, no Lombok.
- **Records** for DTOs and `@ConfigurationProperties`; entities are plain JPA classes.
- **`ddl-auto: validate`** + Flyway authoritative schema — the entity model can never silently drift.

### No Oversell — see [ADR 0001](docs/adr/0001-no-oversell-strategy.md)

Atomic conditional `UPDATE` on `events.reserved_seats` guarded by a DB `CHECK` constraint and an
optimistic-lock `version` increment. Verified by `ReservationConcurrencyIT` (15 threads, capacity 10
→ exactly 10 succeed).

### Idempotency — see [ADR 0002](docs/adr/0002-idempotency.md)

Filter (`CachedBodyHttpServletRequest` + `ContentCachingResponseWrapper`) wraps the request and
response so a `HandlerInterceptor` can hash the body and replay a cached response. Unique
constraint on `(idem_key, endpoint)` plus a `TransactionTemplate` boundary makes claim/replay
race-safe.

### Other Decisions

- **JWT rotation, not replay-detection**: every `/refresh` revokes the old token and issues a
  fresh pair. Detection of replay-after-revoke (auto-revoke all sessions) was considered and
  deferred — basic rotation already meets the spec's security bar.
- **Trace ID propagation**: `TraceIdFilter` writes a UUID into `MDC` on each request, exposed in
  every log line and every `ProblemDetail` response under `traceId`.
- **Audit logging is async** (`@Async` + AOP `@Around`) so it never bloats request latency.
- **Rate limiting** uses Bucket4j + Caffeine: 10 req/min per IP for `/api/auth/{login,register}`,
  100 req/min per authenticated user elsewhere. `429 Too Many Requests` responses include
  `Retry-After`.

## Idempotency Replay — Smoke Flow

```bash
TOKEN=$(curl -sX POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"customer@example.com","password":"Customer123!"}' | jq -r .accessToken)

EVENT_ID=...    # publish an event first as organizer
KEY=$(uuidgen)

# First call: 201 + reservationId
curl -sX POST localhost:8080/api/events/$EVENT_ID/reservations \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H "Content-Type: application/json" -d '{"seats":2}'

# Second call (same key, same body): 201 + SAME reservationId, reservedSeats unchanged
curl -sX POST localhost:8080/api/events/$EVENT_ID/reservations \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H "Content-Type: application/json" -d '{"seats":2}'

# Same key, different body: 422
curl -sX POST localhost:8080/api/events/$EVENT_ID/reservations \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H "Content-Type: application/json" -d '{"seats":3}'
```
