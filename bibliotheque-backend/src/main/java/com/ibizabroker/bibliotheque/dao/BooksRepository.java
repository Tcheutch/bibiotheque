package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Books;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface BooksRepository extends JpaRepository<Books, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT book FROM Books book WHERE book.bookId = :id")
    Optional<Books> findByIdForUpdate(@Param("id") Integer id);
}
