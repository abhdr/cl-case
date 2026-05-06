package com.inghubscl.ticketing.reservation;

import com.inghubscl.ticketing.common.SecurityUtils;
import com.inghubscl.ticketing.reservation.dto.ReservationCreateRequest;
import com.inghubscl.ticketing.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationController {

  private final ReservationService reservationService;

  public ReservationController(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @PostMapping("/api/events/{eventId}/reservations")
  @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
  public ResponseEntity<ReservationResponse> create(
      @PathVariable UUID eventId, @Valid @RequestBody ReservationCreateRequest request) {
    UUID userId = SecurityUtils.requireCurrentUserId();
    ReservationResponse response = reservationService.create(eventId, request.seats(), userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/api/reservations/{id}/confirm")
  public ResponseEntity<ReservationResponse> confirm(@PathVariable UUID id) {
    UUID callerId = SecurityUtils.requireCurrentUserId();
    return ResponseEntity.ok(reservationService.confirm(id, callerId, SecurityUtils.isAdmin()));
  }

  @PostMapping("/api/reservations/{id}/cancel")
  public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id) {
    UUID callerId = SecurityUtils.requireCurrentUserId();
    return ResponseEntity.ok(reservationService.cancel(id, callerId, SecurityUtils.isAdmin()));
  }

  @GetMapping("/api/reservations")
  public ResponseEntity<List<ReservationResponse>> listMine() {
    UUID callerId = SecurityUtils.requireCurrentUserId();
    return ResponseEntity.ok(reservationService.listForUser(callerId));
  }

  @GetMapping("/api/reservations/{id}")
  public ResponseEntity<ReservationResponse> get(@PathVariable UUID id) {
    UUID callerId = SecurityUtils.requireCurrentUserId();
    return ResponseEntity.ok(reservationService.findById(id, callerId, SecurityUtils.isAdmin()));
  }
}
