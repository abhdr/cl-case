package com.inghubscl.ticketing.idempotency;

import java.util.UUID;

public interface IdempotencyService {

  /**
   * Either inserts a new IN_PROGRESS record or returns the resolution for an existing one.
   *
   * @return one of {@link ClaimResult.Granted}, {@link ClaimResult.Replay}, {@link
   *     ClaimResult.InProgress}, {@link ClaimResult.Mismatch}
   */
  ClaimResult claim(String key, String endpoint, String requestHash);

  /** Marks the IN_PROGRESS record as COMPLETED with the response payload. */
  void complete(UUID id, int responseStatus, String responseBody, String responseHash);

  /** Removes an IN_PROGRESS record (used when the handler returned 4xx/5xx). */
  void deleteById(UUID id);

  String hash(byte[] data);

  sealed interface ClaimResult {
    record Granted(UUID id) implements ClaimResult {}

    record Replay(int responseStatus, String responseBody) implements ClaimResult {}

    record InProgress() implements ClaimResult {}

    record Mismatch() implements ClaimResult {}
  }
}
