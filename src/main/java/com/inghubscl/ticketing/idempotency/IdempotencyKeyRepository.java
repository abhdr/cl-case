package com.inghubscl.ticketing.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

  Optional<IdempotencyKey> findByKeyAndEndpoint(String key, String endpoint);

  @Modifying
  @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);

  @Modifying
  @Query("DELETE FROM IdempotencyKey i WHERE i.id = :id")
  int deleteByIdImmediate(@Param("id") UUID id);
}
