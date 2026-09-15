package com.ibizabroker.bibliotheque.service;

import com.ibizabroker.bibliotheque.dao.BooksRepository;
import com.ibizabroker.bibliotheque.dao.ReservationRepository;
import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Books;
import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import com.ibizabroker.bibliotheque.entity.Role;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// RS-01/RS-03 vérifiées par un vrai flux HTTP (TestRestTemplate, sur un
// port réel, SecurityFilterChain réellement active — ni mockée ni
// contournée) : un vrai POST /authenticate obtient un token, réutilisé
// dans l'en-tête Authorization de chaque appel suivant.
//
// Précision méthodologique (voir docs/pr-description-draft.md) : le sujet
// demande le 3e cas (403 sur la donnée d'un autre) sur GET /api/reservations,
// mais cet endpoint ne renvoie jamais 403 par conception — il filtre
// silencieusement (RS-03). Le seul endpoint qui renvoie légitimement 403
// sur la donnée d'un autre est GET /{id}. Les deux comportements sont donc
// couverts ici : la liste (silencieuse, testée) et /{id} (403 explicite).
//
// Pas de @Transactional ici : TestRestTemplate exécute la requête sur un
// thread serveur séparé, qui ne verrait pas les données créées dans une
// transaction de test non validée. Les données sont donc créées et
// supprimées directement (comme scripts/reservation/*.sql), pas via un
// rollback de test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ReservationSecurityIntegrationTest {

    private static final String RAW_PASSWORD = "Test1234!";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UsersRepository usersRepository;
    @Autowired private BooksRepository booksRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired private PlatformTransactionManager transactionManager;

    private Users adherentA;
    private Users adherentB;
    private Users bibliothecaire;
    private Books livre;
    private Reservation reservationDeA;

    @BeforeEach
    void creerLeJeuDeDonnees() {
        long suffixe = System.nanoTime();
        adherentA = creerUtilisateur("it_sec_a1_" + suffixe, "User");
        adherentB = creerUtilisateur("it_sec_a2_" + suffixe, "User");
        bibliothecaire = creerUtilisateur("it_sec_biblio_" + suffixe, "Admin");

        livre = new Books();
        livre.setBookName("[IT-SEC] Livre " + suffixe);
        livre.setBookAuthor("Integration Test");
        livre.setBookGenre("TEST");
        livre.setNoOfCopies(0);
        livre = booksRepository.save(livre);

        Instant maintenant = Instant.now();
        reservationDeA = new Reservation();
        reservationDeA.setLivre(livre);
        reservationDeA.setAdherent(adherentA);
        reservationDeA.setDateReservation(maintenant);
        reservationDeA.setDateExpiration(maintenant.plus(7, ChronoUnit.DAYS));
        reservationDeA.setStatut(ReservationStatus.EN_ATTENTE);
        reservationDeA = reservationRepository.save(reservationDeA);
    }

    // Nettoyage en SQL natif comme scripts/reservation/03_cleanup_scenario.sql :
    // Users.role est en cascade=ALL (préexistant, hors périmètre RS-01..RS-05)
    // et supprimerait aussi la ligne Role partagée si on passait par
    // usersRepository.delete(...) — d'où le DELETE ciblé sur user_role seul.
    @AfterEach
    void nettoyerLeJeuDeDonnees() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (reservationDeA != null) {
                entityManager.createQuery("DELETE FROM Reservation r WHERE r.id = :id")
                        .setParameter("id", reservationDeA.getId())
                        .executeUpdate();
            }
            if (livre != null) {
                entityManager.createQuery("DELETE FROM Books b WHERE b.bookId = :id")
                        .setParameter("id", livre.getBookId())
                        .executeUpdate();
            }
            for (Users adherent : new Users[]{adherentA, adherentB, bibliothecaire}) {
                if (adherent == null) {
                    continue;
                }
                entityManager.createNativeQuery("DELETE FROM user_role WHERE user_id = :id")
                        .setParameter("id", adherent.getUserId())
                        .executeUpdate();
                entityManager.createQuery("DELETE FROM Users u WHERE u.userId = :id")
                        .setParameter("id", adherent.getUserId())
                        .executeUpdate();
            }
        });
    }

    @Test
    void listWithoutToken_shouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/reservations", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Authentification requise"), response.getBody());
    }

    @Test
    void listWithUserToken_shouldReturn200AndOnlyOwnReservations() {
        String tokenB = authenticate(adherentB.getUsername());

        ResponseEntity<String> response = get("/api/reservations", tokenB);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // RS-03 : B n'a aucune réservation propre ; la réservation de A
        // (créée dans le setup) existe bien en base mais n'apparaît pas —
        // filtrage silencieux, pas un refus.
        assertEquals("[]", response.getBody() == null ? null : response.getBody().trim());
    }

    @Test
    void getOwnReservationWithUserToken_shouldReturn200() {
        String tokenA = authenticate(adherentA.getUsername());

        ResponseEntity<String> response = get("/api/reservations/" + reservationDeA.getId(), tokenA);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getSomeoneElsesReservationWithUserToken_shouldReturn403() {
        String tokenB = authenticate(adherentB.getUsername());

        ResponseEntity<String> response = get("/api/reservations/" + reservationDeA.getId(), tokenB);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("propres réservations"));
    }

    // RS-02 : DELETE est réservé au Bibliothécaire (@PreAuthorize sur
    // ReservationController.delete(), même patron que AdminController).
    @Test
    void deleteWithUserToken_shouldReturn403() {
        String tokenB = authenticate(adherentB.getUsername());

        ResponseEntity<String> response = delete("/api/reservations/" + reservationDeA.getId(), tokenB);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        // Confirmé empiriquement (corps observé : {"timestamp":...,"status":403,
        // "message":"Access is denied","path":"...","errors":{}}) : c'est la
        // forme ApiError de ReservationExceptionHandler, pas le corps par
        // défaut de Spring Boot ({"error":"Forbidden",...}, sans "errors").
        // Le @PreAuthorize échoue à l'intérieur du dispatch MVC et est capté
        // par ReservationExceptionHandler avant d'atteindre
        // ExceptionTranslationFilter — JwtAccessDeniedHandler ne sert donc
        // jamais pour ce module (utile ailleurs : AdminController/BooksController).
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"errors\""));
    }

    // RS-04 : adherentId reste obligatoire pour un Bibliothécaire (comportement
    // antérieur inchangé) ; le champ manquant doit être nommé explicitement
    // dans la réponse, pas un message générique — vérifié sur le corps réel.
    @Test
    void createAsAdminWithoutAdherentId_shouldReturn400NamingTheField() {
        String tokenAdmin = authenticate(bibliothecaire.getUsername());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String corps = "{\"livreId\":" + livre.getBookId() + "}";

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/reservations", HttpMethod.POST, new HttpEntity<>(corps, headers), String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"adherentId\":\"adherentId est obligatoire\""));
    }

    @Test
    void deleteWithAdminToken_shouldReturn204() {
        String tokenAdmin = authenticate(bibliothecaire.getUsername());

        ResponseEntity<String> response = delete("/api/reservations/" + reservationDeA.getId(), tokenAdmin);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    // Expiration du token : toujours un 401 (RS-01), mais avec un message qui
    // dit pourquoi — distinct du token absent et du token invalide — pour que
    // l'écran de connexion puisse l'afficher tel quel.
    @Test
    void listWithExpiredToken_shouldReturn401WithSessionExpiredMessage() {
        String tokenExpire = tokenExpirePour(adherentA.getUsername());

        ResponseEntity<String> response = get("/api/reservations", tokenExpire);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Votre session a expiré"), response.getBody());
    }

    @Test
    void listWithMalformedToken_shouldReturn401WithInvalidTokenMessage() {
        ResponseEntity<String> response = get("/api/reservations", "pas.un.jwt");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Token invalide"), response.getBody());
    }

    // Journalisation des refus : chaque 401/403 laisse une ligne WARN
    // "ACCES_REFUSE" (statut, méthode, chemin, IP, utilisateur, raison),
    // jamais le token lui-même.
    @Test
    void expiredTokenRefusal_shouldBeLoggedAs401WithReasonButNeverTheToken(CapturedOutput output) {
        String tokenExpire = tokenExpirePour(adherentA.getUsername());

        get("/api/reservations", tokenExpire);

        String ligne = ligneAccesRefuse(output, "status=401");
        assertTrue(ligne.contains("methode=GET"), ligne);
        assertTrue(ligne.contains("chemin=/api/reservations"), ligne);
        assertTrue(ligne.contains("utilisateur=anonyme"), ligne);
        assertTrue(ligne.contains("TOKEN_EXPIRE"), ligne);
        assertFalse(output.getOut().contains(tokenExpire), "le token ne doit jamais être journalisé");
    }

    @Test
    void accessToSomeoneElsesReservation_shouldBeLoggedAs403WithCallerUsername(CapturedOutput output) {
        String tokenB = authenticate(adherentB.getUsername());

        get("/api/reservations/" + reservationDeA.getId(), tokenB);

        String ligne = ligneAccesRefuse(output, "status=403");
        assertTrue(ligne.contains("utilisateur=" + adherentB.getUsername()), ligne);
        assertTrue(ligne.contains("chemin=/api/reservations/" + reservationDeA.getId()), ligne);
    }

    // Hors module Réservation : un 403 de @PreAuthorize sur AdminController
    // passe par JwtAccessDeniedHandler (pas de @RestControllerAdvice dédié),
    // qui doit journaliser de la même façon.
    @Test
    void adminEndpointWithUserToken_shouldReturn403AndBeLogged(CapturedOutput output) {
        String tokenB = authenticate(adherentB.getUsername());

        ResponseEntity<String> response = get("/admin/users", tokenB);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        String ligne = ligneAccesRefuse(output, "status=403");
        assertTrue(ligne.contains("chemin=/admin/users"), ligne);
        assertTrue(ligne.contains("utilisateur=" + adherentB.getUsername()), ligne);
    }

    // Le fetch du rôle et la sauvegarde de l'utilisateur doivent partager la
    // même transaction : sinon le Role revient détaché entre les deux (pas
    // de transaction ambiante ici), et le cascade PERSIST de Users.role
    // échoue (PersistentObjectException: detached entity passed to persist).
    private Users creerUtilisateur(String username, String roleName) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Users user = new Users();
            user.setUsername(username);
            user.setName(username);
            user.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            Set<Role> roles = new HashSet<>();
            roles.add(role(roleName));
            user.setRole(roles);
            return usersRepository.save(user);
        });
    }

    private Role role(String roleName) {
        return entityManager
                .createQuery("SELECT r FROM Role r WHERE r.roleName = :nom", Role.class)
                .setParameter("nom", roleName)
                .setMaxResults(1)
                .getSingleResult();
    }

    private String authenticate(String username) {
        Map<String, String> body = Map.of("username", username, "password", RAW_PASSWORD);
        ResponseEntity<Map> response = restTemplate.postForEntity("/authenticate", body, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Object token = response.getBody() == null ? null : response.getBody().get("jwtToken");
        assertNotNull(token, "jwtToken absent de la réponse /authenticate : " + response.getBody());
        return token.toString();
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> delete(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    // Même signature que JwtUtil.generateToken (HS512, même clé), mais déjà
    // expiré. La clé est lue par réflexion plutôt que recopiée ici, pour ne
    // pas diverger si elle change.
    private String tokenExpirePour(String username) {
        String cle = (String) ReflectionTestUtils.getField(JwtUtil.class, "SECRET_KEY");
        long maintenant = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(maintenant - 7_200_000))
                .setExpiration(new Date(maintenant - 3_600_000))
                .signWith(SignatureAlgorithm.HS512, cle)
                .compact();
    }

    private static String ligneAccesRefuse(CapturedOutput output, String statut) {
        return output.getOut().lines()
                .filter(ligne -> ligne.contains("ACCES_REFUSE") && ligne.contains(statut))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune ligne ACCES_REFUSE " + statut + " journalisée"));
    }
}
