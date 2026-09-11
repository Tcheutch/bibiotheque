package com.ibizabroker.bibliotheque.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ReservationRequest {

    @NotNull(message = "livreId est obligatoire")
    private Integer livreId;

    // Obligatoire seulement pour un Bibliothécaire (RS-04) ; ignoré pour un
    // Adhérent, dont l'identité vient du token. La contrainte dépend du
    // rôle, donc elle est vérifiée dans ReservationService, pas ici.
    private Integer adherentId;
}
