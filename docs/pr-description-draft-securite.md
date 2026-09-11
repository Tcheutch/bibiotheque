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
  token/token invalide) et `JwtAccessDeniedHandler` (403, câblé dans
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

## Résultats de tests

Capture réelle des deux suites (`./mvnw test` et `npx ng test --watch=false
--browsers=ChromeHeadless`, exécutées juste avant l'ouverture de cette PR) :

![Résultats des tests backend et frontend](https://raw.githubusercontent.com/Tcheutch/bibiotheque/b8438607065ec376a47deb1f36f66fd5188c9b1a/screenshots/test-results-securite.png)

(Lien absolu épinglé au commit `b843860`, pas un chemin relatif : `gh pr
create --body-file` colle le texte brut dans le corps de la PR, qui ne
résout pas les chemins relatifs contre l'arborescence du dépôt — un chemin
relatif comme `screenshots/xxx.png` ne s'affiche jamais dans un corps de PR
GitHub, contrairement à un README affiché en navigant le dépôt. C'est ce qui
n'avait pas été résolu dans la PR #75 : les captures y sont mentionnées par
leur nom mais jamais réellement intégrées.)

**Backend — 21/21, `BUILD SUCCESS`** (`./mvnw clean test`, PostgreSQL réel) :
- `ReservationServiceTest` : 12 tests (dont le cas RG-03 manquant ajouté,
  2 actives → succès, sans toucher au cas 3 actives → refus déjà présent).
- `ReservationIntegrationTest` : 2 tests (RG-01/RG-04, service direct).
- `ReservationSecurityIntegrationTest` : 7 tests, **vrai flux HTTP**
  (`TestRestTemplate`, port réel, `POST /authenticate` réel, chaîne de
  sécurité non mockée) : 401 sans token, 200 + filtrage silencieux avec
  token Adhérent, 200 sur sa propre réservation, 403 sur celle d'un autre,
  403/204 sur `DELETE` (Adhérent/Bibliothécaire), 400 nommant le champ
  `adherentId` manquant.
- `BibliothequeApplicationTests` : 1 test (contexte).

**Frontend — 19 FAILED (identiques, préexistants), 67 SUCCESS, 86 total**
(`ng test`, Chrome headless réel) : **+5 tests nouveaux, tous passants**,
aucune régression sur les 62 déjà verts :
- `reservation-form.component.spec.ts` (+3) : sélecteur "Adhérent" absent du
  DOM quand `afficherSelecteurAdherent=false` ; soumission possible avec
  seulement `livreId` ; corps émis sans `adherentId`.
- `reservations.component.spec.ts` (+2, nouveau bloc `describe` rôle
  Adhérent) : `GET /admin/users` jamais appelé ; `estBibliothecaire` faux et
  `adherents` reste vide.

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
