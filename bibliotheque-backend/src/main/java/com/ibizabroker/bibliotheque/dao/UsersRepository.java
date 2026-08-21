package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Integer> {
    Optional<Users> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT adherent FROM Users adherent WHERE adherent.userId = :id")
    Optional<Users> findByIdForUpdate(@Param("id") Integer id);
}
