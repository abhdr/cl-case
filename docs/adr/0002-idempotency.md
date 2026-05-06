# ADR 0002 — Idempotency for Reservation Creation

## Status
Accepted (2026-05-06)

## Context

`POST /api/events/{id}/reservations` charges seats. A network retry from the client (timeout,
connection drop) must not produce a second reservation. The spec mandates an `Idempotency-Key`
header on this endpoint.

Goals:
1. Same key + same payload twice → return the exact response from the first call. No double
   charge of seats.
2. Same key + different payload → reject with `422 Unprocessable Entity` (key reuse).
3. Two simultaneous requests with the same key → only one wins; the other gets `409 Conflict`.
4. A failed request must not poison the key — the client should be free to retry.

## Decision

A two-layer pattern:

1. **`ContentCachingFilter`** wraps the request in a custom `CachedBodyHttpServletRequest`
   (re-readable input stream) and the response in `ContentCachingResponseWrapper`. Scoped via
   path matching to reservation creation only — the wrappers cost nothing on every other route.
2. **`IdempotencyInterceptor`** does claim/replay/complete:
   - **`preHandle`** reads the cached request body, hashes it (SHA-256), and asks
     `IdempotencyService.claim(key, endpoint, hash)`.
   - **`afterCompletion`** persists the response body + status when the handler returned 2xx,
     or deletes the row on 4xx/5xx so the client can retry.

`IdempotencyService.claim` uses a programmatic `TransactionTemplate` (REQUIRES_NEW) instead of
`@Transactional`. Reason: when the optimistic insert collides on the unique `(idem_key,
endpoint)` constraint, the existing row must be read in a fresh transaction — the original one
is already marked `rollback-only` by Hibernate. The flow:

```
INSERT IN_PROGRESS (key, endpoint, request_hash, expires_at)
└── success → Granted(id)        → handler runs, response captured in afterCompletion
└── unique violation → SELECT existing
                       ├── IN_PROGRESS              → 409
                       ├── COMPLETED + same hash    → cached response replayed (preHandle returns false)
                       └── COMPLETED + diff hash    → 422
```

### Hash strategy

SHA-256 over the raw request bytes (no JSON normalization). Two requests with semantically equal
but syntactically different bodies are treated as different — acceptable, because a single
client retrying its own failed request will send byte-identical content.

### TTL

Records expire after **1 hour**, swept every 15 minutes by `IdempotencyCleanupJob`. Short
enough to keep the table small; long enough for any sensible retry window.

### Endpoint normalization

`/api/events/{uuid}/reservations` is normalized to the literal string
`POST /api/events/*/reservations` so the same key cannot mistakenly map to two different events.

## Consequences

- **Race-safe.** The DB unique constraint is the source of truth for "who claimed first"; no
  application-level locks needed.
- **Failure-friendly.** `4xx`/`5xx` responses delete the row. A client whose validation failed
  the first time can fix the payload and retry with the same key.
- **Memory cost.** Bodies for COMPLETED rows are stored as `TEXT`; with the 1h TTL and the
  volumes this case anticipates, the storage hit is negligible.

## Alternatives Rejected

| Approach                                | Why not                                                  |
|-----------------------------------------|----------------------------------------------------------|
| Service-method `@Idempotent` AOP        | Bypasses HTTP cache layer; replay can't reuse 2xx body.  |
| Single filter (no interceptor)          | Loses access to the matched handler; path matching becomes ad-hoc. |
| Redis-backed idempotency cache          | External dep, no spec requirement, eventually-consistent vs. transactional `(key, endpoint)` constraint. |
| Permissive replay (cache 4xx too)       | Would lock a client out after one bad payload; spec does not require it. |
