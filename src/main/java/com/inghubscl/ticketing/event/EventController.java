package com.inghubscl.ticketing.event;

import com.inghubscl.ticketing.common.PagedResponse;
import com.inghubscl.ticketing.common.SecurityUtils;
import com.inghubscl.ticketing.event.dto.EventCreateRequest;
import com.inghubscl.ticketing.event.dto.EventResponse;
import com.inghubscl.ticketing.event.dto.EventUpdateRequest;
import com.inghubscl.ticketing.event.dto.PublicEventResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

  private final EventService eventService;

  public EventController(EventService eventService) {
    this.eventService = eventService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  public ResponseEntity<EventResponse> create(@Valid @RequestBody EventCreateRequest request) {
    UUID ownerId = SecurityUtils.requireCurrentUserId();
    EventResponse response = eventService.create(request, ownerId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  public ResponseEntity<EventResponse> update(
      @PathVariable UUID id, @Valid @RequestBody EventUpdateRequest request) {
    UUID callerId = SecurityUtils.requireCurrentUserId();
    EventResponse response = eventService.update(id, request, callerId, SecurityUtils.isAdmin());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  public ResponseEntity<EventResponse> publish(@PathVariable UUID id) {
    UUID callerId = SecurityUtils.requireCurrentUserId();
    EventResponse response = eventService.publish(id, callerId, SecurityUtils.isAdmin());
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<PagedResponse<EventResponse>> list(
      @RequestParam(required = false) UUID ownerId, Pageable pageable) {
    return ResponseEntity.ok(eventService.list(ownerId, pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<EventResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(eventService.findById(id));
  }

  @GetMapping("/public")
  public ResponseEntity<PagedResponse<PublicEventResponse>> publicSearch(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false, name = "q") String query,
      Pageable pageable) {
    return ResponseEntity.ok(eventService.searchPublic(from, to, query, pageable));
  }
}
