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

COMMIT;
\echo 'Fixtures [TEST-RES] supprimees.'
