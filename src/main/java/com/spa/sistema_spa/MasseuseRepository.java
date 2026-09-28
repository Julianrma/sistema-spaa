package com.spa.sistema_spa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasseuseRepository extends JpaRepository<Masseuse, Long> {
    List<Masseuse> findByActiveTrueOrderByNameAsc();
    List<Masseuse> findAllByOrderByIdAsc();
}
