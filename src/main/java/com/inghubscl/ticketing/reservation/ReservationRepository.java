package com.inghubscl.ticketing.reservation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

  Optional<Reservation> findByIdAndUserId(UUID id, UUID userId);

  List<Reservation> findAllByUserId(UUID userId);

  long countByEventIdAndStatus(UUID eventId, ReservationStatus status);
}
