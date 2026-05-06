# ADR 0001 — No-Oversell Strategy

## Status
Accepted (2026-05-06)

## Context

Reservations are created concurrently against an event with limited capacity. Two simultaneous
requests for the last seat must not both succeed.

Naive options:
- **Read-then-write inside a service transaction.** Susceptible to lost updates; `READ COMMITTED`
  on PostgreSQL allows two transactions to read the same `reserved_seats`, both see capacity
  remaining, both increment.
- **Pessimistic row lock (`SELECT ... FOR UPDATE`).** Correct but serializes the hottest event,
  hurting throughput in the very scenario this matters.
- **Distributed lock (Redis, ZooKeeper).** Adds an external dependency the spec does not require
  and introduces split-brain failure modes.
- **Queue-based serialization.** Strongest but architecturally heavier than the project warrants.

## Decision

Atomic single-statement UPDATE that does both the capacity check and the counter increment in
one round-trip:

```sql
UPDATE events
   SET reserved_seats = reserved_seats + :seats,
       version        = version + 1
 WHERE id = :id
   AND published = TRUE
   AND reserved_seats + :seats <= capacity;
```

The DB returns `1` on success, `0` if any condition fails. The service then inspects the event
to decide which exception to raise (`ResourceNotFound`, `EventNotPublished`,
`CapacityExceeded`).

A defensive `CHECK (reserved_seats >= 0 AND reserved_seats <= capacity)` constraint protects the
database from any future code path that bypasses the repository.

## Consequences

- **Correct under concurrency without locks.** Verified by `ReservationConcurrencyIT`: 15
  threads, capacity 10, exactly 10 succeed.
- **`Event.reserved_seats` is denormalized** — not in the spec's entity list. The alternative was
  a `SELECT SUM(seats) FROM reservations WHERE status <> 'CANCELLED'` recomputation per request,
  which makes atomic UPDATE impossible. The denormalization is justified explicitly here.
- **`@Version` invariant preserved.** The atomic UPDATE increments `version` itself, so any
  stale entity manager cache that races with `EventService.update()` will fail its next write
  with an `OptimisticLockException` instead of silently overwriting.
- **DB-agnostic.** The query is plain JPQL; it runs identically on H2 (test) and PostgreSQL
  (prod). No native SQL.
- **Rollback ergonomics.** `cancel()` calls `releaseSeats` with the same atomic pattern (with a
  `reserved_seats >= :seats` guard) so a double-cancel is a no-op rather than dropping the
  counter below zero.

## Alternatives Rejected

| Approach              | Why not                                                                 |
|-----------------------|-------------------------------------------------------------------------|
| Pessimistic lock      | Serializes hot events, hurts throughput, no win over atomic UPDATE.     |
| Optimistic lock + retry | Two round-trips per attempt; under contention retry storms hurt latency. |
| Redis distributed lock | External dep, fencing tokens needed; not justified by spec.            |
| Kafka outbox / queue  | Right for flash-sale scale, overkill for this case.                     |

If load grows past what a single Postgres can handle (think tens of thousands of req/s on a
single hot event), the natural next step is a write-through Redis counter with a periodic
reconciler.
