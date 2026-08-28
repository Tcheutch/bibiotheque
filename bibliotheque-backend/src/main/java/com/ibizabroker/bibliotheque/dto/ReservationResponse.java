package com.ibizabroker.bibliotheque.dto;

import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ReservationResponse {
    private Integer id;
    private Integer livreId;
    private Integer adherentId;
    private Instant dateReservation;
    private Instant dateExpiration;
    private ReservationStatus statut;
}
