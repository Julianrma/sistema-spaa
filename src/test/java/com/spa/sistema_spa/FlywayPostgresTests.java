package com.spa.sistema_spa;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: use ONLY a disposable PostgreSQL database. Never cleans or drops schemas. */
@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = "jdbc:postgresql:.*")
class FlywayPostgresTests {
    @TempDir
    Path futureMigrations;

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("FLYWAY_TEST_URL"),
                System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD"));
    }

    private String newSchema() throws SQLException {
        String schema = "flyway_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        return schema;
    }

    private Flyway flyway(String schema, boolean baseline, String... extraLocations) {
        String[] locations = new String[extraLocations.length + 1];
        locations[0] = "classpath:db/migration";
        System.arraycopy(extraLocations, 0, locations, 1, extraLocations.length);
        return Flyway.configure().dataSource(System.getenv("FLYWAY_TEST_URL"),
                        System.getenv("FLYWAY_TEST_USER"), System.getenv("FLYWAY_TEST_PASSWORD"))
                .defaultSchema(schema).schemas(schema).createSchemas(false)
                .locations(locations).baselineVersion("1").baselineOnMigrate(baseline)
                .cleanDisabled(true).validateMigrationNaming(true).load();
    }

    private void execute(String schema, String sql) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.execute(sql);
        }
    }

    private String scalar(String schema, String sql) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            connection.setSchema(schema);
            try (var result = statement.executeQuery(sql)) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private String existingSchema() throws Exception {
        return existingSchema(UnaryOperator.identity());
    }

    private String existingSchema(UnaryOperator<String> transform) throws Exception {
        String schema = newSchema();
        try (var stream = getClass().getResourceAsStream("/db/migration/V1__initial_schema.sql")) {
            assertNotNull(stream);
            execute(schema, transform.apply(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
        execute(schema, """
                INSERT INTO branches (name, active) VALUES ('Existing branch', true);
                INSERT INTO masseuses (name, active) VALUES ('Existing masseuse', true);
                INSERT INTO spa_services (name, description, active)
                    VALUES ('Existing service', repeat('x', 500), true);
                INSERT INTO reservations (service_id, branch_id, masseuse_id, customer_name,
                    customer_id_number, customer_phone, customer_email, reservation_date,
                    reservation_time, status)
                    VALUES (1, 1, 1, 'Fixture', 'fixture', 'fixture', 'fixture@example.com',
                        DATE '2099-01-01', '18:00', 'PENDIENTE');
                INSERT INTO reviews (customer_name, branch_id, rating) VALUES ('Fixture', 1, 5);
                INSERT INTO reservations (service_id, branch_id, customer_name,
                    customer_id_number, customer_phone, customer_email, reservation_date,
                    reservation_time, status)
                    VALUES (1, 1, 'Unassigned fixture', 'fixture', 'fixture', 'fixture@example.com',
                        DATE '2099-01-02', '18:00', 'PENDIENTE');
                """);
        return schema;
    }

    private String snapshot(String schema) throws SQLException {
        StringBuilder result = new StringBuilder();
        for (String table : new String[]{"branches", "masseuses", "spa_services", "reservations", "reviews"}) {
            result.append(scalar(schema, "SELECT json_agg(t ORDER BY id)::text FROM " + table + " t"));
            result.append(scalar(schema, "SELECT last_value FROM " + table + "_id_seq"));
        }
        return result.toString();
    }

    @Test
    void emptyDatabaseMigratesOnceAndValidates() throws Exception {
        Flyway flyway = flyway(newSchema(), false);
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals("1", flyway.info().current().getVersion().toString());
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void baselinePreservesRowsIdsRelationsAndSequencesAndAcceptsFutureMigration() throws Exception {
        String schema = existingSchema();
        String before = snapshot(schema);
        Flyway flyway = flyway(schema, true);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("BASELINE", scalar(schema,
                "SELECT type FROM flyway_schema_history WHERE version = '1'"));
        assertEquals(before, snapshot(schema));
        Files.writeString(futureMigrations.resolve("V2__test_future_change.sql"),
                "CREATE TABLE future_fixture (id BIGINT PRIMARY KEY);");
        Flyway next = flyway(schema, false, "filesystem:" + futureMigrations);
        assertEquals(1, next.migrate().migrationsExecuted);
        assertEquals(0, next.migrate().migrationsExecuted);
        next.validate();
        assertEquals(before, snapshot(schema));
    }

    @Test
    void existingDatabaseRequiresExplicitAdoption() throws Exception {
        String schema = existingSchema();
        String before = snapshot(schema);
        assertThrows(FlywayException.class, () -> flyway(schema, false).migrate());
        assertEquals(before, snapshot(schema));
    }

    @Test
    void baselineRejectsUnrelatedOrIncompleteSchema() throws Exception {
        String schema = newSchema();
        execute(schema, "CREATE TABLE unrelated (id BIGINT PRIMARY KEY)");
        assertThrows(FlywayException.class, () -> flyway(schema, true).migrate());
        assertEquals("0", scalar(schema, "SELECT count(*) FROM unrelated"));
    }

    @Test
    void baselineRejectsMissingHistoricalServiceChanges() throws Exception {
        String oldDescription = existingSchema(sql -> sql.replace("description TEXT", "description VARCHAR(1000)"));
        String missingFeatured = existingSchema(sql -> sql.replace(",\n    featured BOOLEAN NOT NULL DEFAULT false", ""));
        for (String schema : new String[]{oldDescription, missingFeatured}) {
            String before = snapshot(schema);
            assertThrows(FlywayException.class, () -> flyway(schema, true).migrate());
            assertEquals(before, snapshot(schema));
            assertNull(flyway(schema, false).info().current());
        }
    }

    @Test
    void cleanIsDisabled() throws Exception {
        String schema = existingSchema();
        String before = snapshot(schema);
        assertThrows(FlywayException.class, () -> flyway(schema, false).clean());
        assertEquals(before, snapshot(schema));
    }
}
