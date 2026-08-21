# Phase 2 + Phase 3 — Réservation

## 1. Protéger la branche de remise

Ne modifiez pas la branche déjà poussée pour l'épreuve.

```bash
cd "/home/tcheutch/PK_fokam final_Exam/bibiotheque"
git status
git switch -c improve/reservation-professional
```

## 2. Copier le contenu de ce kit à la racine du dépôt

Le dossier `bibliotheque-backend/` de ce kit doit fusionner avec le dossier existant.

## 3. Vérifier les changements

```bash
git status --short
git diff --check
git diff -- bibliotheque-backend/src/main/java/com/ibizabroker/bibliotheque/dao/BorrowRepository.java
git diff -- bibliotheque-backend/src/main/java/com/ibizabroker/bibliotheque/service/ReservationService.java
```

## 4. Tester avec Java 17

```bash
cd bibliotheque-backend
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
bash mvnw clean test
```

## 5. Préparer automatiquement L1-L5 / A1-A3

```bash
PGPASSWORD=bibliotheque psql \
  -h 127.0.0.1 -p 5432 \
  -U bibliotheque -d bibliotheque \
  -f scripts/reservation/01_prepare_scenario.sql
```

## 6. Vérifier le dataset

```bash
PGPASSWORD=bibliotheque psql \
  -h 127.0.0.1 -p 5432 \
  -U bibliotheque -d bibliotheque \
  -f scripts/reservation/02_check_scenario.sql
```

## 7. Test d'acceptation HTTP

Démarrer le backend dans un terminal puis :

```bash
bash scripts/reservation/run_reservation_acceptance.sh
```

Attendu : RG-01 à RG-04 = PASS.

## 8. Nettoyage facultatif

```bash
PGPASSWORD=bibliotheque psql \
  -h 127.0.0.1 -p 5432 \
  -U bibliotheque -d bibliotheque \
  -f scripts/reservation/03_cleanup_scenario.sql
```
