package com.spa.sistema_spa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
	List<Reservation> findAllByOrderByReservationDateAscReservationTimeAsc();
	List<Reservation> findByMasseuseIdAndReservationDateAndStatusNot(Long masseuseId, java.time.LocalDate date, String status);
}