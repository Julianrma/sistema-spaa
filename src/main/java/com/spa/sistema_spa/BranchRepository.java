package com.spa.sistema_spa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByActiveTrueOrderByIdAsc();
    List<Branch> findAllByOrderByIdAsc();
}