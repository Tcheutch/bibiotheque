package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.BorrowRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.dto.ReservationRequest;
import com.ibizabroker.bibliotheque.dto.ReservationResponse;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Borrow;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ReservationIntegrationTest {

    @Autowired private ReservationService reservationService;
    @Autowired private BooksRepository booksRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private BorrowRepository borrowRepository;

    // Ces deux tests couvrent RG-01/RG-04, indépendantes du rôle de
    // l'appelant : contexte Bibliothécaire (Admin) fixé ici, seul cas où
    // create() n'a besoin d'aucun autre appel à CurrentUserService
    // (adherentId vient du corps, pas du token). Sécurité RS-01/RS-02/RS-03
    // couverte par ReservationSecurityIntegrationTest (vrai flux HTTP).
    @BeforeEach
    void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "it_res_admin", null, List.of(new SimpleGrantedAuthority("ROLE_Admin"))
                )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional
    void rg01ShouldRejectAvailableBookWithoutActiveBorrow() {
        Books livre = saveBook("[IT-RES] L1 disponible", 1);
        Users adherent = saveUser("it_res_a1");

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(livre.getBookId());
        request.setAdherentId(adherent.getUserId());

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reservationService.create(request)
        );

        assertTrue(exception.getMessage().contains("RG-01"));
    }

    @Test
    @Transactional
    void shouldCreateReservationForBorrowedBookAndApplyRg04() {
        Books livre = saveBook("[IT-RES] L2 emprunte", 0);
        Users emprunteur = saveUser("it_res_a3");
        Users reservataire = saveUser("it_res_a1_rg04");

        Borrow borrow = new Borrow();
        borrow.setBookId(livre.getBookId());
        borrow.setUserId(emprunteur.getUserId());
        borrow.setIssueDate(new Date());
        borrow.setDueDate(Date.from(Instant.now().plus(Duration.ofDays(14))));
        borrow.setReturnDate(null);
        borrowRepository.save(borrow);

        ReservationRequest request = new ReservationRequest();
        request.setLivreId(livre.getBookId());
        request.setAdherentId(reservataire.getUserId());

        ReservationResponse response = reservationService.create(request);

        assertEquals(ReservationStatus.EN_ATTENTE, response.getStatut());
        assertEquals(
                Duration.ofDays(7),
                Duration.between(response.getDateReservation(), response.getDateExpiration())
        );
    }

    private Books saveBook(String name, Integer copies) {
        Books book = new Books();
        book.setBookName(name);
        book.setBookAuthor("Integration Test");
        book.setBookGenre("TEST");
        book.setNoOfCopies(copies);
        return booksRepository.save(book);
    }

    private Users saveUser(String username) {
        Users user = new Users();
        user.setUsername(username);
        user.setName(username);
        user.setPassword("TEST_ONLY");
        return usersRepository.save(user);
    }
}
