# Brouillon de description de Pull Request — Sécurité du module Réservation

Séance 4. Branche `feature/reservation-securite-omer-donat-tcheutchoua-fossi`,
depuis `feature/reservation-ui-donat-omer-tcheutchoua-fossi`.

## Résumé

Sécurise les cinq endpoints `/api/reservations` par rôle : authentification
obligatoire (déjà couverte, reconfirmée), autorisations par rôle (`DELETE`
réservé au Bibliothécaire), un Adhérent ne voit et ne modifie que ses
propres réservations, l'identité vient du token — jamais du corps de la
requête. Adapte l'écran `/reservations` pour qu'un compte `User` puisse
l'utiliser (sélecteur "Adhérent" retiré, pas seulement caché). Corrige au
passage un bug préexistant de `roleMatch()` (paragraphe dédié plus bas),
révélé par ce travail sans en faire partie.

Traite aussi les trois points « Si vous finissez en avance » (section
dédiée plus bas) : message explicite sur un token expiré, de bout en bout
(backend + écran de connexion) ; journalisation des accès refusés
(401/403) ; le test sur RG-01 existait déjà depuis la séance 3, il est cité
avec sa preuve.

## Correspondance de rôles (rappel explicite)

Le sujet de la séance utilise `ADHERENT` et `BIBLIOTHECAIRE` ; ce projet a
déjà un système de rôles (`Role.roleName` = `"User"`/`"Admin"`, utilisé
depuis toujours par `AdminController`). Correspondance appliquée sans
nouvelle valeur de rôle ni renommage : **`ADHERENT` = `User`**,
**`BIBLIOTHECAIRE` = `Admin`**. Documentée en commentaire d'en-tête de
`ReservationController.java` (avant la déclaration de classe).

## Correctif hors périmètre strict — `roleMatch()` (`users.service.ts`)

Même traitement que le correctif d'`auth.interceptor.ts` en séance 3: fichier
partagé, en dehors du périmètre Réservation, signalé en évidence plutôt que
noyé dans la liste des fichiers modifiés.

**Cause exacte** (`bibliotheque-frontend/src/app/_service/users.service.ts:29`,
avant correctif) : `roleMatch()` itérait sur `allowedRoles` avec une boucle
imbriquée dont la branche `else` retournait **au premier essai raté**, sans
jamais regarder le reste du tableau :

```ts
for (let j = 0; j < allowedRoles.length; j++) {
  if (userRoles[i].roleName === allowedRoles[j]) { return true; }
  else { return false; }   // <-- sort ici, n'essaie jamais allowedRoles[1..]
}
```

