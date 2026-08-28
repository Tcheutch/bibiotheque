# Brouillon de description de Pull Request

Brouillon tenu à jour au fil des phases (voir §14 du sujet). La version
finale sera rédigée une fois les phases 3 à 8 validées.

## Résumé

_(à compléter en fin de branche : une à deux phrases sur l'écran livré)_

## Captures à joindre

- [ ] État de chargement — emplacement : _(à définir)_
- [ ] Liste remplie — emplacement : _(à définir)_
- [ ] Liste vide — emplacement : _(à définir)_
- [ ] Refus 409 (RG-01/02/03) — emplacement : _(à définir)_

## Trajet de la donnée (clic → base → retour)

_(à rédiger en fin de branche, une fois toutes les phases posées)_

## Points d'attention pour la revue

### Fichier partagé modifié hors du périmètre strict Réservation

`bibliotheque-frontend/src/app/_auth/auth.interceptor.ts` (commit `f4ab39e`) :
le `catchError` remplaçait **toute** erreur HTTP (pas seulement 401/403) par
la chaîne générique `"Some thing is wrong"`, ce qui aurait rendu impossible
l'affichage du message métier serveur exigé en phase 7. Correctif à
périmètre strict : seule la valeur relancée change (`HttpErrorResponse`
d'origine au lieu de la chaîne générique) ; les redirections 401→`/login`
et 403→`/forbidden` sont inchangées au caractère près. Couvert par
4 tests Jasmine/Karma dédiés (`auth.interceptor.spec.ts`). Signalé en
évidence ici car ce fichier est partagé par tout le module JWT existant,
en dehors du strict périmètre Réservation.

### Dette de test préexistante (hors périmètre de cette branche)

La suite Jasmine/Karma comptait déjà, avant ce travail, 19 tests en échec
(`should create` sur `BooksListComponent`, `CreateBookComponent`,
`UsersListComponent`, `LoginComponent`, etc., et `should be created` sur
`BooksService`, `UsersService`, `BorrowService`, `AuthGuard`). Cause dans
tous les cas : `NullInjectorError` (`No provider for HttpClient!` ou
`No provider for ActivatedRoute!`) — ces specs ne fournissent ni
`HttpClientTestingModule` ni `RouterTestingModule` dans leur
`TestBed.configureTestingModule`, alors que les composants/services testés
en dépendent.

Vérifié explicitement (grep sur tous les `*.spec.ts`) : aucun de ces
19 fichiers ne référence `HTTP_INTERCEPTORS` ni `AuthInterceptor` — seul
`auth.interceptor.spec.ts` (ajouté par cette branche) le fait. Le
correctif de l'intercepteur n'est donc pas en cause dans ces échecs.
Dette de test antérieure à cette branche, hors de son périmètre ; non
corrigée ici.
