package com.spa.sistema_spa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MasseuseRepository extends JpaRepository<Masseuse, Long> {
    List<Masseuse> findByActiveTrueOrderByNameAsc();
    List<Masseuse> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select masseuse from Masseuse masseuse where masseuse.id = :id")
    Optional<Masseuse> findByIdForUpdate(@Param("id") Long id);
}
