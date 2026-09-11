package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.dto.ReservationRequest;
import com.ibizabroker.bibliotheque.dto.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.BusinessRuleException;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import com.ibizabroker.bibliotheque.exceptions.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final Set<ReservationStatus> ACTIVE_STATUSES = EnumSet.of(
            ReservationStatus.EN_ATTENTE,
            ReservationStatus.DISPONIBLE
    );

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BooksRepository booksRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @Transactional
    public ReservationResponse create(ReservationRequest request) {
        Integer adherentId = resolveAdherentId(request);

        Books livre = booksRepository.findByIdForUpdate(request.getLivreId())
                .orElseThrow(() -> new NotFoundException(
                        "Livre avec l'identifiant " + request.getLivreId() + " introuvable."
                ));
        Users adherent = usersRepository.findByIdForUpdate(adherentId)
                .orElseThrow(() -> new NotFoundException(
                        "Adhérent avec l'identifiant " + adherentId + " introuvable."
                ));

        // RG-01 : noOfCopies reflète le nombre d'exemplaires actuellement disponibles
        // (décrémenté/incrémenté par le module Borrow). Un livre n'est réservable
        // que s'il n'en reste aucun disponible, quel que soit le nombre total d'exemplaires.
        if (livre.getNoOfCopies() != null && livre.getNoOfCopies() > 0) {
            throw new BusinessRuleException(
                    "RG-01 : le livre doit être indisponible pour être réservé."
            );
        }

        if (reservationRepository.existsByLivreBookIdAndAdherentUserIdAndStatutIn(
                livre.getBookId(), adherent.getUserId(), ACTIVE_STATUSES
        )) {
            throw new BusinessRuleException(
                    "RG-02 : l'adhérent possède déjà une réservation active pour ce livre."
            );
        }

        // RG-03 : les réservations actives de l'adhérent sont verrouillées avant d'être
        // comptées, pour empêcher deux créations concurrentes de dépasser la limite de 3.
        List<Reservation> reservationsActives = reservationRepository
                .findActiveByAdherentForUpdate(adherent.getUserId(), ACTIVE_STATUSES);

        if (reservationsActives.size() >= 3) {
            throw new BusinessRuleException(
                    "RG-03 : l'adhérent ne peut pas dépasser 3 réservations actives simultanées."
            );
        }

        Instant dateReservation = Instant.now();
        Reservation reservation = new Reservation();
        reservation.setLivre(livre);
        reservation.setAdherent(adherent);
        reservation.setDateReservation(dateReservation);
        reservation.setDateExpiration(dateReservation.plus(7, ChronoUnit.DAYS));
        reservation.setStatut(ReservationStatus.EN_ATTENTE);

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(ReservationStatus statut, Integer adherentId) {
        // RS-03 : un Adhérent ne peut lister que ses propres réservations,
        // quel que soit le adherentId demandé en paramètre — écrasé ici,
        // filtré silencieusement, jamais un refus 403 sur la liste.
        Integer adherentEffectif = currentUserService.isAdmin()
                ? adherentId
                : currentUserService.getCurrentUser().getUserId();

        List<Reservation> reservations;
        if (statut != null && adherentEffectif != null) {
            reservations = reservationRepository
                    .findByStatutAndAdherentUserIdOrderByDateReservationAsc(statut, adherentEffectif);
        } else if (statut != null) {
            reservations = reservationRepository.findByStatutOrderByDateReservationAsc(statut);
        } else if (adherentEffectif != null) {
            reservations = reservationRepository
                    .findByAdherentUserIdOrderByDateReservationAsc(adherentEffectif);
        } else {
            reservations = reservationRepository.findAllByOrderByDateReservationAsc();
        }

        return reservations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Integer id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> reservationNotFound(id));
        assertOwnerOrAdmin(reservation);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancel(Integer id) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> reservationNotFound(id));
        assertOwnerOrAdmin(reservation);

        if (!ACTIVE_STATUSES.contains(reservation.getStatut())) {
            throw new BusinessRuleException(
                    "RG-05 : seules les réservations EN_ATTENTE ou DISPONIBLE peuvent être annulées. "
                            + "RG-06 : une réservation " + reservation.getStatut()
                            + " ne peut plus changer d'état."
            );
        }

        reservation.setStatut(ReservationStatus.ANNULEE);
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public void delete(Integer id) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> reservationNotFound(id));
        reservationRepository.delete(reservation);
    }

    private NotFoundException reservationNotFound(Integer id) {
        return new NotFoundException(
                "Réservation avec l'identifiant " + id + " introuvable."
        );
    }

    // RS-04 : un Bibliothécaire (Admin) réserve pour l'adherentId fourni
    // dans le corps ; un Adhérent (User) réserve pour lui-même, le corps
    // est ignoré pour ce champ — l'identité vient exclusivement du token.
    private Integer resolveAdherentId(ReservationRequest request) {
        if (!currentUserService.isAdmin()) {
            return currentUserService.getCurrentUser().getUserId();
        }
        if (request.getAdherentId() == null) {
            Map<String, String> erreurs = new LinkedHashMap<>();
            erreurs.put("adherentId", "adherentId est obligatoire");
            throw new ValidationException("Validation échouée.", erreurs);
        }
        return request.getAdherentId();
    }

    // RS-03/RS-05 : un Adhérent ne peut consulter ou annuler que ses
    // propres réservations ; un Bibliothécaire n'a aucune restriction.
    // Seul point de contrôle de propriété, réutilisé par get() et cancel().
    private void assertOwnerOrAdmin(Reservation reservation) {
        if (currentUserService.isAdmin()) {
            return;
        }
        Integer idAppelant = currentUserService.getCurrentUser().getUserId();
        if (!idAppelant.equals(reservation.getAdherent().getUserId())) {
            throw new AccessDeniedException(
                    "Vous ne pouvez consulter ou annuler que vos propres réservations."
            );
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getLivre().getBookId(),
                reservation.getAdherent().getUserId(),
                reservation.getDateReservation(),
                reservation.getDateExpiration(),
                reservation.getStatut()
        );
    }
}
