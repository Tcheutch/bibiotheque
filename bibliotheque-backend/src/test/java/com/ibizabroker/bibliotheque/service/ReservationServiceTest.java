package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.dto.ReservationRequest;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BooksRepository booksRepository;

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void shouldRejectFourthActiveReservationAccordingToRg03() {
        ReservationRequest request = new ReservationRequest();
        request.setLivreId(10);
        request.setAdherentId(4);

        Books livre = new Books();
        livre.setBookId(10);
        livre.setNoOfCopies(0);

        Users adherent = new Users();
        adherent.setUserId(4);

        when(booksRepository.findByIdForUpdate(10)).thenReturn(Optional.of(livre));
        when(usersRepository.findByIdForUpdate(4)).thenReturn(Optional.of(adherent));
        when(reservationRepository.existsByLivreBookIdAndAdherentUserIdAndStatutIn(
                eq(10), eq(4), any(Collection.class)
        )).thenReturn(false);
        when(reservationRepository.countByAdherentUserIdAndStatutIn(
                eq(4), any(Collection.class)
        )).thenReturn(3L);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reservationService.create(request)
        );

        assertTrue(exception.getMessage().contains("RG-03"));
        verify(reservationRepository, never()).save(any(Reservation.class));
    }
}
