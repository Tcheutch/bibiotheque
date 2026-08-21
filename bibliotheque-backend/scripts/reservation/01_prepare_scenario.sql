\set ON_ERROR_STOP on
BEGIN;

DELETE FROM reservation
WHERE livre_id IN (SELECT book_id FROM books WHERE book_name LIKE '[TEST-RES]%')
   OR adherent_id IN (
       SELECT user_id FROM users
       WHERE username IN ('reservation_a1', 'reservation_a2', 'reservation_a3')
   );

DELETE FROM borrow
WHERE book_id IN (SELECT book_id FROM books WHERE book_name LIKE '[TEST-RES]%')
   OR user_id IN (
       SELECT user_id FROM users
       WHERE username IN ('reservation_a1', 'reservation_a2', 'reservation_a3')
   );

DELETE FROM user_role
WHERE user_id IN (
    SELECT user_id FROM users
    WHERE username IN ('reservation_a1', 'reservation_a2', 'reservation_a3')
);

DELETE FROM users
WHERE username IN ('reservation_a1', 'reservation_a2', 'reservation_a3');

DELETE FROM books
WHERE book_name LIKE '[TEST-RES]%';

DO $$
BEGIN
    IF to_regclass('hibernate_sequence') IS NULL THEN
        RAISE EXCEPTION 'hibernate_sequence introuvable : vérifier le schéma Hibernate.';
    END IF;
END $$;

INSERT INTO books (book_id, book_name, book_author, book_genre, no_of_copies)
VALUES
    (nextval('hibernate_sequence'), '[TEST-RES] L1 Disponible', 'Fixture Réservation', 'TEST', 1),
    (nextval('hibernate_sequence'), '[TEST-RES] L2 Emprunte',   'Fixture Réservation', 'TEST', 0),
    (nextval('hibernate_sequence'), '[TEST-RES] L3 Emprunte',   'Fixture Réservation', 'TEST', 0),
    (nextval('hibernate_sequence'), '[TEST-RES] L4 Emprunte',   'Fixture Réservation', 'TEST', 0),
    (nextval('hibernate_sequence'), '[TEST-RES] L5 Emprunte',   'Fixture Réservation', 'TEST', 0);

INSERT INTO users (user_id, username, name, password)
VALUES
    (nextval('hibernate_sequence'), 'reservation_a1', 'A1 - Réservataire principal', 'TEST_ONLY_NO_LOGIN'),
    (nextval('hibernate_sequence'), 'reservation_a2', 'A2 - Test quota',              'TEST_ONLY_NO_LOGIN'),
    (nextval('hibernate_sequence'), 'reservation_a3', 'A3 - Emprunteur',              'TEST_ONLY_NO_LOGIN');

INSERT INTO borrow (book_id, user_id, issue_date, due_date, return_date)
SELECT
    b.book_id,
    u.user_id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '14 days',
    NULL
FROM books b
CROSS JOIN users u
WHERE u.username = 'reservation_a3'
  AND b.book_name IN (
      '[TEST-RES] L2 Emprunte',
      '[TEST-RES] L3 Emprunte',
      '[TEST-RES] L4 Emprunte',
      '[TEST-RES] L5 Emprunte'
  );

COMMIT;

\echo '===== FIXTURES CREEES ====='
SELECT book_id, book_name, no_of_copies
FROM books
WHERE book_name LIKE '[TEST-RES]%'
ORDER BY book_name;

SELECT user_id, username, name
FROM users
WHERE username IN ('reservation_a1', 'reservation_a2', 'reservation_a3')
ORDER BY username;

SELECT br.borrow_id, b.book_name, u.username AS emprunteur, br.return_date
FROM borrow br
JOIN books b ON b.book_id = br.book_id
JOIN users u ON u.user_id = br.user_id
WHERE b.book_name LIKE '[TEST-RES]%'
ORDER BY b.book_name;
