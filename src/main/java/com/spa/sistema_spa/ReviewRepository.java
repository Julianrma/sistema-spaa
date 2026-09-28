package com.spa.sistema_spa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByStatusOrderByIdDesc(String status);
}