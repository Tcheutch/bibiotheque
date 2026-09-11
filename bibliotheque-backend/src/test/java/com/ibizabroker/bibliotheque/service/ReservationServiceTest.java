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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private BooksRepository booksRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private CurrentUserService currentUserService;

    @InjectMocks
    private ReservationService reservationService;

    // Ces tests couvrent les règles métier RG-01..RG-06, indépendantes du
    // rôle de l'appelant ; on fixe donc un contexte Bibliothécaire (Admin),
    // seul cas où create()/cancel() n'ont besoin d'aucune autre interaction
    // avec CurrentUserService (adherentId vient du corps, pas du token ;
    // aucun contrôle de propriété). RS-04/RS-03/RS-05 ont leurs propres
    // tests dédiés (ReservationIntegrationTest).
    @BeforeEach
    void setUpCallerAsAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
    }

    @Test
    void rg01ShouldRejectBookWithAvailableCopies() {
        ReservationRequest request = request(10, 4);
        Books livre = book(10, 1);
        Users adherent = user(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reservationService.create(request)
        );

        assertTrue(exception.getMessage().contains("RG-01"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void rg01ShouldRejectMultiCopyBookWithAtLeastOneCopyStillAvailable() {
        ReservationRequest request = request(10, 4);
        // 3 exemplaires au total, 1 emprunté (noOfCopies décrémenté par le module Borrow) :
        // il en reste 2 disponibles, donc le livre n'est pas réservable.
        Books livre = book(10, 2);
        Users adherent = user(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reservationService.create(request)
        );

        assertTrue(exception.getMessage().contains("RG-01"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void rg01ShouldAllowReservationWhenMultiCopyBookHasNoCopyLeft() {
        ReservationRequest request = request(10, 4);
        // Dernier exemplaire emprunté : plus aucune copie disponible, réservable.
        Books livre = book(10, 0);
        Users adherent = user(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));
        when(reservationRepository.existsByLivreBookIdAndAdherentUserIdAndStatutIn(
                eq(10), eq(4), any(Collection.class)
        )).thenReturn(false);
        when(reservationRepository.findActiveByAdherentForUpdate(
                eq(4), any(Collection.class)
        )).thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId(1);
            return reservation;
        });

        ReservationResponse response = reservationService.create(request);

        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
    }

    @Test
    void rg02ShouldRejectDuplicateActiveReservationForSameBookAndUser() {
        ReservationRequest request = request(10, 4);
        Books livre = book(10, 0);
        Users adherent = user(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));
        when(reservationRepository.existsByLivreBookIdAndAdherentUserIdAndStatutIn(
                eq(10), eq(4), any(Collection.class)
        )).thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reservationService.create(request)
        );

        assertTrue(exception.getMessage().contains("RG-02"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void rg03ShouldRejectFourthActiveReservation() {
        ReservationRequest request = request(10, 4);
        Books livre = book(10, 0);
        Users adherent = user(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));
        when(reservationRepository.existsByLivreBookIdAndAdherentUserIdAndStatutIn(
                eq(10), eq(4), any(Collection.class)
        )).thenReturn(false);
        when(reservationRepository.findActiveByAdherentForUpdate(
                eq(4), any(Collection.class)
        )).thenReturn(List.of(
                reservation(1, ReservationStatus.EN_ATTENTE),
                reservation(2, ReservationStatus.EN_ATTENTE),
                reservation(3, ReservationStatus.DISPONIBLE)
        ));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reservationService.create(request)
        );

        assertTrue(exception.getMessage().contains("RG-03"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void rg03ShouldAllowThirdActiveReservationWhenOnlyTwoActiveExist() {
        ReservationRequest request = request(10, 4);
        Books livre = book(10, 0);
        Users adherent = user(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));
        when(reservationRepository.existsByLivreBookIdAndAdherentUserIdAndStatutIn(
                eq(10), eq(4), any(Collection.class)
        )).thenReturn(false);
        when(reservationRepository.findActiveByAdherentForUpdate(
                eq(4), any(Collection.class)
        )).thenReturn(List.of(
                reservation(1, ReservationStatus.EN_ATTENTE),
                reservation(2, ReservationStatus.DISPONIBLE)
        ));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId(3);
            return reservation;
        });

        ReservationResponse response = reservationService.create(request);

        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void rg04ShouldSetExpirationExactlySevenDaysAfterReservation() {
        ReservationRequest request = request(10, 4);
        Books livre = book(10, 0);
        Users adherent = user(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));
        when(reservationRepository.existsByLivreBookIdAndAdherentUserIdAndStatutIn(
                eq(10), eq(4), any(Collection.class)
        )).thenReturn(false);
        when(reservationRepository.findActiveByAdherentForUpdate(
                eq(4), any(Collection.class)
        )).thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId(1);
            return reservation;
        });

        ReservationResponse response = reservationService.create(request);

        assertEquals(
                Duration.ofDays(7),
                Duration.between(response.getDateReservation(), response.getDateExpiration())
        );
        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
    }

    @Test
    void rg05ShouldAllowCancellationFromEnAttente() {
        Reservation reservation = reservation(8, ReservationStatus.EN_ATTENTE);

        when(reservationRepository.findByIdForUpdate(8)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.cancel(8);

        assertEquals(ReservationStatus.ANNULEE, response.getStatut());
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"ANNULEE", "EXPIREE", "HONOREE"})
    void rg06ShouldRejectChangesFromTerminalStatus(ReservationStatus terminalStatus) {
        Reservation reservation = reservation(8, terminalStatus);
        when(reservationRepository.findByIdForUpdate(8)).thenReturn(Optional.of(reservation));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reservationService.cancel(8)
        );

        assertTrue(exception.getMessage().contains("RG-06"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    private ReservationRequest request(Integer livreId, Integer adherentId) {
        ReservationRequest request = new ReservationRequest();
        request.setLivreId(livreId);
        request.setAdherentId(adherentId);
        return request;
    }

    private Books book(Integer id, Integer copies) {
        Books livre = new Books();
        livre.setBookId(id);
        livre.setBookName("Livre test " + id);
        livre.setNoOfCopies(copies);
        return livre;
    }

    private Users user(Integer id) {
        Users adherent = new Users();
        adherent.setUserId(id);
        adherent.setUsername("user" + id);
        return adherent;
    }

    private Reservation reservation(Integer id, ReservationStatus status) {
        Books livre = book(10, 0);
        Users adherent = user(4);
        Instant now = Instant.now();

        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setLivre(livre);
        reservation.setAdherent(adherent);
        reservation.setDateReservation(now);
        reservation.setDateExpiration(now.plus(Duration.ofDays(7)));
        reservation.setStatut(status);
        return reservation;
    }
}
