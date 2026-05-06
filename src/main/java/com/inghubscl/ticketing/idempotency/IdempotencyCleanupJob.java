package com.inghubscl.ticketing.idempotency;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdempotencyCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

  private final IdempotencyKeyRepository repository;

  public IdempotencyCleanupJob(IdempotencyKeyRepository repository) {
    this.repository = repository;
  }

  @Scheduled(fixedRateString = "${app.idempotency.cleanup-interval}")
  @Transactional
  public void deleteExpired() {
    int deleted = repository.deleteExpired(Instant.now());
    if (deleted > 0) {
      log.info("Removed {} expired idempotency_keys row(s)", deleted);
    }
  }
}
