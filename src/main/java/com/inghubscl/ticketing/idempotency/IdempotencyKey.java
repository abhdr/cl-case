package com.inghubscl.ticketing.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

  @Id @UuidGenerator private UUID id;

  @Column(name = "idem_key", nullable = false)
  private String key;

  @Column(nullable = false)
  private String endpoint;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "response_hash", length = 64)
  private String responseHash;

  @Column(name = "response_body", columnDefinition = "TEXT")
  private String responseBody;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IdempotencyStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected IdempotencyKey() {}

  public IdempotencyKey(String key, String endpoint, String requestHash, Instant expiresAt) {
    this.key = key;
    this.endpoint = endpoint;
    this.requestHash = requestHash;
    this.status = IdempotencyStatus.IN_PROGRESS;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public String getKey() {
    return key;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public String getResponseHash() {
    return responseHash;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public IdempotencyStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void complete(int responseStatus, String responseBody, String responseHash) {
    this.status = IdempotencyStatus.COMPLETED;
    this.responseStatus = responseStatus;
    this.responseBody = responseBody;
    this.responseHash = responseHash;
  }
}
