package com.spa.sistema_spa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaServiceRepository extends JpaRepository<SpaService, Long> {
    List<SpaService> findByActiveTrueOrderByIdAsc();
    List<SpaService> findAllByOrderByIdAsc();
}