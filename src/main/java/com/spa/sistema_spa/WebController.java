package com.spa.sistema_spa;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class WebController {

    private final SpaServiceRepository serviceRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final BranchRepository branchRepository;
    private final MasseuseRepository masseuseRepository;
    private final ReservationBookingService reservationBookingService;
    private final ReservationLifecycle lifecycle;
    private final AdminCredentials adminCredentials;
    private final LoginAttemptService loginAttemptService;

    public WebController(SpaServiceRepository serviceRepository, ReservationRepository reservationRepository,
                         ReviewRepository reviewRepository,
                         BranchRepository branchRepository, MasseuseRepository masseuseRepository,
                         ReservationBookingService reservationBookingService,
                         AdminCredentials adminCredentials, LoginAttemptService loginAttemptService, ReservationLifecycle lifecycle) {
        this.serviceRepository = serviceRepository;
        this.reservationRepository = reservationRepository;
        this.reviewRepository = reviewRepository;
        this.branchRepository = branchRepository;
        this.masseuseRepository = masseuseRepository;
        this.reservationBookingService = reservationBookingService;
        this.adminCredentials = adminCredentials;
        this.loginAttemptService = loginAttemptService;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featuredServices", serviceRepository.findByActiveTrueAndFeaturedTrueOrderByIdAsc());
        return "index";
    }

    @GetMapping("/sucursales")
    public String sucursales(Model model) {
        model.addAttribute("branches", branchRepository.findByActiveTrueOrderByIdAsc());
        List<Review> reviews = reviewRepository.findByStatusOrderByIdDesc("APROBADA");
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", reviews.size());
        model.addAttribute("averageRating", reviews.stream().mapToInt(review -> review.getRating()).average().orElse(0));
        return "sucursales";
    }

    @GetMapping("/servicios")
    public String servicios(Model model) {
        model.addAttribute("services", serviceRepository.findByActiveTrueOrderByIdAsc());
        return "servicios";
    }

    @GetMapping("/agendar")
    public String agendar(@RequestParam(required = false) Integer sucursalId,
                          @RequestParam(required = false) Long masajistaId,
                          @RequestParam(required = false) Long servicioId,
                          @RequestParam(required = false) String fecha, Model model) {
        populateBookingModel(model, sucursalId, masajistaId, servicioId, fecha);
        return "agendar";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpSession session, Model model, @RequestParam(defaultValue = "all") String period) {
        if (!Boolean.TRUE.equals(session.getAttribute("adminAuthenticated"))) {
            return "redirect:/admin/login";
        }

        lifecycle.reconcile();
        var reservations = reservationRepository.findAllByOrderByIdDesc();
        var branches = branchRepository.findAllByOrderByIdAsc();
        var selectedPeriod = DashboardPeriod.of(period, lifecycle.now().toLocalDate());
        var periodReservations = selectedPeriod.select(reservations);
        DashboardAnalytics analytics = DashboardAnalytics.from(periodReservations, branches);
        model.addAttribute("selectedPeriod", selectedPeriod);
        model.addAttribute("branchNames", branches.stream().collect(Collectors.toMap(b -> b.getId().intValue(), Branch::getName)));
        model.addAttribute("services", serviceRepository.findAllByOrderByIdAsc());
        model.addAttribute("branches", branches);
        model.addAttribute("masseuses", masseuseRepository.findAllByOrderByIdAsc());
        model.addAttribute("reservationCount", periodReservations.size());
        model.addAttribute("reservations", reservations);
        populateDashboardActivity(model, reservations, periodReservations);
        model.addAttribute("analytics", analytics);
        List<Review> reviews = reviewRepository.findAll();
        List<Review> approvedReviews = reviewRepository.findByStatusOrderByIdDesc("APROBADA");
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", approvedReviews.size());
        model.addAttribute("averageRating", approvedReviews.stream().mapToInt(review -> review.getRating()).average().orElse(0));
        return "admin-dashboard";
    }

    @GetMapping("/admin/login")
    public String adminLogin(Model model) {
        if (!adminCredentials.isConfigured()) {
            model.addAttribute("error", "El acceso administrativo no está configurado. Define SPA_ADMIN_USERNAME y SPA_ADMIN_PASSWORD.");
            model.addAttribute("loginDisabled", true);
        }
        model.addAttribute("loginBlocked", false);
        return "admin-login";
    }

    @PostMapping("/admin/login")
    public String authenticate(@RequestParam String username, @RequestParam String password,
                               HttpServletRequest request, Model model) {
        String clientAddress = request.getRemoteAddr();
        if (loginAttemptService.isBlocked(clientAddress)) {
            model.addAttribute("error", "Demasiados intentos. Espera 15 minutos antes de volver a intentar.");
            model.addAttribute("loginDisabled", false);
            model.addAttribute("loginBlocked", true);
            return "admin-login";
        }
        if (adminCredentials.matches(username, password)) {
            loginAttemptService.reset(clientAddress);
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
            request.getSession(true).setAttribute("adminAuthenticated", true);
            return "redirect:/admin/dashboard";
        }
        loginAttemptService.recordFailure(clientAddress);
        model.addAttribute("error", adminCredentials.isConfigured()
                ? "Usuario o contraseña incorrectos."
                : "El acceso administrativo no está configurado. Define SPA_ADMIN_USERNAME y SPA_ADMIN_PASSWORD.");
        model.addAttribute("loginDisabled", !adminCredentials.isConfigured());
        model.addAttribute("loginBlocked", loginAttemptService.isBlocked(clientAddress));
        return "admin-login";
    }

    @PostMapping("/admin/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login?logout";
    }

    @GetMapping("/admin/branches")
    public String branchesAdmin(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        model.addAttribute("branches", branchRepository.findAllByOrderByIdAsc());
        model.addAttribute("editingBranch", null);
        return "admin-branches";
    }

    @GetMapping("/admin/branches/edit")
    public String editBranch(@RequestParam Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        model.addAttribute("branches", branchRepository.findAllByOrderByIdAsc());
        model.addAttribute("editingBranch", branchRepository.findById(id).orElse(null));
        return "admin-branches";
    }

    @PostMapping("/admin/branches/save")
    public String saveBranch(@RequestParam(required = false) Long id, @RequestParam String name,
                             @RequestParam String sector, @RequestParam String address,
                             @RequestParam String openingHours, @RequestParam Double latitude,
                             @RequestParam Double longitude, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (id == null) {
            branchRepository.save(new Branch(name, sector, address, openingHours, latitude, longitude));
        } else {
            branchRepository.findById(id).ifPresent(branch -> {
                branch.update(name, sector, address, openingHours, latitude, longitude);
                branchRepository.save(branch);
            });
        }
        return "redirect:/admin/branches?saved";
    }

    @PostMapping("/admin/branches/toggle")
    public String toggleBranch(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        branchRepository.findById(id).ifPresent(branch -> {
            branch.setActive(!branch.isActive());
            branchRepository.save(branch);
        });
        return "redirect:/admin/branches";
    }

    @PostMapping("/admin/branches/delete")
    public String deleteBranch(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        branchRepository.findById(id).ifPresent(branch -> {
            branch.setActive(false);
            branchRepository.save(branch);
        });
        return "redirect:/admin/branches";
    }

    @GetMapping("/admin/reservations")
    public String reservationsAdmin(HttpSession session, Model model,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String date,
            @RequestParam(required = false) Integer branchId, @RequestParam(required = false) Long masseuseId,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        lifecycle.reconcile();

        boolean invalid = (!date.isBlank() && parseDate(date) == null)
                || (!status.isBlank() && !ReservationPolicy.STATES.contains(status)) || q.length() > 100;
        if (invalid) model.addAttribute("error", "Revisa la fecha, el estado y la búsqueda (máximo 100 caracteres).");
        var results = reservationRepository.findAll(ReservationFilters.matching(invalid ? "INVALID" : status,
                parseDate(date), branchId, masseuseId, q.length() > 100 ? "" : q),
                PageRequest.of(Math.max(0, Math.min(page, 100000)), 20,
                        Sort.by(Sort.Order.desc("reservationDate"), Sort.Order.desc("reservationTime"), Sort.Order.desc("id"))));
        if (results.isEmpty() && results.getTotalElements() > 0) {
            results = reservationRepository.findAll(ReservationFilters.matching(status, parseDate(date), branchId, masseuseId, q),
                    PageRequest.of(results.getTotalPages() - 1, 20, results.getSort()));
        }
        model.addAttribute("appointments", summaries(results.getContent()));
        model.addAttribute("resultPage", results);
        model.addAttribute("branches", branchRepository.findAllByOrderByIdAsc());
        model.addAttribute("masseuses", masseuseRepository.findAllByOrderByIdAsc());
        model.addAttribute("states", ReservationPolicy.STATES);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterDate", date);
        model.addAttribute("filterBranch", branchId);
        model.addAttribute("filterMasseuse", masseuseId);
        model.addAttribute("filterQuery", q.length() > 100 ? "" : q);
        return "admin-reservations";
    }

    @GetMapping("/admin/reservations/action")
    public String reservationAction(@RequestParam Long id, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "status") String operation, HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        lifecycle.reconcile();

        Reservation reservation = reservationRepository.findById(id).orElse(null);
        if (reservation == null) return "redirect:/admin/reservations";
        model.addAttribute("appointment", summaries(List.of(reservation)).getFirst());
        boolean valid = ("status".equals(operation)
                && ReservationPolicy.transitions(reservation, lifecycle.now()).contains(status)
                && !reservation.getStatus().equals(status));
        model.addAttribute("actionValid", valid);
        model.addAttribute("operation", operation);
        model.addAttribute("targetStatus", status);
        return "admin-reservation-action";
    }

    @PostMapping("/admin/reservations/status")
    public String updateReservationStatus(@RequestParam Long id, @RequestParam String status,
                                          HttpSession session, RedirectAttributes redirect) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        var result = reservationBookingService.changeStatus(id, status);
        redirect.addFlashAttribute(result.successful() ? "confirmation" : "error", result.message());
        return "redirect:/admin/reservations";
    }

    @PostMapping("/admin/reservations/delete")
    public String deleteReservation(@RequestParam Long id, HttpSession session, RedirectAttributes redirect) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, reservationBookingService.delete(id).message());
    }

    @GetMapping("/admin/services/edit")
    public String editService(@RequestParam Long id, HttpSession session, Model model,
                              @RequestParam(defaultValue = "all") String period) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        model.addAttribute("editingService", serviceRepository.findById(id).orElse(null));
        return adminDashboard(session, model, period);
    }

    @GetMapping("/admin/masseuses")
    public String masseusesAdmin(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        model.addAttribute("masseuses", masseuseRepository.findAllByOrderByIdAsc());
        model.addAttribute("editingMasseuse", null);
        return "admin-masseuses";
    }

    @GetMapping("/admin/masseuses/edit")
    public String editMasseuse(@RequestParam Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        model.addAttribute("masseuses", masseuseRepository.findAllByOrderByIdAsc());
        model.addAttribute("editingMasseuse", masseuseRepository.findById(id).orElse(null));
        return "admin-masseuses";
    }

    @PostMapping("/admin/masseuses/save")
    public String saveMasseuse(@RequestParam(required = false) Long id, @RequestParam String name,
                               @RequestParam String specialty, @RequestParam String phone,
                               @RequestParam String email, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (id == null) {
            masseuseRepository.save(new Masseuse(name, specialty, phone, email));
        } else {
            masseuseRepository.findById(id).ifPresent(masseuse -> {
                masseuse.update(name, specialty, phone, email);
                masseuseRepository.save(masseuse);
            });
        }
        return "redirect:/admin/masseuses?saved";
    }

    @PostMapping("/admin/masseuses/toggle")
    public String toggleMasseuse(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        masseuseRepository.findById(id).ifPresent(masseuse -> {
            masseuse.setActive(!masseuse.isActive());
            masseuseRepository.save(masseuse);
        });
        return "redirect:/admin/masseuses";
    }

    @PostMapping("/admin/masseuses/delete")
    public String deleteMasseuse(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        masseuseRepository.findById(id).ifPresent(masseuse -> {
            masseuse.setActive(false);
            masseuseRepository.save(masseuse);
        });
        return "redirect:/admin/masseuses";
    }

    @PostMapping("/admin/services/save")
    public String saveService(@RequestParam(required = false) Long id,
                              @RequestParam String name, @RequestParam String category,
                              @RequestParam String description, @RequestParam Integer durationMinutes,
                              @RequestParam BigDecimal price, @RequestParam String imageUrl,
                              @RequestParam(defaultValue = "false") boolean featured,
                              HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (id == null) {
            SpaService service = new SpaService(name, category, description, durationMinutes, price, imageUrl);
            service.setFeatured(featured);
            serviceRepository.save(service);
        } else {
            serviceRepository.findById(id).ifPresent(service -> {
                service.update(name, category, description, durationMinutes, price, imageUrl);
                service.setFeatured(featured);
                serviceRepository.save(service);
            });
        }
        return "redirect:/admin/dashboard?serviceSaved";
    }

    @PostMapping("/admin/services/toggle")
    public String toggleService(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        serviceRepository.findById(id).ifPresent(service -> {
            service.setActive(!service.isActive());
            serviceRepository.save(service);
        });
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/services/delete")
    public String deleteService(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        serviceRepository.findById(id).ifPresent(service -> {
            service.setActive(false);
            serviceRepository.save(service);
        });
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/resenas/save")
    public String saveReview(@RequestParam String customerName, @RequestParam Integer branchId,
                             @RequestParam Integer rating, @RequestParam String comment) {
        reviewRepository.save(new Review(customerName, branchId, rating, comment));
        return "redirect:/sucursales?reviewSent";
    }

    @PostMapping("/admin/reviews/approve")
    public String approveReview(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        reviewRepository.findById(id).ifPresent(review -> {
            review.approve();
            reviewRepository.save(review);
        });
        return "redirect:/admin/dashboard#resenas";
    }

    @PostMapping("/admin/reviews/delete")
    public String deleteReview(@RequestParam Long id, @RequestParam(defaultValue = "false") boolean confirmed, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (!confirmed) return "redirect:/admin/dashboard?reviewConfirmationRequired#resenas";
        reviewRepository.deleteById(id);
        return "redirect:/admin/dashboard#resenas";
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute("adminAuthenticated"));
    }

    @PostMapping("/reservas/confirmar")
    public String confirmReservation(@RequestParam Long servicioId, @RequestParam Integer sucursalId,
                                     @RequestParam Long masajistaId,
                                     @RequestParam String fecha, @RequestParam String horaSeleccionada,
                                     @RequestParam String clienteNombre, @RequestParam String clienteCedula,
                                     @RequestParam String clienteTelefono, @RequestParam String clienteEmail,
                                     Model model, HttpSession session) {
        LocalDate reservationDate = parseDate(fecha);
        ReservationBookingService.BookingResult result = reservationBookingService.book(servicioId, sucursalId,
            masajistaId, reservationDate, horaSeleccionada, clienteNombre, clienteCedula,
            clienteTelefono, clienteEmail);
        model.addAttribute(result.successful() ? "confirmation" : "error", result.message());
        if (result.successful()) {
            session.setAttribute("reservationReceipt", new ReservationReceipt(clienteEmail.strip(), result.accessCode(), Instant.now()));
            return "redirect:/reservas/confirmada";
        }
        populateBookingModel(model, sucursalId, masajistaId, servicioId, fecha);
        model.addAttribute("selectedTime", horaSeleccionada);
        model.addAttribute("clienteNombre", clienteNombre);
        model.addAttribute("clienteCedula", clienteCedula);
        model.addAttribute("clienteTelefono", clienteTelefono);
        model.addAttribute("clienteEmail", clienteEmail);
        return "agendar";
    }

    private record ReservationReceipt(String email, String code, Instant createdAt) { }

    @GetMapping("/reservas/confirmada")
    public String reservationConfirmed(HttpSession session, Model model) {
        Object value = session.getAttribute("reservationReceipt");
        if (!(value instanceof ReservationReceipt receipt)
                || Duration.between(receipt.createdAt(), Instant.now()).toMinutes() >= 30) {
            session.removeAttribute("reservationReceipt");
            return "redirect:/reservas/consultar";
        }
        if (!populateReservation(model, receipt.email(), receipt.code())) return "redirect:/reservas/consultar";
        return "reservation-confirmation";
    }

    @GetMapping({"/reservas/consultar", "/reservation-lookup"})
    public String reservationLookup() {
        return "reservation-lookup";
    }

    @PostMapping({"/reservas/consultar", "/reservation-lookup"})
    public String lookupReservation(@RequestParam String email, @RequestParam String accessCode, Model model) {
        if (!populateReservation(model, email, accessCode)) {
            model.addAttribute("error", "No encontramos una reserva con esos datos. Revisa el correo y el código privado e inténtalo de nuevo.");
        }
        return "reservation-lookup";
    }

    @PostMapping("/reservas/cancelar")
    public String cancelReservation(@RequestParam String email, @RequestParam String accessCode,
                                   @RequestParam(defaultValue = "false") boolean confirmed, Model model) {
        if (!populateReservation(model, email, accessCode)) {
            model.addAttribute("error", "No encontramos una reserva con esos datos.");
            return "reservation-lookup";
        }
        if (!confirmed) {
            model.addAttribute("confirmCancellation", true);
            return "reservation-lookup";
        }
        var result = reservationBookingService.cancel(email, accessCode);
        populateReservation(model, email, accessCode);
        model.addAttribute(result.successful() ? "confirmation" : "error", result.message());
        return "reservation-lookup";
    }

    private boolean populateReservation(Model model, String email, String code) {
        model.asMap().remove("appointment");
        model.addAttribute("lookupEmail", email);
        model.addAttribute("accessCode", code);
        var found = reservationBookingService.findReservation(email, code);
        if (found.isEmpty()) return false;
        model.addAttribute("appointment", summaries(List.of(found.get())).getFirst());
        return true;
    }

    private List<ReservationSummary> summaries(List<Reservation> reservations) {
        Map<Long, SpaService> services = serviceRepository.findAll().stream().collect(Collectors.toMap(SpaService::getId, Function.identity()));
        Map<Long, Branch> branches = branchRepository.findAll().stream().collect(Collectors.toMap(Branch::getId, Function.identity()));
        Map<Long, Masseuse> masseuses = masseuseRepository.findAll().stream().collect(Collectors.toMap(Masseuse::getId, Function.identity()));
        return reservations.stream().map(r -> ReservationSummary.from(r, services.get(r.getServiceId()),
                branches.get(r.getBranchId().longValue()), masseuses.get(r.getMasseuseId()), lifecycle.now())).toList();
    }

    private void populateDashboardActivity(Model model, List<Reservation> reservations, List<Reservation> periodReservations) {
        LocalDate today = lifecycle.now().toLocalDate();
        var active = reservations.stream().filter(r -> "PENDIENTE".equals(r.getStatus()) || "CONFIRMADA".equals(r.getStatus())).toList();
        var resolution = reservations.stream().filter(r -> "CONFIRMADA".equals(r.getStatus())
                && ReservationPolicy.hasStarted(r, lifecycle.now())).toList();
        model.addAttribute("resolutionCount", resolution.size());
        model.addAttribute("requiringResolution", summaries(resolution.stream()
                .sorted(java.util.Comparator.comparing(Reservation::getReservationDate).thenComparing(Reservation::getReservationTime))
                .limit(5).toList()));
        model.addAttribute("today", today);
        model.addAttribute("todayCount", active.stream().filter(r -> today.equals(r.getReservationDate())).count());
        model.addAttribute("pendingCount", reservations.stream().filter(r -> "PENDIENTE".equals(r.getStatus()) && ReservationPolicy.isFuture(r, lifecycle.now())).count());
        model.addAttribute("upcoming", summaries(active.stream().filter(r -> ReservationPolicy.canCancel(r, lifecycle.now()))
                .sorted(java.util.Comparator.comparing(Reservation::getReservationDate).thenComparing(Reservation::getReservationTime))
                .limit(5).toList()));
        model.addAttribute("recentAppointments", summaries(reservations.stream().limit(6).toList()));
        var distribution = new java.util.LinkedHashMap<String, Long>();
        ReservationPolicy.STATES.forEach(state -> distribution.put(state,
                periodReservations.stream().filter(r -> state.equals(r.getStatus())).count()));
        model.addAttribute("statusCounts", distribution);
    }

    @GetMapping("/agendar/disponibilidad")
    public String availability(@RequestParam(required = false) Integer sucursalId,
                               @RequestParam(required = false) Long masajistaId,
                               @RequestParam(required = false) Long servicioId,
                               @RequestParam(required = false) String fecha, Model model) {
        populateBookingModel(model, sucursalId, masajistaId, servicioId, fecha);
        return "agendar";
    }

    private void populateBookingModel(Model model, Integer branchId, Long masseuseId, Long serviceId, String date) {
        LocalDate selectedDate = parseDate(date);
        model.addAttribute("services", serviceRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("branches", branchRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("masseuses", masseuseRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("availableSlots", reservationBookingService.availableSlots(
                branchId, masseuseId, serviceId, selectedDate));
        model.addAttribute("selectedBranchId", branchId);
        model.addAttribute("selectedMasseuseId", masseuseId);
        model.addAttribute("selectedServiceId", serviceId);
        model.addAttribute("selectedDate", date);
        model.addAttribute("today", lifecycle.now().toLocalDate());
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
