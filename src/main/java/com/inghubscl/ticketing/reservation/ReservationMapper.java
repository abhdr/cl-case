package com.inghubscl.ticketing.reservation;

import com.inghubscl.ticketing.reservation.dto.ReservationResponse;
import org.mapstruct.Mapper;

@Mapper
public interface ReservationMapper {

  ReservationResponse toResponse(Reservation reservation);
}