Resté invisible tant que **toutes** les routes existantes utilisaient un
tableau à une seule entrée (`['Admin']` ou `['User']`) — le premier (et
seul) essai suffisait toujours. Révélé quand ce travail élargit le garde de
`/reservations` à `data:{roles:['Admin','User']}`
(`app-routing.module.ts:29`) : un compte `User` était redirigé vers
`/forbidden` malgré ce garde, car `'User'` comparé en premier à `'Admin'`
faisait sortir la fonction avant d'atteindre `'User'` dans le tableau.
**Constaté empiriquement** (capture d'écran, scénario ci-dessous) avant
correction, puis corrigé et reconfirmé.

**Correctif** (`users.service.ts:29-38`) : remplace la double boucle par
`userRoles.some(...)`, qui teste bien tout `allowedRoles` ; ajoute une garde
`Array.isArray(allowedRoles)` (`route.data["roles"] as Array<string>` n'est
un tableau que par convention TypeScript non vérifiée à l'exécution, pas une
garantie runtime).

**Preuve qu'aucune route existante n'a changé de comportement** : les 8
appels à `roleMatch()` du reste de l'app passent toujours un tableau à une
seule entrée (`header.component.html` : 6 appels littéraux `['Admin']`/
`['User']` ; `auth.guard.ts:25` : `route.data["roles"]`, un tableau à une
entrée sur toutes les routes sauf `/reservations`) — le nouveau code les
traite identiquement à l'ancien. Confirmé par la suite Karma complète,
avant et après correctif : **19 FAILED, 67 SUCCESS, 86 total, à l'identique**
(les mêmes 19 échecs préexistants, nommément vérifiés).

Commit séparé : `8d4759e fix(auth): roleMatch() sur tout allowedRoles, pas
seulement le premier essai`.

## Règles métier (RS) — où chacune est implémentée, avec preuve

- **RS-01** (authentification exigée sur tous les endpoints) — déjà couverte
  par `anyRequest().authenticated()` (`WebSecurityConfiguration.java:55`),
  reconfirmée par un test réel plutôt que supposée :
  `ReservationSecurityIntegrationTest.listWithoutToken_shouldReturn401()`.
- **RS-02** (autorisations par rôle) — `DELETE /{id}` réservé au
  Bibliothécaire via `@PreAuthorize("hasRole('Admin')")`
  (`ReservationController.java:97`, même patron que `AdminController`).
  Confirmé par `deleteWithUserToken_shouldReturn403()` et
  `deleteWithAdminToken_shouldReturn204()`.
- **RS-03** (un Adhérent ne voit que ses réservations) — `list()`
  (`ReservationService.java:102`) force `adherentEffectif` à l'id de
  l'appelant si non-Admin, quel que soit le paramètre demandé : filtrage
  silencieux, jamais un 403 sur la liste. Confirmé par
  `listWithUserToken_shouldReturn200AndOnlyOwnReservations()` **et** par le
  scénario navigateur réel ci-dessous.
- **RS-04** (l'identité vient du token, pas du corps) — `resolveAdherentId()`
  (`ReservationService.java:170`) : Adhérent → `CurrentUserService
  .getCurrentUser()` (`CurrentUserService.java:22`), corps ignoré pour ce
  champ ; Bibliothécaire → `adherentId` du corps, toujours obligatoire
  (`ValidationException` nommant le champ si absent, confirmé par
  `createAsAdminWithoutAdherentId_shouldReturn400NamingTheField`). Côté
  frontend, `afficherSelecteurAdherent`
  (`reservation-form.component.ts:17,51-58`) retire le champ du corps
  envoyé par un Adhérent, pas seulement du formulaire visible. Confirmé par
  le corps réseau réel capturé dans le scénario ci-dessous.
- **RS-05** (un Adhérent ne modifie que ses réservations) — `get()`/
  `cancel()` (`ReservationService.java:129,137`) appellent tous les deux
  `assertOwnerOrAdmin()` (`ReservationService.java:185`), point de contrôle
  unique. Confirmé par `getOwnReservationWithUserToken_shouldReturn200()`
  et `getSomeoneElsesReservationWithUserToken_shouldReturn403()`.
- **Distinction 401/403** — `JwtAuthenticationEntryPoint` (401, pas de
  token, token invalide ou expiré — message propre à chaque cas depuis les
  ajouts « en avance », voir plus bas) et `JwtAccessDeniedHandler` (403, câblé dans
  `WebSecurityConfiguration.java:58-59`) sont deux composants distincts.
  **Précision empirique** (corps de réponse observé sur le 403 de `DELETE`
  par un `User` : `{"timestamp":...,"status":403,"message":"Access is
  denied","path":"...","errors":{}}`) : c'est en réalité
  **`ReservationExceptionHandler`** (`@ExceptionHandler(AccessDeniedException
  .class)`) qui produit tous les 403 du module Réservation — la forme
  `ApiError` (champ `errors` présent) le prouve, ce n'est pas le corps par
  défaut de Spring Boot que produirait `JwtAccessDeniedHandler`.
  `@RestControllerAdvice(assignableTypes = ReservationController.class)`
  intercepte l'exception dans le dispatch MVC avant qu'elle n'atteigne
  `ExceptionTranslationFilter`. `JwtAccessDeniedHandler` reste utile
  ailleurs (`AdminController`, `BooksController`, sans `@RestControllerAdvice`
  dédié). Un futur lecteur qui cherche pourquoi un 403 Réservation a cette
  forme de corps doit regarder `ReservationExceptionHandler`, pas
  `JwtAccessDeniedHandler`.

## « Si vous finissez en avance » — les trois points

### Expiration du token et message associé

- **Backend** — `JwtRequestFilter.java:46-49` : un token expiré
  (`ExpiredJwtException`) ou invalide (toute autre `JwtException`) ne
  produit plus un simple `System.out.println`. Le filtre pose le motif
  (`TOKEN_EXPIRE` / `TOKEN_INVALIDE`) en attribut de requête et laisse la
  requête continuer sans authentification : un endpoint public reste
  accessible (vérifié : `/borrow` répond 200 avec un token malformé).
  `JwtAuthenticationEntryPoint.java:28,47-65` renvoie alors un
  401 au format `ApiError`, avec un message qui dit pourquoi :
  - token expiré : « Votre session a expiré. Veuillez vous reconnecter. »
  - token invalide : « Token invalide. Veuillez vous reconnecter. »
  - pas de token : « Authentification requise. Veuillez vous connecter. »

  Avant : le même `sendError(401, "Unauthorized")` dans les trois cas, sans
  message exploitable côté client.
- **Frontend** — sur un 401, `auth.interceptor.ts:30-36` vide la session
  locale (sinon `AuthGuard` laisserait encore passer avec le token expiré
  resté dans le `localStorage`) et redirige vers `/login` en transmettant
  le message du serveur tel quel dans l'état de navigation, avec un message
  de repli si le corps n'en contient pas (`auth.interceptor.ts:8,49`).
  `LoginComponent` le lit sur la navigation en cours
  (`login.component.ts:17,23`) et l'affiche dans une alerte
  (`login.component.html:3`). Le 403 et la propagation du
  `HttpErrorResponse` d'origine sont inchangés.
- **Preuve** — backend :
  `listWithExpiredToken_shouldReturn401WithSessionExpiredMessage` (token
  signé avec la vraie clé mais déjà expiré),
  `listWithMalformedToken_shouldReturn401WithInvalidTokenMessage`, et
  `listWithoutToken_shouldReturn401`, qui vérifie maintenant aussi le
  message. Frontend : 2 tests d'intercepteur (message du serveur ou message
  de repli, session vidée dans les deux cas) et 2 tests `LoginComponent`
  (alerte affichée, pas d'alerte sans message). **Vérifié aussi sur un
  backend démarré** (`curl` avec `Origin: http://localhost:4200`) : le 401
  porte `Access-Control-Allow-Origin` et le JSON du message. Sans cet
  en-tête, le navigateur n'aurait pas laissé l'écran lire le message.
- **Limite assumée** — rien ne se déclenche à l'instant exact de
  l'expiration (token valable 5 h, `JwtUtil.java:19`) : le message
  apparaît à la requête suivante vers l'API.

### Test sur RG-01 (réservation d'un livre disponible)

**Déjà présent avant cette branche, pas ajouté ici.**
`rg01ShouldRejectBookWithAvailableCopies` (`ReservationServiceTest`,
repository simulé) et `rg01ShouldRejectAvailableBookWithoutActiveBorrow`
(`ReservationIntegrationTest`, PostgreSQL réel) vérifient qu'un livre
disponible est refusé avec `RG-01` et que rien n'est enregistré. Deux cas
multi-exemplaires les complètent dans `ReservationServiceTest` (une copie
restante → refus ; plus aucune copie → réservation acceptée). Introduits en
séance 3 (commits `93e2f46` et `ae91741`), toujours verts : 4/4 relancés
isolément, et inclus dans le 26/26 ci-dessous.

### Journalisation des tentatives d'accès refusées

Nouveau `SecurityAuditLogger` (`SecurityAuditLogger.java:22`) : une ligne
`WARN` par refus, au format clé=valeur, **sans jamais le token**. Il est
appelé par les trois seuls points qui produisent un refus :
`JwtAuthenticationEntryPoint` (401), `JwtAccessDeniedHandler` (403 hors
Réservation, `JwtAccessDeniedHandler.java:24`) et
`ReservationExceptionHandler` (403 Réservation,
`ReservationExceptionHandler.java:92`). Les retours à la ligne sont
neutralisés dans les valeurs, pour qu'un chemin forgé ne puisse pas injecter
de fausses lignes. Lignes réelles relevées pendant les tests :

```
ACCES_REFUSE status=401 methode=GET chemin=/api/reservations ip=127.0.0.1 utilisateur=anonyme motif="TOKEN_EXPIRE"
ACCES_REFUSE status=403 methode=GET chemin=/api/reservations/112 ip=127.0.0.1 utilisateur=it_sec_a2_7213239477496 motif="Vous ne pouvez consulter ou annuler que vos propres réservations."
ACCES_REFUSE status=403 methode=GET chemin=/admin/users ip=127.0.0.1 utilisateur=it_sec_a2_7214364532054 motif="Access is denied"
```

Preuve : trois tests lisent la sortie réelle via `OutputCaptureExtension`.
`expiredTokenRefusal_shouldBeLoggedAs401WithReasonButNeverTheToken` vérifie
aussi que le token n'apparaît nulle part dans la sortie.
`accessToSomeoneElsesReservation_shouldBeLoggedAs403WithCallerUsername`
couvre le 403 Réservation, et
`adminEndpointWithUserToken_shouldReturn403AndBeLogged` le chemin
`JwtAccessDeniedHandler`. Pour un 401, le journal indique
`utilisateur=anonyme` : l'identité contenue dans un token expiré n'est pas
reprise.

### Fichiers partagés modifiés hors du module Réservation

Même traitement que `auth.interceptor.ts` (séance 3) et `roleMatch()`
(ci-dessus) : signalés en évidence. Backend : `JwtRequestFilter`,
`JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`. Frontend :
`auth.interceptor.ts`, `login.component.*`. Effet sur toute l'application :
tout 401 porte désormais un message et vide la session locale côté
frontend. Les 403 hors Réservation gardent leur corps par défaut ; seule la
journalisation s'y ajoute.

Commits séparés : `9abb5e3 feat(securite): 401 explicite sur token
expiré/invalide et journalisation des refus` (backend) et `7a8fd9a
feat(auth-ui): affiche le motif du 401 sur l'écran de connexion` (frontend).

## Résultats de tests

Capture réelle des deux suites (`./mvnw clean test` et `npx ng test
--watch=false --browsers=ChromeHeadless`), exécutées après les ajouts « en
avance » :

![Résultats des tests backend et frontend](https://raw.githubusercontent.com/Tcheutch/bibiotheque/762e2e1268f508713b3c118289ee27c0a41fa755/screenshots/test-results-securite.png)

(Lien absolu épinglé au commit `762e2e1`, qui met à jour la capture, pas un chemin relatif : `gh pr
create --body-file` colle le texte brut dans le corps de la PR, qui ne
résout pas les chemins relatifs contre l'arborescence du dépôt — un chemin
relatif comme `screenshots/xxx.png` ne s'affiche jamais dans un corps de PR
GitHub, contrairement à un README affiché en navigant le dépôt. C'est ce qui
n'avait pas été résolu dans la PR #75 : les captures y sont mentionnées par
leur nom mais jamais réellement intégrées.)

**Backend — 26/26, `BUILD SUCCESS`** (`./mvnw clean test`, PostgreSQL réel) :
- `ReservationServiceTest` : 11 tests (dont le cas RG-03 manquant ajouté,
  2 actives → succès, sans toucher au cas 3 actives → refus déjà présent).
- `ReservationIntegrationTest` : 2 tests (RG-01/RG-04, service direct).
- `ReservationSecurityIntegrationTest` : 12 tests, **vrai flux HTTP**
  (`TestRestTemplate`, port réel, `POST /authenticate` réel, chaîne de
  sécurité non mockée) : 401 sans token (avec son message), 200 + filtrage
  silencieux avec token Adhérent, 200 sur sa propre réservation, 403 sur
  celle d'un autre, 403/204 sur `DELETE` (Adhérent/Bibliothécaire), 400
  nommant le champ `adherentId` manquant. **Ajouts « en avance »** : 401 +
  message sur token expiré et sur token malformé, et trois vérifications du
  journal `ACCES_REFUSE` (401 token expiré, 403 sur la réservation d'un
  autre, 403 sur `/admin/users`).
- `BibliothequeApplicationTests` : 1 test (contexte).

**Frontend — 18 FAILED (préexistants), 71 SUCCESS, 89 total** (`ng test`,
Chrome headless réel), aucune régression. Les 18 échecs sont les 19
préexistants moins `LoginComponent should create`, réparé (TestBed
complété) pour pouvoir y tester l'alerte de session. Tests ajoutés par cette
branche, tous passants :
- `reservation-form.component.spec.ts` (+3) : sélecteur "Adhérent" absent du
  DOM quand `afficherSelecteurAdherent=false` ; soumission possible avec
  seulement `livreId` ; corps émis sans `adherentId`.
- `reservations.component.spec.ts` (+2, nouveau bloc `describe` rôle
  Adhérent) : `GET /admin/users` jamais appelé ; `estBibliothecaire` faux et
  `adherents` reste vide.
- `auth.interceptor.spec.ts` (+1 net, le test du 401 étant remplacé par
  deux) : session vidée et redirection vers `/login` avec le message du
  serveur ; message de repli ; un 403 ne vide pas la session.
- `login.component.spec.ts` (+2) : alerte affichée avec le message transmis ;
  aucune alerte sans message.

## Scénario §6.3 — vérification navigateur réelle (preuve empirique RS-03/RS-05)

Deux comptes `User` de test distincts, chacun avec une réservation active
préexistante, pilotés via Chromium headless réel (protocole DevTools,
backend + PostgreSQL réels, ni mock ni simulation) :

1. Connecté en tant que premier compte : `/reservations` s'ouvre sans
   passer par `/forbidden` (`GET /api/reservations` → 200). Sélecteur
   "Adhérent" absent du DOM. Création d'une réservation pour lui-même :
   corps réseau intercepté = **`{"livreId":378}`** — `adherentId` absent,
   pas seulement `undefined`.
2. **Preuve la plus parlante, pas une simple liste non vide** : après
   création, sa liste affiche exactement ses 2 réservations (l'ancienne +
   la nouvelle) — **et rien de la réservation du second compte**. Basculé
   sur le second compte : sa liste affiche exactement sa propre réservation
   — **et rien des 2 réservations du premier compte**. Absence totale de
   croisement entre les données des deux comptes dans les deux sens, pas
   seulement l'un des deux.
3. Chaque ligne affiche "Adhérent #<id>" (ex. `Adhérent #380`) — effet de
   bord déjà signalé (l'annuaire des adhérents n'est plus chargé pour un
   Adhérent, RS-04) confirmé visuellement comme non régressif : pas de case
   vide, pas d'erreur affichée, juste un identifiant numérique au lieu d'un
   nom.

Comptes et données de test supprimés après vérification (`e2e_sec_user1`/
`e2e_sec_user2`, 3 livres `[E2E-SEC]`, 2 réservations) — distincts du jeu de
données de démonstration préparé séparément pour le passage devant le
formateur.

## Revue `/code-review ultra` sur l'ensemble de la branche

Lancée avant le push, sur toute la branche (vs `main`, 66 fichiers). Trois
remontées :

1. **Corrigé** — `header.component.html:28` gardait le lien de navigation
   "Réservations" sur `roleMatch(['Admin'])` seul, alors que la route
   (`app-routing.module.ts:29`) et l'écran servent désormais aussi les
   Adhérents : sans ce correctif, un `User` authentifié n'avait **aucun
   moyen de découvrir l'écran depuis l'UI** (URL à taper à la main). Passé à
   `roleMatch(['Admin','User'])`. Ce point avait échappé à la vérification
   empirique du §6.3 parce que le scénario navigue directement vers l'URL
   (`page.goto(...)`), sans jamais passer par le lien de menu — reconfirmé
   après correctif avec un compte de test jetable, cliquant réellement sur
   le lien.
2. **Corrigé** — `ReservationsComponent.estUtilisateurBibliothecaire()`
   réimplémentait la même logique que `UsersService.roleMatch(['Admin'])`,
   déjà utilisée partout ailleurs dans l'app : deux implémentations
   indépendantes de "qui est Admin" qui auraient pu diverger silencieusement
   — exactement le risque que le point précédent vient d'illustrer.
   Remplacé par un appel direct à `roleMatch(['Admin'])`.
3. **Signalé, non corrigé** — `BorrowRepository.existsByBookIdAndReturnDateIsNull`
   n'a aucun appelant dans tout le dépôt. Vérifié : cette méthode vient du
   commit `93e2f46` (séance 3, module Réservation initial), **avant** le
   point de départ de cette branche — `git diff feature/reservation-ui-...
   ...HEAD -- .../BorrowRepository.java` est vide, ce fichier n'a jamais été
   touché ici. Dette préexistante de la branche parente, hors périmètre de
   cette PR, non corrigée sans accord explicite.

Suite Karma après les deux correctifs : 19 FAILED (identiques), 67 SUCCESS,
86 total — inchangé.

## Points d'attention pour la revue (hors périmètre, non corrigés aujourd'hui)

- `role_name = 'User'` existe en double en base (`role_id` 2 et 3) —
  préexistant, découvert lors de la reconnaissance, non touché.
- `users.service.ts` construit son URL en dur
  (`http://localhost:8080/admin/users`) plutôt que via `environment
  .apiBaseUrl` — préexistant, non touché.
- Pour un Adhérent, la liste affiche `Adhérent #<id>` au lieu d'un nom
  (conséquence directe et voulue de RS-04 : l'annuaire des adhérents n'est
  plus chargé pour ce rôle) — effet de bord assumé, pas une régression.
