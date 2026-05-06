package com.inghubscl.ticketing.event;

import com.inghubscl.ticketing.event.dto.EventResponse;
import com.inghubscl.ticketing.event.dto.PublicEventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper
public interface EventMapper {

  @Mapping(target = "availableSeats", source = "event", qualifiedByName = "calcAvailable")
  EventResponse toResponse(Event event);

  @Mapping(target = "availableSeats", source = "event", qualifiedByName = "calcAvailable")
  PublicEventResponse toPublicResponse(Event event);

  @Named("calcAvailable")
  default int calcAvailable(Event event) {
    return event.getCapacity() - event.getReservedSeats();
  }
}
