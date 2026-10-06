package com.spa.sistema_spa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReservationExperienceTests {
    @Autowired MockMvc mvc;
    @Autowired ReservationBookingService booking;
    @Autowired ReservationRepository reservations;
    @Autowired BranchRepository branches;
    @Autowired MasseuseRepository masseuses;
    @Autowired SpaServiceRepository services;
    private Branch branch;
    private Masseuse professional;
    private SpaService treatment;
    private final LocalDate date = LocalDate.of(2099, 1, 5);
    private String email;
    private String code;
    private Reservation reservation;

    @BeforeEach
    void fixture() {
        String suffix = UUID.randomUUID().toString();
        email = suffix + "@example.com";
        branch = branches.save(new Branch("Sede prueba " + suffix, "Prueba", "Dirección",
                "Lun - Dom: 08h00 - 20h00", 0, 0));
        professional = masseuses.save(new Masseuse("Profesional prueba", "Prueba", "0999999999", email));
        treatment = services.save(new SpaService("Ritual violeta", "Bienestar", "Descripción", 60,
                new BigDecimal("45.00"), "https://example.com/fixture.jpg"));
        var result = booking.book(treatment.getId(), branch.getId().intValue(), professional.getId(), date,
                "10:00", "Cliente experiencia", "1234567890", "0999999999", email);
        assertTrue(result.successful());
        code = result.accessCode();
        reservation = booking.findReservation(email, code).orElseThrow();
    }

    private MockHttpSession admin() {
        var session = new MockHttpSession();
        session.setAttribute("adminAuthenticated", true);
        return session;
    }

    @Test
    void confirmationUsesRedirectAndRefreshDoesNotCreateAnotherReservation() throws Exception {
        var session = new MockHttpSession();
        long before = reservations.count();
        mvc.perform(post("/reservas/confirmar").with(csrf()).session(session)
                .param("servicioId", treatment.getId().toString()).param("sucursalId", branch.getId().toString())
                .param("masajistaId", professional.getId().toString()).param("fecha", date.toString())
                .param("horaSeleccionada", "11:00").param("clienteNombre", "Cliente confirmado")
                .param("clienteCedula", "1234567890").param("clienteTelefono", "0999999999").param("clienteEmail", email))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reservas/confirmada"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/reservas/confirmada").session(session)).andExpect(status().isOk())
                    .andExpect(view().name("reservation-confirmation"))
                    .andExpect(content().string(containsString("Tu reserva está registrada.")))
                    .andExpect(content().string(containsString("Ritual violeta")))
                    .andExpect(content().string(containsString("Profesional prueba")))
                    .andExpect(content().string(containsString("60 minutos")))
                    .andExpect(content().string(containsString("$45.00")))
                    .andExpect(model().attributeExists("accessCode"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
        assertEquals(before + 1, reservations.count());
        mvc.perform(get("/reservas/confirmada")).andExpect(redirectedUrl("/reservas/consultar"));
    }

    @Test
    void lookupSupportsBothRoutesAndDoesNotExposeIdNumberOrHash() throws Exception {
        for (String route : new String[]{"/reservas/consultar", "/reservation-lookup"}) {
            mvc.perform(get(route)).andExpect(status().isOk()).andExpect(content().string(containsString("Acceso privado")));
            mvc.perform(post(route).with(csrf()).param("email", email.toUpperCase()).param("accessCode", code))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("appointment"))
                    .andExpect(content().string(containsString("SMB-" + reservation.getId())))
                    .andExpect(content().string(not(containsString(reservation.getAccessCodeHash()))))
                    .andExpect(content().string(not(containsString(reservation.getCustomerIdNumber()))));
        }
    }

    @Test
    void receiptExpiresAndCannotBeReadFromAnotherSession() throws Exception {
        var receiptType = Class.forName("com.spa.sistema_spa.WebController$ReservationReceipt");
        var constructor = receiptType.getDeclaredConstructor(String.class, String.class, java.time.Instant.class);
        constructor.setAccessible(true);
        var session = new MockHttpSession();
        session.setAttribute("reservationReceipt", constructor.newInstance(email, code, java.time.Instant.now().minusSeconds(1801)));
        mvc.perform(get("/reservas/confirmada").session(session)).andExpect(redirectedUrl("/reservas/consultar"));
        assertNull(session.getAttribute("reservationReceipt"));
        mvc.perform(get("/reservas/confirmada").param("id", reservation.getId().toString()))
                .andExpect(redirectedUrl("/reservas/consultar"));
    }

    @Test
    void incorrectCredentialsShowSameFriendlyEmptyState() throws Exception {
        mvc.perform(post("/reservas/consultar").with(csrf()).param("email", "wrong@example.com").param("accessCode", code))
                .andExpect(model().attributeDoesNotExist("appointment")).andExpect(content().string(containsString("No encontramos una reserva")));
        mvc.perform(post("/reservas/consultar").with(csrf()).param("email", email).param("accessCode", "wrong"))
                .andExpect(model().attributeDoesNotExist("appointment")).andExpect(content().string(containsString("No encontramos una reserva")));
    }

    @Test
    void cancellationRequiresPreviewAndThenUpdatesDisplayedState() throws Exception {
        mvc.perform(post("/reservas/cancelar").with(csrf()).param("email", email).param("accessCode", code))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Sí, cancelar esta reserva")));
        assertEquals("PENDIENTE", reservations.findById(reservation.getId()).orElseThrow().getStatus());
        mvc.perform(post("/reservas/cancelar").with(csrf()).param("email", email).param("accessCode", code).param("confirmed", "true"))
                .andExpect(content().string(containsString("La reserva fue cancelada.")))
                .andExpect(content().string(containsString("Esta reserva está cancelada.")));
        assertEquals("CANCELADA", reservations.findById(reservation.getId()).orElseThrow().getStatus());
        assertTrue(booking.cancel(email, code).successful());
    }

    @Test
    void cancellationRejectsPastAndCompletedEvenWithForgedConfirmation() throws Exception {
        for (String state : new String[]{"PENDIENTE", "COMPLETADA"}) {
            reservation.setStatus(state);
            ReflectionTestUtils.setField(reservation, "reservationDate", LocalDate.of(2000, 1, 1));
            reservations.save(reservation);
            mvc.perform(post("/reservas/cancelar").with(csrf()).param("email", email).param("accessCode", code).param("confirmed", "true"))
                    .andExpect(model().attributeExists("error"))
                    .andExpect(content().string(not(containsString("Sí, cancelar esta reserva"))));
            assertEquals("PENDIENTE".equals(state) ? "EXPIRADA" : state,
                    reservations.findById(reservation.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void publicWritesRequireCodeEmailAndCsrfNotSequentialId() throws Exception {
        mvc.perform(post("/reservas/cancelar").param("email", email).param("accessCode", code).param("confirmed", "true"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/reservas/cancelar").with(csrf()).param("id", reservation.getId().toString()).param("confirmed", "true"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/reservas/cancelar").with(csrf()).param("email", email).param("accessCode", "SMB-" + reservation.getId()).param("confirmed", "true"))
                .andExpect(model().attributeDoesNotExist("appointment"));
        mvc.perform(post("/reservas/cancelar").with(csrf()).param("email", "other@example.com").param("accessCode", code).param("confirmed", "true"))
                .andExpect(model().attributeDoesNotExist("appointment"));
        assertEquals("PENDIENTE", reservations.findById(reservation.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminEndpointsRemainProtected() throws Exception {
        for (String route : new String[]{"/admin/dashboard", "/admin/reservations", "/admin/reservations/action?id=" + reservation.getId()})
            mvc.perform(get(route)).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(post("/admin/reservations/status").with(csrf()).param("id", reservation.getId().toString()).param("status", "CANCELADA"))
                .andExpect(redirectedUrl("/admin/login"));
        mvc.perform(post("/admin/reservations/delete").with(csrf()).param("id", reservation.getId().toString()))
                .andExpect(redirectedUrl("/admin/login"));
        mvc.perform(post("/admin/reservations/status").session(admin()).param("id", reservation.getId().toString()).param("status", "CANCELADA"))
                .andExpect(status().isForbidden());
        assertEquals("PENDIENTE", reservations.findById(reservation.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminReviewsValidTransitionAndBackendRejectsStaleTransition() throws Exception {
        mvc.perform(get("/admin/reservations/action").session(admin()).param("id", reservation.getId().toString()).param("status", "CONFIRMADA"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Confirmar cambio")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/admin/reservations/status").session(admin()).with(csrf()).param("id", reservation.getId().toString()).param("status", "CONFIRMADA"))
                .andExpect(flash().attributeExists("confirmation")).andExpect(redirectedUrl("/admin/reservations"));
        assertEquals("CONFIRMADA", reservations.findById(reservation.getId()).orElseThrow().getStatus());
        assertTrue(booking.changeStatus(reservation.getId(), "COMPLETADA").successful());
        mvc.perform(post("/admin/reservations/status").session(admin()).with(csrf()).param("id", reservation.getId().toString()).param("status", "CANCELADA"))
                .andExpect(flash().attributeExists("error"));
        assertEquals("COMPLETADA", reservations.findById(reservation.getId()).orElseThrow().getStatus());
        mvc.perform(get("/admin/reservations/action").session(admin()).param("id", reservation.getId().toString()).param("status", "CANCELADA"))
                .andExpect(model().attribute("actionValid", false));
    }

    @Test
    void filtersCombineAndSearchByReferenceAndName() throws Exception {
        mvc.perform(get("/admin/reservations").session(admin()).param("q", "SMB-" + reservation.getId())
                .param("status", "PENDIENTE").param("date", date.toString()).param("branchId", branch.getId().toString())
                .param("masseuseId", professional.getId().toString()))
                .andExpect(status().isOk()).andExpect(model().attribute("appointments", hasSize(1)))
                .andExpect(content().string(containsString("Profesional prueba")))
                .andExpect(content().string(not(containsString(code))));
        mvc.perform(get("/admin/reservations").session(admin()).param("q", "cliente experiencia").param("branchId", branch.getId().toString()))
                .andExpect(model().attribute("appointments", hasSize(1)));
        mvc.perform(get("/admin/reservations").session(admin()).param("q", "SMB-" + reservation.getId()).param("status", "CANCELADA"))
                .andExpect(model().attribute("appointments", hasSize(0))).andExpect(content().string(containsString("No hay reservas para esta selección")));
        mvc.perform(get("/admin/reservations").session(admin()).param("date", "not-a-date"))
                .andExpect(model().attributeExists("error")).andExpect(model().attribute("appointments", hasSize(0)));
    }

    @Test
    void paginationKeepsFiltersAndCapsRows() throws Exception {
        for (int i = 0; i < 23; i++) reservations.save(new Reservation(treatment.getId(), branch.getId().intValue(),
                professional.getId(), "Cliente página", "1234567890", "0999999999", email, date, "18:00"));
        mvc.perform(get("/admin/reservations").session(admin()).param("branchId", branch.getId().toString()))
                .andExpect(model().attribute("appointments", hasSize(20))).andExpect(content().string(containsString("Siguiente")))
                .andExpect(content().string(containsString("branchId=" + branch.getId())));
        mvc.perform(get("/admin/reservations").session(admin()).param("branchId", branch.getId().toString()).param("page", "1"))
                .andExpect(model().attribute("appointments", hasSize(4)));
        mvc.perform(get("/admin/reservations").session(admin()).param("branchId", branch.getId().toString()).param("page", "999"))
                .andExpect(model().attribute("appointments", hasSize(4)));
    }

    @Test
    void relatedPagesAndCatalogEditingStillRender() throws Exception {
        for (String route : new String[]{"/", "/servicios", "/sucursales", "/agendar", "/admin/login"})
            mvc.perform(get(route)).andExpect(status().isOk());
        for (String route : new String[]{"/admin/dashboard", "/admin/branches", "/admin/masseuses",
                "/admin/services/edit?id=" + treatment.getId()})
            mvc.perform(get(route).session(admin())).andExpect(status().isOk());
        mvc.perform(get("/admin/dashboard").session(admin())).andExpect(model().attributeExists("statusCounts", "todayCount", "upcoming"))
                .andExpect(content().string(containsString("Próximas citas")));
    }
}
