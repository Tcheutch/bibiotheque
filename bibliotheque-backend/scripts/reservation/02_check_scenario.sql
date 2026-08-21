\set ON_ERROR_STOP on

\echo '===== ETAT DES LIVRES L1-L5 ====='
SELECT
    b.book_id,
    b.book_name,
    b.no_of_copies,
    COUNT(br.borrow_id) FILTER (WHERE br.return_date IS NULL) AS emprunts_actifs,
    CASE
        WHEN COUNT(br.borrow_id) FILTER (WHERE br.return_date IS NULL) = 0
            THEN 'DISPONIBLE'
        ELSE 'INDISPONIBLE'
    END AS etat_metier
FROM books b
LEFT JOIN borrow br ON br.book_id = b.book_id
WHERE b.book_name LIKE '[TEST-RES]%'
GROUP BY b.book_id, b.book_name, b.no_of_copies
ORDER BY b.book_name;

\echo '===== ETAT DES ADHERENTS A1-A3 ====='
SELECT user_id, username, name
FROM users
WHERE username IN ('reservation_a1', 'reservation_a2', 'reservation_a3')
ORDER BY username;
