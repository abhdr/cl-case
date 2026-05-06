package com.inghubscl.ticketing.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

  private final IdempotencyKeyRepository repository;
  private final IdempotencyProperties properties;
  private final TransactionTemplate txTemplate;

  public IdempotencyServiceImpl(
      IdempotencyKeyRepository repository,
      IdempotencyProperties properties,
      PlatformTransactionManager txManager) {
    this.repository = repository;
    this.properties = properties;
    this.txTemplate = new TransactionTemplate(txManager);
    this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public ClaimResult claim(String key, String endpoint, String requestHash) {
    Instant expiresAt = Instant.now().plus(properties.ttl());
    IdempotencyKey candidate = new IdempotencyKey(key, endpoint, requestHash, expiresAt);
    try {
      IdempotencyKey saved = txTemplate.execute(status -> repository.saveAndFlush(candidate));
      return new ClaimResult.Granted(saved.getId());
    } catch (DataIntegrityViolationException ex) {
      Optional<IdempotencyKey> existing =
          txTemplate.execute(status -> repository.findByKeyAndEndpoint(key, endpoint));
      if (existing == null || existing.isEmpty()) {
        return new ClaimResult.InProgress();
      }
      IdempotencyKey row = existing.get();
      return switch (row.getStatus()) {
        case IN_PROGRESS -> new ClaimResult.InProgress();
        case COMPLETED -> {
          if (row.getRequestHash().equals(requestHash)) {
            yield new ClaimResult.Replay(
                row.getResponseStatus() == null ? 200 : row.getResponseStatus(),
                row.getResponseBody() == null ? "" : row.getResponseBody());
          }
          yield new ClaimResult.Mismatch();
        }
      };
    }
  }

  @Override
  public void complete(UUID id, int responseStatus, String responseBody, String responseHash) {
    txTemplate.executeWithoutResult(
        status ->
            repository
                .findById(id)
                .ifPresent(row -> row.complete(responseStatus, responseBody, responseHash)));
  }

  @Override
  public void deleteById(UUID id) {
    txTemplate.executeWithoutResult(status -> repository.deleteByIdImmediate(id));
  }

  @Override
  public String hash(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(data == null ? new byte[0] : data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  static String responseBodyAsString(byte[] bytes) {
    return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
  }
}
