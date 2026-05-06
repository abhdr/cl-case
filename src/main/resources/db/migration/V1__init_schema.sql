-- ============================================================================
-- Secure Ticketing & Reservation API — initial schema
-- DB-agnostic SQL: works on H2 (test/dev) and PostgreSQL 16+ (postgres profile).
-- UUIDs are generated JVM-side (Hibernate @UuidGenerator), not DB-side.
-- All timestamps are stored UTC; application uses java.time + jdbc.time_zone=UTC.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- USERS
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- USER_ROLES (junction; one user → many roles)
-- ----------------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id  UUID        NOT NULL,
    role     VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_roles_role CHECK (role IN ('ADMIN', 'ORGANIZER', 'CUSTOMER'))
);

-- ----------------------------------------------------------------------------
-- EVENTS
-- reserved_seats: denormalized counter for atomic capacity check (see ADR 0001).
-- version: optimistic locking (incremented by tryReserve / releaseSeats too).
-- ----------------------------------------------------------------------------
CREATE TABLE events (
    id              UUID         PRIMARY KEY,
    owner_id        UUID         NOT NULL,
    title           VARCHAR(255) NOT NULL,
    venue           VARCHAR(255) NOT NULL,
    starts_at       TIMESTAMP    NOT NULL,
    ends_at         TIMESTAMP    NOT NULL,
    capacity        INT          NOT NULL,
    reserved_seats  INT          NOT NULL DEFAULT 0,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_events_owner             FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_events_capacity         CHECK (capacity > 0),
    CONSTRAINT chk_events_reserved_seats   CHECK (reserved_seats >= 0 AND reserved_seats <= capacity),
    CONSTRAINT chk_events_dates            CHECK (ends_at > starts_at)
);

CREATE INDEX idx_events_owner_id           ON events (owner_id);
CREATE INDEX idx_events_published_starts   ON events (published, starts_at);

-- ----------------------------------------------------------------------------
-- RESERVATIONS
-- ----------------------------------------------------------------------------
CREATE TABLE reservations (
    id          UUID         PRIMARY KEY,
    event_id    UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    seats       INT          NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_event   FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_reservations_user    FOREIGN KEY (user_id)  REFERENCES users (id),
    CONSTRAINT chk_reservations_seats  CHECK (seats > 0),
    CONSTRAINT chk_reservations_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_reservations_event_status ON reservations (event_id, status);
CREATE INDEX idx_reservations_user_id      ON reservations (user_id);

-- ----------------------------------------------------------------------------
-- IDEMPOTENCY_KEYS
-- "key" is reserved in PostgreSQL → use idem_key.
-- response_body kept as TEXT for replay; cleaned by @Scheduled job after expires_at.
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    id               UUID         PRIMARY KEY,
    idem_key         VARCHAR(255) NOT NULL,
    endpoint         VARCHAR(255) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    response_hash    VARCHAR(64),
    response_body    TEXT,
    response_status  INT,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uq_idempotency_keys_key_endpoint UNIQUE (idem_key, endpoint),
    CONSTRAINT chk_idempotency_status           CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);

-- ----------------------------------------------------------------------------
-- REFRESH_TOKENS
-- token_hash: SHA-256 of the raw refresh token. Plain token never persisted.
-- ----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ----------------------------------------------------------------------------
-- AUDIT_LOGS
-- ----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id             UUID         PRIMARY KEY,
    actor_id       UUID,
    action         VARCHAR(100) NOT NULL,
    resource_type  VARCHAR(50)  NOT NULL,
    resource_id    VARCHAR(255),
    ip             VARCHAR(45),
    user_agent     VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_actor_created ON audit_logs (actor_id, created_at);
CREATE INDEX idx_audit_logs_resource      ON audit_logs (resource_type, resource_id);
