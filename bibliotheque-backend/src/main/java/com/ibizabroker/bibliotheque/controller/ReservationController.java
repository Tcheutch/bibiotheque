package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dto.ReservationRequest;
import com.ibizabroker.bibliotheque.dto.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Réservations", description = "Gestion des réservations de livres")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @PostMapping
    @Operation(summary = "Créer une réservation")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Réservation créée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Authentification requise"),
            @ApiResponse(responseCode = "404", description = "Livre ou adhérent introuvable"),
            @ApiResponse(responseCode = "409", description = "Règle métier non respectée")
    })
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody ReservationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(request));
    }

    @GetMapping
    @Operation(summary = "Lister les réservations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservations retournées"),
            @ApiResponse(responseCode = "400", description = "Filtre invalide"),
            @ApiResponse(responseCode = "401", description = "Authentification requise")
    })
    public ResponseEntity<List<ReservationResponse>> list(
            @RequestParam(required = false) ReservationStatus statut,
            @RequestParam(required = false) Integer adherentId
    ) {
        return ResponseEntity.ok(reservationService.list(statut, adherentId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une réservation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation retournée"),
            @ApiResponse(responseCode = "401", description = "Authentification requise"),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable")
    })
    public ResponseEntity<ReservationResponse> get(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.get(id));
    }

    @PatchMapping("/{id}/annuler")
    @Operation(summary = "Annuler une réservation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réservation annulée"),
            @ApiResponse(responseCode = "401", description = "Authentification requise"),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable"),
            @ApiResponse(responseCode = "409", description = "Règle métier non respectée")
    })
    public ResponseEntity<ReservationResponse> cancel(@PathVariable Integer id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une réservation")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Réservation supprimée"),
            @ApiResponse(responseCode = "401", description = "Authentification requise"),
            @ApiResponse(responseCode = "404", description = "Réservation introuvable")
    })
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
