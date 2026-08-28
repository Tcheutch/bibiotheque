# Note d'explication, fichier par fichier

Support de révision avant le passage devant le formateur — une phrase par
fichier créé ou modifié pour l'écran Réservations. À lire dans l'ordre :
modèle → service → conteneur → liste → formulaire → routage → correctif
transverse.

## Modèle

- **`_model/reservation.ts`** — définit `ReservationStatus` (les 5 valeurs
  possibles), `ReservationRequest` (ce qui part vers le serveur :
  `livreId`/`adherentId` seulement), `Reservation` (ce qui en revient) et
  `ReservationAffichage` (la même chose, augmentée des libellés livre/
  adhérent résolus côté frontend).

## Service

- **`_service/reservation.service.ts`** — le seul endroit du code qui parle
  HTTP pour ce module : 5 méthodes (lister/créer/annuler les réservations,
  lister livres et adhérents en réutilisant `BooksService`/`UsersService`
  existants).
- **`_service/reservation.service.spec.ts`** — vérifie que chaque méthode
  construit la bonne URL/le bon verbe/le bon paramètre (notamment :
  l'absence de `statut` envoie « tous », jamais `statut=tous`).

## Conteneur (état de l'écran)

- **`reservations/reservations.component.ts`** — détient tout l'état
  (réservations, livres, adhérents, filtre, chargement/erreur, erreurs de
  création/annulation) et appelle le service ; c'est lui qui décide quand
  recharger la liste et quand réinitialiser le formulaire.
- **`reservations/reservations.component.html`** — assemble le formulaire
  et la liste, et bascule entre les trois blocs mutuellement exclusifs
  (spinner / erreur+Réessayer / liste).
- **`reservations/reservations.component.css`** — vide (aucun style
  propre au conteneur, tout passe par les classes Bootstrap existantes).
- **`reservations/reservations.component.spec.ts`** — le plus gros fichier
  de test : filtre → nouvelle requête, quatre états, refus 400/404/409 à la
  création, refus 409 à l'annulation, jamais de `window.alert`.

## Liste (présentation pure)

- **`reservations-list/reservations-list.component.ts`** — reçoit les
  réservations déjà résolues en `@Input`, décide si le bouton Annuler doit
  apparaître (`EN_ATTENTE`/`DISPONIBLE` seulement), et demande confirmation
  avant d'émettre l'événement d'annulation.
- **`reservations-list/reservations-list.component.html`** — le tableau à
  6 colonnes, le sélecteur de filtre, la ligne « Aucune réservation » qui
  garde les en-têtes, et le bloc d'erreur d'annulation.
- **`reservations-list/reservations-list.component.css`** — vide.
- **`reservations-list/reservations-list.component.spec.ts`** — vérifie
  l'affichage des libellés et dates formatées, la visibilité conditionnelle
  du bouton Annuler, la confirmation, et l'état vide avec en-têtes conservés.

## Formulaire (présentation pure)

- **`reservation-form/reservation-form.component.ts`** — les deux listes
  déroulantes, le bouton désactivé tant que les deux champs ne sont pas
  remplis, et l'affichage du succès/échec de la dernière tentative
  (jamais l'inverse : c'est le conteneur qui décide si c'est un succès).
- **`reservation-form/reservation-form.component.html`** — le formulaire
  lui-même, plus les deux blocs conditionnels (succès / erreur avec détail
  par champ pour un 400).
- **`reservation-form/reservation-form.component.css`** — vide.
- **`reservation-form/reservation-form.component.spec.ts`** — vérifie que
  la sélection n'est effacée qu'après un 201 confirmé (jamais au clic
  lui-même) et le rendu des trois types de refus (409/400/404).

## Routage et navigation

- **`app-routing.module.ts`** *(modifié)* — ajoute la route `reservations`,
  gardée par `AuthGuard` avec `roles:['Admin']` (comme `books`/`users`,
  puisque `GET /admin/users` — nécessaire au formulaire — est déjà
  réservé à Admin côté backend).
- **`app.module.ts`** *(modifié)* — déclare les trois nouveaux composants
  et fournit `ReservationService`.
- **`header/header.component.html`** *(modifié)* — ajoute le lien
  « Réservations » dans la navigation, visible uniquement pour Admin.

## Correctif transverse (hors périmètre strict Réservation)

- **`_auth/auth.interceptor.ts`** *(modifié)* — une seule ligne changée :
  relance l'erreur HTTP d'origine au lieu d'une chaîne générique, sinon
  aucun écran de l'application ne pourrait jamais afficher un message
  d'erreur venant du serveur.
- **`_auth/auth.interceptor.spec.ts`** — prouve que ce changement ne
  touche ni la redirection 401→`/login` ni la redirection 403→`/forbidden`.

## Documentation

- **`docs/reservation-ui-demo.md`** — déroulé chronométré de la
  démonstration de 8 minutes, avec les commandes de préparation du jeu de
  données.
- **`docs/pr-description-draft.md`** — brouillon de description de PR :
  résumé, emplacement des captures, trajet de la donnée du clic à la base.
- **`docs/note-explication.md`** — ce fichier.
