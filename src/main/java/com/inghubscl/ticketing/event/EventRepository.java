package com.inghubscl.ticketing.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository
    extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

  /**
   * Atomic capacity check + seat reservation. Returns 1 if successful, 0 otherwise. The version
   * column is incremented to keep optimistic-locking invariants consistent with concurrent
   * EventService.update calls.
   */
  @Modifying
  @Query(
      """
      UPDATE Event e
         SET e.reservedSeats = e.reservedSeats + :seats,
             e.version = e.version + 1
       WHERE e.id = :id
         AND e.published = true
         AND e.reservedSeats + :seats <= e.capacity
      """)
  int tryReserve(@Param("id") UUID id, @Param("seats") int seats);

  /**
   * Defensive decrement; only applies if reservedSeats stays >= 0. Returns 0 on double-cancel
   * (idempotent no-op).
   */
  @Modifying
  @Query(
      """
      UPDATE Event e
         SET e.reservedSeats = e.reservedSeats - :seats,
             e.version = e.version + 1
       WHERE e.id = :id
         AND e.reservedSeats >= :seats
      """)
  int releaseSeats(@Param("id") UUID id, @Param("seats") int seats);
}
