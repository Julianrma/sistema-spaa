package com.spa.sistema_spa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.accessCodeHash = :hash and lower(r.customerEmail) = lower(:email)")
    Optional<Reservation> findForUpdateByAccessCode(@Param("hash") String hash, @Param("email") String email);
	List<Reservation> findAllByOrderByIdDesc();
	List<Reservation> findByMasseuseIdAndReservationDateAndStatusNot(Long masseuseId, java.time.LocalDate date, String status);
	Optional<Reservation> findByAccessCodeHashAndCustomerEmailIgnoreCase(String accessCodeHash, String customerEmail);
}
