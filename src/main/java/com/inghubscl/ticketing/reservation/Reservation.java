package com.inghubscl.ticketing.reservation;

import com.inghubscl.ticketing.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "reservations")
public class Reservation extends Auditable {

  @Id @UuidGenerator private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReservationStatus status;

  @Column(nullable = false)
  private int seats;

  protected Reservation() {}

  public Reservation(UUID eventId, UUID userId, int seats) {
    this.eventId = eventId;
    this.userId = userId;
    this.seats = seats;
    this.status = ReservationStatus.PENDING;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getUserId() {
    return userId;
  }

  public ReservationStatus getStatus() {
    return status;
  }

  public int getSeats() {
    return seats;
  }

  public void confirm() {
    this.status = ReservationStatus.CONFIRMED;
  }

  public void cancel() {
    this.status = ReservationStatus.CANCELLED;
  }
}
