package com.spa.sistema_spa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SistemaSpaApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SpaServiceRepository serviceRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private MasseuseRepository masseuseRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void dashboardRequiresLogin() throws Exception {
		mockMvc.perform(get("/admin/dashboard"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/login"));
	}

	@Test
	void branchesPageRendersFromDatabase() throws Exception {
		mockMvc.perform(get("/sucursales"))
				.andExpect(status().isOk());
	}

	@Test
	void validCredentialsOpenDashboard() throws Exception {
		HttpSession session = mockMvc.perform(post("/admin/login")
				.param("username", "admin")
				.param("password", "1234"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/dashboard"))
				.andReturn().getRequest().getSession();

		mockMvc.perform(get("/admin/dashboard").session((org.springframework.mock.web.MockHttpSession) session))
				.andExpect(status().isOk());
	}

	@Test
	void adminCanCreateServiceFromPortal() throws Exception {
		HttpSession session = mockMvc.perform(post("/admin/login")
				.param("username", "admin")
				.param("password", "1234"))
				.andReturn().getRequest().getSession();

		mockMvc.perform(post("/admin/services/save")
				.session((org.springframework.mock.web.MockHttpSession) session)
				.param("name", "Servicio de prueba")
				.param("category", "Bienestar")
				.param("description", "Descripción para validar el alta desde el portal.")
				.param("durationMinutes", "30")
				.param("price", "20.00")
				.param("imageUrl", "https://example.com/service.jpg"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/dashboard?serviceSaved"));

		List<SpaService> services = serviceRepository.findAll();
		org.junit.jupiter.api.Assertions.assertTrue(services.stream()
				.anyMatch(service -> "Servicio de prueba".equals(service.getName())));
	}

	@Test
	void adminCanCreateBranchFromPortal() throws Exception {
		HttpSession session = mockMvc.perform(post("/admin/login")
				.param("username", "admin").param("password", "1234"))
				.andReturn().getRequest().getSession();

		mockMvc.perform(post("/admin/branches/save")
				.session((org.springframework.mock.web.MockHttpSession) session)
				.param("name", "Sede de prueba").param("sector", "Prueba")
				.param("address", "Dirección de prueba").param("openingHours", "Lun - Vie: 09h00 - 18h00")
				.param("latitude", "-2.20").param("longitude", "-79.90"))
				.andExpect(status().is3xxRedirection());

		org.junit.jupiter.api.Assertions.assertTrue(branchRepository.findAll().stream()
				.anyMatch(branch -> "Sede de prueba".equals(branch.getName())));
	}

	@Test
	void bookingPageShowsSlotsWithinSelectedBranchSchedule() throws Exception {
		Branch branch = branchRepository.findByActiveTrueOrderByIdAsc().get(0);
		SpaService service = serviceRepository.save(new SpaService("Servicio agenda", "Prueba",
				"Servicio para probar disponibilidad", 30, new BigDecimal("20.00"), "https://example.com/booking.jpg"));
		Masseuse masseuse = masseuseRepository.findByActiveTrueOrderByNameAsc().get(0);
		LocalDate monday = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
		mockMvc.perform(get("/agendar")
				.param("sucursalId", branch.getId().toString())
				.param("servicioId", service.getId().toString())
				.param("masajistaId", masseuse.getId().toString())
				.param("fecha", monday.toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("08:00")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("19:30")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("20:00"))))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("María González")));
	}

	@Test
	void customerCanReserveAtSixPmWithSelectedMasseuse() throws Exception {
		SpaService service = serviceRepository.save(new SpaService("Servicio seis", "Prueba", "Servicio de prueba", 60, new BigDecimal("15.00"), "https://example.com/service-six.jpg"));
		Masseuse masseuse = masseuseRepository.save(new Masseuse("Masajista seis", "Masoterapia", "0999999997", "seis@example.com"));

		mockMvc.perform(post("/reservas/confirmar")
				.param("servicioId", service.getId().toString())
				.param("sucursalId", "1")
				.param("masajistaId", masseuse.getId().toString())
				.param("fecha", LocalDate.now().plusDays(2).toString())
				.param("horaSeleccionada", "18:00")
				.param("clienteNombre", "Cliente seis")
				.param("clienteCedula", "0000000001")
				.param("clienteTelefono", "0999999996")
				.param("clienteEmail", "seis@example.com"))
				.andExpect(status().isOk());

		org.junit.jupiter.api.Assertions.assertTrue(reservationRepository.findAll().stream()
				.anyMatch(reservation -> masseuse.getId().equals(reservation.getMasseuseId())
						&& "18:00".equals(reservation.getReservationTime())));
	}

	@Test
	void customerCannotBookTreatmentThatWouldEndAfterBranchClosing() throws Exception {
		Branch branch = branchRepository.findByActiveTrueOrderByIdAsc().get(0);
		SpaService service = serviceRepository.save(new SpaService("Tratamiento largo", "Prueba",
				"Tratamiento de dos horas", 120, new BigDecimal("30.00"), "https://example.com/long.jpg"));
		Masseuse masseuse = masseuseRepository.findByActiveTrueOrderByNameAsc().get(0);
		LocalDate date = LocalDate.now().plusWeeks(2).with(java.time.DayOfWeek.MONDAY);

		mockMvc.perform(post("/reservas/confirmar")
				.param("servicioId", service.getId().toString())
				.param("sucursalId", branch.getId().toString())
				.param("masajistaId", masseuse.getId().toString())
				.param("fecha", date.toString()).param("horaSeleccionada", "19:00")
				.param("clienteNombre", "Cliente largo").param("clienteCedula", "0000000011")
				.param("clienteTelefono", "0999999911").param("clienteEmail", "largo@example.com"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("no cabe dentro del horario")));

		org.junit.jupiter.api.Assertions.assertFalse(reservationRepository.findAll().stream()
				.anyMatch(reservation -> "Cliente largo".equals(reservation.getCustomerName())));
	}

	@Test
	void customerCannotBookSlotThatPartiallyOverlapsExistingTreatment() throws Exception {
		Branch branch = branchRepository.findByActiveTrueOrderByIdAsc().get(0);
		SpaService existingService = serviceRepository.save(new SpaService("Tratamiento existente", "Prueba",
				"Tratamiento de una hora", 60, new BigDecimal("20.00"), "https://example.com/existing.jpg"));
		SpaService requestedService = serviceRepository.save(new SpaService("Tratamiento solicitado", "Prueba",
				"Tratamiento de media hora", 30, new BigDecimal("10.00"), "https://example.com/requested.jpg"));
		Masseuse masseuse = masseuseRepository.findByActiveTrueOrderByNameAsc().get(0);
		LocalDate date = LocalDate.now().plusWeeks(3).with(java.time.DayOfWeek.MONDAY);
		reservationRepository.save(new Reservation(existingService.getId(), branch.getId().intValue(), masseuse.getId(),
				"Cliente existente", "0000000012", "0999999912", "existente@example.com", date, "09:00"));

		mockMvc.perform(post("/reservas/confirmar")
				.param("servicioId", requestedService.getId().toString())
				.param("sucursalId", branch.getId().toString())
				.param("masajistaId", masseuse.getId().toString())
				.param("fecha", date.toString()).param("horaSeleccionada", "09:30")
				.param("clienteNombre", "Cliente solapado").param("clienteCedula", "0000000013")
				.param("clienteTelefono", "0999999913").param("clienteEmail", "solapado@example.com"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("no cabe dentro del horario")));

		org.junit.jupiter.api.Assertions.assertFalse(reservationRepository.findAll().stream()
				.anyMatch(reservation -> "Cliente solapado".equals(reservation.getCustomerName())));
	}

	@Test
	void customerContactDetailsAreValidatedBeforeSavingReservation() throws Exception {
		Branch branch = branchRepository.findByActiveTrueOrderByIdAsc().get(0);
		SpaService service = serviceRepository.save(new SpaService("Servicio contacto", "Prueba",
				"Servicio para validar contacto", 30, new BigDecimal("10.00"), "https://example.com/contact.jpg"));
		Masseuse masseuse = masseuseRepository.findByActiveTrueOrderByNameAsc().get(0);
		LocalDate date = LocalDate.now().plusWeeks(4).with(java.time.DayOfWeek.MONDAY);

		mockMvc.perform(post("/reservas/confirmar")
				.param("servicioId", service.getId().toString())
				.param("sucursalId", branch.getId().toString())
				.param("masajistaId", masseuse.getId().toString())
				.param("fecha", date.toString()).param("horaSeleccionada", "09:00")
				.param("clienteNombre", "Cliente inválido").param("clienteCedula", "12")
				.param("clienteTelefono", "0999999914").param("clienteEmail", "correo-invalido"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Revisa el nombre")));

		org.junit.jupiter.api.Assertions.assertFalse(reservationRepository.findAll().stream()
				.anyMatch(reservation -> "Cliente inválido".equals(reservation.getCustomerName())));
	}

	@Test
	void adminCanChangeReservationStatus() throws Exception {
		SpaService service = serviceRepository.save(new SpaService("Servicio cita", "Prueba", "Servicio de prueba", 30, new BigDecimal("10.00"), "https://example.com/test.jpg"));
		Masseuse masseuse = masseuseRepository.save(new Masseuse("Masajista de prueba", "Masoterapia", "0999999998", "masajista@example.com"));
		Reservation reservation = reservationRepository.save(new Reservation(service.getId(), 1, masseuse.getId(), "Cliente prueba", "0000000000", "0999999999", "cliente@example.com", LocalDate.now().plusDays(1), "09:00"));
		HttpSession session = mockMvc.perform(post("/admin/login")
				.param("username", "admin").param("password", "1234"))
				.andReturn().getRequest().getSession();

		mockMvc.perform(post("/admin/reservations/status")
				.session((org.springframework.mock.web.MockHttpSession) session)
				.param("id", reservation.getId().toString()).param("status", "CONFIRMADA"))
				.andExpect(status().is3xxRedirection());

		org.junit.jupiter.api.Assertions.assertEquals("CONFIRMADA", reservationRepository.findById(reservation.getId()).orElseThrow().getStatus());
	}

}
