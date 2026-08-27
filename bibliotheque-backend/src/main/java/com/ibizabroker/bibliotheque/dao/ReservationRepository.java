package com.ibizabroker.bibliotheque.dao;

import com.ibizabroker.bibliotheque.entity.Reservation;
import com.ibizabroker.bibliotheque.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    boolean existsByLivreBookIdAndAdherentUserIdAndStatutIn(
            Integer livreId,
            Integer adherentId,
            Collection<ReservationStatus> statuts
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM Reservation reservation "
            + "WHERE reservation.adherent.userId = :adherentId AND reservation.statut IN :statuts")
    List<Reservation> findActiveByAdherentForUpdate(
            @Param("adherentId") Integer adherentId,
            @Param("statuts") Collection<ReservationStatus> statuts
    );

    List<Reservation> findAllByOrderByDateReservationAsc();

    List<Reservation> findByStatutOrderByDateReservationAsc(ReservationStatus statut);

    List<Reservation> findByAdherentUserIdOrderByDateReservationAsc(Integer adherentId);

    List<Reservation> findByStatutAndAdherentUserIdOrderByDateReservationAsc(
            ReservationStatus statut,
            Integer adherentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM Reservation reservation WHERE reservation.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Integer id);
}
