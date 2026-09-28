package com.spa.sistema_spa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SistemaSpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemaSpaApplication.class, args);
    }

    @Bean
    @Order(0)
    CommandLineRunner migrateReservationMasseuseColumn(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("ALTER TABLE reservations ADD COLUMN IF NOT EXISTS masseuse_id BIGINT");
        };
    }

    @Bean
    @Order(3)
    CommandLineRunner backfillReservationMasseuse(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.update("""
                    UPDATE reservations
                    SET masseuse_id = (SELECT MIN(id) FROM masseuses)
                    WHERE masseuse_id IS NULL
                    """);
        };
    }

    @Bean
    @Order(1)
    CommandLineRunner seedBranches(BranchRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new Branch("Sede Norte 1 (Kennedy)", "Sector Norte", "Av. San Jorge y Fco. Boloña", "Lun - Sáb: 08h00 - 20h00", -2.1709, -79.9005));
                repository.save(new Branch("Sede Norte 2 (Samborondón)", "Alta Demanda", "Km 2.5 Vía Samborondón", "Mar - Dom: 09h00 - 21h00", -2.1450, -79.8650));
                repository.save(new Branch("Sede Centro (Malecón)", "Sector Centro", "Av. 9 de Octubre y Pedro Carbo", "Lun - Vie: 08h30 - 19h30", -2.1945, -79.8820));
                repository.save(new Branch("Sede Sur (Centenario)", "Promociones", "Rosa Borja de Ycaza", "Lun - Sáb: 09h00 - 19h00", -2.2150, -79.8980));
            }
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner seedMasseuses(MasseuseRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new Masseuse("María González", "Masoterapia y piedras volcánicas", "0990000001", "maria@auraspa.com"));
                repository.save(new Masseuse("Sofía Ramírez", "Masaje relajante y descontracturante", "0990000002", "sofia@auraspa.com"));
                repository.save(new Masseuse("Valentina Torres", "Tratamientos faciales e hidroterapia", "0990000003", "valentina@auraspa.com"));
            }
        };
    }

}
