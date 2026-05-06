package com.inghubscl.ticketing.event;

import com.inghubscl.ticketing.common.PagedResponse;
import com.inghubscl.ticketing.event.dto.EventCreateRequest;
import com.inghubscl.ticketing.event.dto.EventResponse;
import com.inghubscl.ticketing.event.dto.EventUpdateRequest;
import com.inghubscl.ticketing.event.dto.PublicEventResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface EventService {

  EventResponse create(EventCreateRequest request, UUID ownerId);

  EventResponse update(UUID id, EventUpdateRequest request, UUID callerId, boolean callerIsAdmin);

  EventResponse publish(UUID id, UUID callerId, boolean callerIsAdmin);

  EventResponse findById(UUID id);

  PagedResponse<EventResponse> list(UUID ownerId, Pageable pageable);

  PagedResponse<PublicEventResponse> searchPublic(
      Instant from, Instant to, String query, Pageable pageable);
}
