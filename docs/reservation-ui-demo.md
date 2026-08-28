# Démonstration — Écran Réservations (8 minutes)

Déroulé testé de bout en bout (commandes rejouées, DB nettoyée après coup) au
moment de la rédaction. Le jeu de données réutilise le script existant de la
séance 2 (`scripts/reservation/01_prepare_scenario.sql`), complété par deux
réservations de départ pour que l'écran ne s'ouvre pas sur une liste vide.

## Avant la démo — préparer l'environnement

### 1. Démarrer les deux serveurs

```bash
# Terminal 1 — backend
cd bibliotheque-backend
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw spring-boot:run

# Terminal 2 — frontend
cd bibliotheque-frontend
npm start
```

### 2. Compte de connexion

`admin / admin123`. Si la base a été réinitialisée depuis, le compte n'existe
plus : recréer avec la procédure du `README.md` (§5, insertion SQL manuelle
d'un admin BCrypt). **Piège sur PostgreSQL** : ce README date d'une version
MySQL et propose `UPDATE hibernate_sequence SET next_val = 100`, qui échoue
ici (`hibernate_sequence` est une séquence PostgreSQL, pas une table).
Utiliser à la place :

```sql
SELECT setval('hibernate_sequence', 100, false);
```

### 3. Jeu de données

```bash
cd bibliotheque-backend
PGPASSWORD=bibliotheque psql -h 127.0.0.1 -p 5432 -U bibliotheque -d bibliotheque \
  -f scripts/reservation/01_prepare_scenario.sql
```

Crée L1 (disponible), L2 à L5 (empruntés, non rendus par A3), et les
adhérents A1 (réservataire principal), A2 (quota, non utilisé dans ce
déroulé), A3 (l'emprunteur). Le script affiche les identifiants réels créés
— notez-les, ou laissez le bloc ci-dessous les relire automatiquement.

### 4. Deux réservations de départ (pour ouvrir sur l'état DONNEES, pas VIDE)

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/authenticate \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["jwtToken"])')

L3=$(PGPASSWORD=bibliotheque psql -h 127.0.0.1 -p 5432 -U bibliotheque -d bibliotheque \
  -Atc "SELECT book_id FROM books WHERE book_name='[TEST-RES] L3 Emprunte';")
L4=$(PGPASSWORD=bibliotheque psql -h 127.0.0.1 -p 5432 -U bibliotheque -d bibliotheque \
  -Atc "SELECT book_id FROM books WHERE book_name='[TEST-RES] L4 Emprunte';")
A1=$(PGPASSWORD=bibliotheque psql -h 127.0.0.1 -p 5432 -U bibliotheque -d bibliotheque \
  -Atc "SELECT user_id FROM users WHERE username='reservation_a1';")

curl -sS -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"livreId\":$L3,\"adherentId\":$A1}" http://localhost:8080/api/reservations
curl -sS -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"livreId\":$L4,\"adherentId\":$A1}" http://localhost:8080/api/reservations
```

A1 a maintenant 2 réservations actives (L3, L4) sur les 3 autorisées — c'est
volontaire, voir l'étape RG-03 plus bas.

### 5. Connexion dans le navigateur

`http://localhost:4200/login` → `admin` / `admin123` → menu **Réservations**.

---

## Déroulé chronométré (8 minutes)

Toute la démo se joue avec un seul adhérent (**A1**) et les livres L1/L2/L5 —
plus besoin de jongler entre plusieurs adhérents.

| Temps | Étape | Action | Ce qu'on montre |
|---|---|---|---|
| 0:00–0:30 | **Chargement** | Ouvrir/recharger `/reservations` | Indicateur de chargement bref, jamais d'écran figé |
| 0:30–1:00 | **Données** | — | Les 2 lignes déjà présentes (L3, L4, A1) ; colonnes = libellés, pas d'ID ; dates formatées |
| 1:00–1:45 | **Filtre** | Changer « Filtrer par statut » (ex. EN_ATTENTE) | Onglet Réseau : une nouvelle requête `GET /api/reservations?statut=...` part à chaque changement ; revenir à « Tous » → aucun paramètre envoyé |
| 1:45–2:45 | **Création valide** | Livre = L2, Adhérent = A1 → Réserver | Bouton désactivé tant que les deux champs ne sont pas remplis ; après succès : bandeau vert, formulaire vidé, nouvelle ligne visible sans recharger la page |
| 2:45–3:45 | **Refus RG-01** | Livre = **L1** (disponible), Adhérent = A1 → Réserver | Message serveur exact affiché à côté du formulaire : *« RG-01 : le livre doit être indisponible pour être réservé. »* |
| 3:45–4:45 | **Refus RG-02** | Livre = **L2** (déjà réservé à l'étape précédente), Adhérent = A1 → Réserver | *« RG-02 : l'adhérent possède déjà une réservation active pour ce livre. »* |
| 4:45–5:45 | **Refus RG-03** | Livre = **L5**, Adhérent = A1 → Réserver | A1 est déjà à 3 réservations actives (L3, L4, L2) : *« RG-03 : l'adhérent ne peut pas dépasser 3 réservations actives simultanées. »* |
| 5:45–6:45 | **Annulation** | Cliquer *Annuler* sur la ligne L4 | Boîte de confirmation native ; après acceptation, statut → ANNULEE, bouton disparu, pas de rechargement de page |
| 6:45–7:45 | **Backend coupé** | `Ctrl+C` dans le terminal du backend, puis recharger `/reservations` | Spinner disparaît, message « Le serveur est injoignable… » + bouton **Réessayer** ; relancer le backend et cliquer Réessayer pour clore proprement |

Marge de ~15 s en fin de créneau pour les questions du formateur.

---

## Provoquer une erreur hors de l'ordre (référence rapide)

Si le formateur demande de rejouer un cas précis sans suivre l'ordre ci-dessus :

| Erreur | Code | Sélection dans le formulaire |
|---|---|---|
| RG-01 (livre disponible) | 409 | Choisir **L1** comme livre |
| RG-02 (déjà réservé) | 409 | Choisir un couple livre/adhérent déjà actif dans le tableau |
| RG-03 (quota dépassé) | 409 | Amener un adhérent à 3 réservations actives, puis en tenter une 4ᵉ |
| Livre/adhérent introuvable | 404 | Non atteignable via l'UI (les listes déroulantes ne proposent que des entrées existantes) — visible uniquement en appelant l'API directement avec un id absent |
| Champ manquant | 400 | Non atteignable via l'UI (bouton désactivé tant que les deux champs ne sont pas remplis) — visible uniquement en appelant l'API directement |
| Refus d'annulation (RG-05/RG-06) | 409 | Annuler deux fois la même réservation (le deuxième clic échoue), ou l'annuler côté API pendant que l'écran affiche encore l'ancien état |
| Backend injoignable | — | Arrêter le backend puis recharger la page, ou cliquer Réessayer pendant qu'il est arrêté |

---

## Nettoyage après la démo (optionnel)

```bash
cd bibliotheque-backend
PGPASSWORD=bibliotheque psql -h 127.0.0.1 -p 5432 -U bibliotheque -d bibliotheque \
  -f scripts/reservation/03_cleanup_scenario.sql
```

Supprime uniquement les données `[TEST-RES]` et les comptes `reservation_a1/2/3` ;
laisse le compte `admin` intact.
