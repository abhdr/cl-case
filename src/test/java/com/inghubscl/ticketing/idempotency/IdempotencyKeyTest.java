package com.inghubscl.ticketing.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

  @Test
  void newKeyIsInProgress() {
    Instant expires = Instant.now().plusSeconds(3600);

    IdempotencyKey key = new IdempotencyKey("k1", "POST /x", "hash", expires);

    assertThat(key.getKey()).isEqualTo("k1");
    assertThat(key.getEndpoint()).isEqualTo("POST /x");
    assertThat(key.getRequestHash()).isEqualTo("hash");
    assertThat(key.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
    assertThat(key.getExpiresAt()).isEqualTo(expires);
    assertThat(key.getCreatedAt()).isNotNull();
    assertThat(key.getResponseHash()).isNull();
    assertThat(key.getResponseBody()).isNull();
    assertThat(key.getResponseStatus()).isNull();
  }

  @Test
  void completeUpdatesAllResponseFields() {
    IdempotencyKey key = new IdempotencyKey("k", "e", "h", Instant.now().plusSeconds(60));

    key.complete(201, "{\"id\":\"x\"}", "respHash");

    assertThat(key.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    assertThat(key.getResponseStatus()).isEqualTo(201);
    assertThat(key.getResponseBody()).isEqualTo("{\"id\":\"x\"}");
    assertThat(key.getResponseHash()).isEqualTo("respHash");
  }
}
