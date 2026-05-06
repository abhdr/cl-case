package com.inghubscl.ticketing.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyServiceImplTest {

  @Mock IdempotencyKeyRepository repository;
  @Mock PlatformTransactionManager txManager;

  IdempotencyServiceImpl service;

  @BeforeEach
  void init() {
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service =
        new IdempotencyServiceImpl(
            repository,
            new IdempotencyProperties(Duration.ofHours(1), Duration.ofMinutes(15)),
            txManager);
  }

  @Test
  void claimGrantsWhenInsertSucceeds() {
    when(repository.saveAndFlush(any(IdempotencyKey.class)))
        .thenAnswer(
            inv -> {
              IdempotencyKey saved = inv.getArgument(0);
              setId(saved, UUID.randomUUID());
              return saved;
            });

    IdempotencyService.ClaimResult result = service.claim("k", "endpoint", "hash");

    assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.Granted.class);
  }

  @Test
  void claimReturnsInProgressOnConflictWithInProgressRow() {
    when(repository.saveAndFlush(any(IdempotencyKey.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));
    IdempotencyKey existing = new IdempotencyKey("k", "e", "h", Instant.now().plusSeconds(3600));
    when(repository.findByKeyAndEndpoint("k", "e")).thenReturn(Optional.of(existing));

    IdempotencyService.ClaimResult result = service.claim("k", "e", "h");

    assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.InProgress.class);
  }

  @Test
  void claimReturnsReplayOnCompletedSameHash() {
    when(repository.saveAndFlush(any(IdempotencyKey.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));
    IdempotencyKey existing = new IdempotencyKey("k", "e", "h", Instant.now().plusSeconds(3600));
    existing.complete(201, "{\"id\":\"x\"}", "rh");
    when(repository.findByKeyAndEndpoint("k", "e")).thenReturn(Optional.of(existing));

    IdempotencyService.ClaimResult result = service.claim("k", "e", "h");

    assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.Replay.class);
    var replay = (IdempotencyService.ClaimResult.Replay) result;
    assertThat(replay.responseStatus()).isEqualTo(201);
    assertThat(replay.responseBody()).isEqualTo("{\"id\":\"x\"}");
  }

  @Test
  void claimReturnsMismatchOnCompletedDifferentHash() {
    when(repository.saveAndFlush(any(IdempotencyKey.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));
    IdempotencyKey existing =
        new IdempotencyKey("k", "e", "originalHash", Instant.now().plusSeconds(3600));
    existing.complete(200, "{}", "rh");
    when(repository.findByKeyAndEndpoint("k", "e")).thenReturn(Optional.of(existing));

    IdempotencyService.ClaimResult result = service.claim("k", "e", "differentHash");

    assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.Mismatch.class);
  }

  @Test
  void claimReturnsInProgressWhenExistingRowVanished() {
    when(repository.saveAndFlush(any(IdempotencyKey.class)))
        .thenThrow(new DataIntegrityViolationException("dup"));
    when(repository.findByKeyAndEndpoint("k", "e")).thenReturn(Optional.empty());

    IdempotencyService.ClaimResult result = service.claim("k", "e", "h");

    assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.InProgress.class);
  }

  @Test
  void completeUpdatesExistingRow() {
    UUID id = UUID.randomUUID();
    IdempotencyKey row = new IdempotencyKey("k", "e", "h", Instant.now().plusSeconds(3600));
    when(repository.findById(id)).thenReturn(Optional.of(row));

    service.complete(id, 201, "body", "rh");

    assertThat(row.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    assertThat(row.getResponseStatus()).isEqualTo(201);
    assertThat(row.getResponseBody()).isEqualTo("body");
  }

  @Test
  void completeIgnoresMissingRow() {
    when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());

    service.complete(UUID.randomUUID(), 201, "b", "rh");
  }

  @Test
  void deleteByIdDelegatesToRepository() {
    UUID id = UUID.randomUUID();
    service.deleteById(id);
    verify(repository, times(1)).deleteByIdImmediate(id);
  }

  @Test
  void hashIsDeterministic() {
    byte[] body = "{\"seats\":2}".getBytes();
    String h1 = service.hash(body);
    String h2 = service.hash(body);

    assertThat(h1).isEqualTo(h2).hasSize(64);
  }

  @Test
  void hashHandlesNullAsEmpty() {
    String h = service.hash(null);
    assertThat(h).isEqualTo(service.hash(new byte[0]));
  }

  private static void setId(IdempotencyKey row, UUID id) {
    try {
      var f = IdempotencyKey.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(row, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
