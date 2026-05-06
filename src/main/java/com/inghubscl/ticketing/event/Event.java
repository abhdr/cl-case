package com.inghubscl.ticketing.event;

import com.inghubscl.ticketing.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "events")
public class Event extends Auditable {

  @Id @UuidGenerator private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String venue;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(nullable = false)
  private int capacity;

  @Column(name = "reserved_seats", nullable = false)
  private int reservedSeats = 0;

  @Column(nullable = false)
  private boolean published = false;

  @Version
  @Column(nullable = false)
  private long version = 0;

  protected Event() {}

  public Event(
      UUID ownerId, String title, String venue, Instant startsAt, Instant endsAt, int capacity) {
    this.ownerId = ownerId;
    this.title = title;
    this.venue = venue;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.capacity = capacity;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getVenue() {
    return venue;
  }

  public void setVenue(String venue) {
    this.venue = venue;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant endsAt) {
    this.endsAt = endsAt;
  }

  public int getCapacity() {
    return capacity;
  }

  public void setCapacity(int capacity) {
    this.capacity = capacity;
  }

  public int getReservedSeats() {
    return reservedSeats;
  }

  public boolean isPublished() {
    return published;
  }

  public void publish() {
    this.published = true;
  }

  public long getVersion() {
    return version;
  }
}
