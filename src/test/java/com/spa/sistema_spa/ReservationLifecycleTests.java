package com.spa.sistema_spa;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spa.reservations.expiration.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(ReservationLifecycleTests.FixedTime.class)
class ReservationLifecycleTests {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock fixedReservationClock() {
            // 10:00 in Ecuador; deliberately UTC to verify business-zone conversion.
            return Clock.fixed(Instant.parse("2026-10-05T15:00:00Z"), ZoneOffset.UTC);
        }
    }
    @Autowired ReservationLifecycle lifecycle;
    @Autowired ReservationExpirationTask task;
    @Autowired ReservationBookingService booking;
    @Autowired ReservationRepository reservations;
    @Autowired BranchRepository branches;
    @Autowired MasseuseRepository masseuses;
    @Autowired SpaServiceRepository services;
    @Autowired MockMvc mvc;
    @Autowired ConfigurableApplicationContext context;
    @Autowired PlatformTransactionManager transactions;
    private Branch branch;
    private Masseuse professional;
    private SpaService service;
    private String email;
    private static final String CODE = "lifecycle-fixture-private-code";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @BeforeEach
    void fixtures() {
        email = UUID.randomUUID() + "@example.com";
        branch = branches.save(new Branch("Sede temporal", "Prueba", "Prueba", "Lun - Dom: 08h00 - 20h00", 0, 0));
        professional = masseuses.save(new Masseuse("Profesional temporal", "Prueba", "0999999999", email));
        service = services.save(new SpaService("Tratamiento temporal", "Prueba", "Prueba", 120, new BigDecimal("45"), ""));
    }

    private Reservation appointment(String status, LocalDate date, String time) throws Exception {
        var r = new Reservation(service.getId(), branch.getId().intValue(), professional.getId(), "Cliente temporal",
                "1234567890", "0999999999", email, date, time);
        r.setStatus(status);
        r.setAccessCodeHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CODE.getBytes(StandardCharsets.UTF_8))));
        return reservations.save(r);
    }
    private String state(Reservation r) { return reservations.findById(r.getId()).orElseThrow().getStatus(); }
    private MockHttpSession admin() {
        var session = new MockHttpSession(); session.setAttribute("adminAuthenticated", true); return session;
    }

    @Test
    void futurePendingRemainsPendingAndKeepsTransitions() throws Exception {
        var r = appointment("PENDIENTE", TODAY, "11:00"); task.reconcile();
        assertEquals("PENDIENTE", state(r));
        assertEquals(List.of("PENDIENTE", "CONFIRMADA", "COMPLETADA", "CANCELADA"), ReservationPolicy.transitions(r, lifecycle.now()));
        assertTrue(booking.changeStatus(r.getId(), "CONFIRMADA").successful());
    }

    @Test
    void startupAndScheduledTaskExpirePastPendingIdempotentlyWithoutAnAdminRequest() throws Exception {
        var r = appointment("PENDIENTE", LocalDate.of(2026, 9, 30), "14:00");
        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(SistemaSpaApplication.class), new String[0], context, Duration.ZERO));
        assertEquals("EXPIRADA", state(r));
        var another = appointment("PENDIENTE", TODAY, "09:00");
        task.reconcile(); task.reconcile();
        assertEquals("EXPIRADA", state(another));
        assertEquals(0, lifecycle.reconcile());
        assertTrue(reservations.existsById(r.getId()));
    }

    @Test
    void manipulatedAdminPostCannotConfirmOrCompletePastPendingOrExpired() throws Exception {
        for (String source : List.of("PENDIENTE", "EXPIRADA")) {
            for (String target : List.of("CONFIRMADA", "COMPLETADA", "CANCELADA", "PENDIENTE")) {
                var r = appointment(source, TODAY.minusDays(1), "14:00");
                mvc.perform(post("/admin/reservations/status").session(admin()).with(csrf())
                        .param("id", r.getId().toString()).param("status", target))
                        .andExpect(flash().attributeExists("error"));
                assertEquals("EXPIRADA", state(r));
            }
        }
    }

    @Test
    void customerSeesExpiredHistoryAndCannotCancelIt() throws Exception {
        var r = appointment("PENDIENTE", TODAY.minusDays(5), "14:00");
        mvc.perform(post("/reservas/consultar").with(csrf()).param("email", email).param("accessCode", CODE))
                .andExpect(content().string(containsString("EXPIRADA")))
                .andExpect(content().string(containsString("Esta reserva expiró sin confirmación.")))
                .andExpect(content().string(containsString("Reservar otra experiencia")))
                .andExpect(content().string(not(containsString("action=\"/reservas/cancelar\""))));
        mvc.perform(post("/reservas/cancelar").with(csrf()).param("email", email).param("accessCode", CODE).param("confirmed", "true"))
                .andExpect(model().attributeExists("error"));
        assertEquals("EXPIRADA", state(r));
    }

    @Test
    void dashboardExcludesExpiredFromOperationalKpiAndUpcomingButIncludesHistory() throws Exception {
        var expired = appointment("PENDIENTE", TODAY.minusDays(5), "14:00");
        var future = appointment("PENDIENTE", TODAY.plusDays(1), "14:00");
        var model = mvc.perform(get("/admin/dashboard").session(admin())).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertEquals("EXPIRADA", state(expired));
        long expected = reservations.findAll().stream().filter(r -> "PENDIENTE".equals(r.getStatus()) && ReservationPolicy.isFuture(r, lifecycle.now())).count();
        assertEquals(expected, model.get("pendingCount"));
        var upcoming = (List<ReservationSummary>) model.get("upcoming");
        assertTrue(upcoming.stream().noneMatch(a -> a.reservation().getId().equals(expired.getId())));
        assertTrue(upcoming.stream().allMatch(a -> ReservationPolicy.isFuture(a.reservation(), lifecycle.now())));
        var counts = (Map<String, Long>) model.get("statusCounts");
        assertTrue(counts.get("EXPIRADA") >= 1);
        assertEquals("PENDIENTE", state(future));
    }

    @Test
    void expiredFilterAndActionPreviewUseTemporalPolicy() throws Exception {
        var r = appointment("PENDIENTE", TODAY.minusDays(1), "14:00");
        mvc.perform(get("/admin/reservations").session(admin()).param("status", "EXPIRADA").param("branchId", branch.getId().toString()))
                .andExpect(model().attribute("appointments", hasSize(1)))
                .andExpect(content().string(containsString("Esta reserva expiró sin confirmación.")))
                .andExpect(content().string(not(containsString("Cambiar estado"))));
        mvc.perform(get("/admin/reservations/action").session(admin()).param("id", r.getId().toString()).param("status", "CONFIRMADA"))
                .andExpect(model().attribute("actionValid", false));
    }

    @Test
    void pastConfirmedRequiresResolutionAndIsNeverAutomaticallyCompleted() throws Exception {
        var r = appointment("CONFIRMADA", TODAY.minusDays(1), "14:00"); task.reconcile();
        assertEquals("CONFIRMADA", state(r));
        var summary = ReservationSummary.from(r, service, branch, professional, lifecycle.now());
        assertTrue(summary.resolutionRequired());
        assertEquals(List.of("COMPLETADA", "CANCELADA"), summary.transitions());
        assertFalse(booking.changeStatus(r.getId(), "PENDIENTE").successful());
        assertFalse(booking.cancel(email, CODE).successful());
        mvc.perform(get("/admin/reservations").session(admin()).param("q", "SMB-" + r.getId()))
                .andExpect(content().string(containsString("Requiere resolución")));
        assertTrue(booking.changeStatus(r.getId(), "COMPLETADA").successful());
    }

    @Test
    void exactStartExpiresButUtcDoesNotPrematurelyExpireEcuadorAppointments() throws Exception {
        assertEquals(LocalDateTime.of(2026, 10, 5, 10, 0), lifecycle.now());
        var boundary = appointment("PENDIENTE", TODAY, "10:00");
        var future = appointment("PENDIENTE", TODAY, "10:01");
        var afterNoon = appointment("PENDIENTE", TODAY, "2:00 PM");
        task.reconcile();
        assertEquals("EXPIRADA", state(boundary)); assertEquals("PENDIENTE", state(future)); assertEquals("PENDIENTE", state(afterNoon));
        var utcMidnight = new ReservationLifecycle(reservations, Clock.fixed(Instant.parse("2026-10-06T02:00:00Z"), ZoneOffset.UTC));
        assertEquals(LocalDateTime.of(2026, 10, 5, 21, 0), utcMidnight.now());
    }

    @Test
    void malformedTimesAreNotGuessedAndCannotBeConfirmed() throws Exception {
        var r = appointment("PENDIENTE", TODAY.minusDays(1), "not-a-time"); task.reconcile();
        assertEquals("PENDIENTE", state(r));
        assertFalse(booking.changeStatus(r.getId(), "CONFIRMADA").successful());
    }

    @Test
    void reconciliationDoesNotOverwriteConcurrentResolution() throws Exception {
        var r = appointment("PENDIENTE", TODAY.minusDays(1), "14:00");
        r.setStatus("CONFIRMADA"); reservations.save(r);
        int updated = new TransactionTemplate(transactions).execute(tx -> reservations.expirePending(r.getId(), r.getReservationDate(), r.getReservationTime()));
        assertEquals(0, updated); assertEquals("CONFIRMADA", state(r));
    }

    @Test
    void expiredAndUnreconciledPastPendingDoNotBlockFutureSlotsButConfirmedDoes() throws Exception {
        var pending = appointment("PENDIENTE", TODAY, "09:30");
        var slots = booking.availableSlots(branch.getId().intValue(), professional.getId(), service.getId(), TODAY);
        assertTrue(slots.contains("10:30")); assertFalse(slots.contains("09:30"));
        task.reconcile(); assertEquals("EXPIRADA", state(pending));
        assertTrue(booking.availableSlots(branch.getId().intValue(), professional.getId(), service.getId(), TODAY).contains("10:30"));
        appointment("CONFIRMADA", TODAY, "09:30");
        assertFalse(booking.availableSlots(branch.getId().intValue(), professional.getId(), service.getId(), TODAY).contains("10:30"));
        assertTrue(booking.availableSlots(branch.getId().intValue(), professional.getId(), service.getId(), TODAY.plusDays(1)).contains("09:30"));
    }

    @Test
    void futureBookingStillWorksAndCannotBeManuallyExpired() {
        var result = booking.book(service.getId(), branch.getId().intValue(), professional.getId(), TODAY.plusDays(1), "11:00",
                "Cliente futuro", "1234567890", "0999999999", email);
        assertTrue(result.successful());
        var r = booking.findReservation(email, result.accessCode()).orElseThrow();
        assertEquals("PENDIENTE", r.getStatus());
        assertFalse(booking.changeStatus(r.getId(), "EXPIRADA").successful());
    }
}
