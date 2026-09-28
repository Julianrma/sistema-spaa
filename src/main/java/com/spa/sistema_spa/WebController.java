package com.spa.sistema_spa;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.springframework.ui.Model;

@Controller
public class WebController {

    private final SpaServiceRepository serviceRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final BranchRepository branchRepository;
    private final MasseuseRepository masseuseRepository;
    private final String adminUsername;
    private final String adminPassword;

    public WebController(SpaServiceRepository serviceRepository, ReservationRepository reservationRepository,
                         ReviewRepository reviewRepository,
                         BranchRepository branchRepository, MasseuseRepository masseuseRepository,
                         org.springframework.core.env.Environment environment) {
        this.serviceRepository = serviceRepository;
        this.reservationRepository = reservationRepository;
        this.reviewRepository = reviewRepository;
        this.branchRepository = branchRepository;
        this.masseuseRepository = masseuseRepository;
        this.adminUsername = environment.getProperty("spa.admin.username", "admin");
        this.adminPassword = environment.getProperty("spa.admin.password", "1234");
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("services", serviceRepository.findByActiveTrueOrderByIdAsc());
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

    @GetMapping("/agendar")
    public String agendar(@RequestParam(required = false) Integer sucursalId,
                          @RequestParam(required = false) Long masajistaId,
                          @RequestParam(required = false) Long servicioId,
                          @RequestParam(required = false) String fecha, Model model) {
        model.addAttribute("services", serviceRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("branches", branchRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("masseuses", masseuseRepository.findByActiveTrueOrderByNameAsc());
        LocalDate selectedDate = fecha == null || fecha.isBlank() ? null : LocalDate.parse(fecha);
        model.addAttribute("availableSlots", availableSlots(masajistaId, selectedDate));
        model.addAttribute("selectedBranchId", sucursalId);
        model.addAttribute("selectedMasseuseId", masajistaId);
        model.addAttribute("selectedServiceId", servicioId);
        model.addAttribute("selectedDate", fecha);
        return "agendar";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpSession session, Model model) {
        if (!Boolean.TRUE.equals(session.getAttribute("adminAuthenticated"))) {
            return "redirect:/admin/login";
        }
        var reservations = reservationRepository.findAllByOrderByReservationDateAscReservationTimeAsc();
        var branches = branchRepository.findAllByOrderByIdAsc();
        DashboardAnalytics analytics = DashboardAnalytics.from(reservations, branches);
        model.addAttribute("services", serviceRepository.findAllByOrderByIdAsc());
        model.addAttribute("branches", branches);
        model.addAttribute("masseuses", masseuseRepository.findAllByOrderByIdAsc());
        model.addAttribute("reservationCount", reservations.size());
        model.addAttribute("reservations", reservations);
        model.addAttribute("analytics", analytics);
        List<Review> reviews = reviewRepository.findAll();
        List<Review> approvedReviews = reviewRepository.findByStatusOrderByIdDesc("APROBADA");
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", approvedReviews.size());
        model.addAttribute("averageRating", approvedReviews.stream().mapToInt(review -> review.getRating()).average().orElse(0));
        return "admin-dashboard";
    }

    @GetMapping("/admin/login")
    public String adminLogin() { return "admin-login"; }

    @PostMapping("/admin/login")
    public String authenticate(@RequestParam String username, @RequestParam String password,
                               HttpSession session, Model model) {
        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            session.setAttribute("adminAuthenticated", true);
            return "redirect:/admin/dashboard";
        }
        model.addAttribute("error", "Usuario o contraseña incorrectos.");
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
        branchRepository.deleteById(id);
        return "redirect:/admin/branches";
    }

    @GetMapping("/admin/reservations")
    public String reservationsAdmin(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        model.addAttribute("reservations", reservationRepository.findAllByOrderByReservationDateAscReservationTimeAsc());
        model.addAttribute("branches", branchRepository.findAllByOrderByIdAsc());
        model.addAttribute("services", serviceRepository.findAllByOrderByIdAsc());
        model.addAttribute("masseuses", masseuseRepository.findAllByOrderByIdAsc());
        return "admin-reservations";
    }

    @PostMapping("/admin/reservations/status")
    public String updateReservationStatus(@RequestParam Long id, @RequestParam String status, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (List.of("PENDIENTE", "CONFIRMADA", "COMPLETADA", "CANCELADA").contains(status)) {
            reservationRepository.findById(id).ifPresent(reservation -> {
                reservation.setStatus(status);
                reservationRepository.save(reservation);
            });
        }
        return "redirect:/admin/reservations";
    }

    @PostMapping("/admin/reservations/delete")
    public String deleteReservation(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        reservationRepository.deleteById(id);
        return "redirect:/admin/reservations";
    }

    @GetMapping("/admin/services/edit")
    public String editService(@RequestParam Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        model.addAttribute("services", serviceRepository.findAllByOrderByIdAsc());
        model.addAttribute("branches", branchRepository.findAllByOrderByIdAsc());
        model.addAttribute("masseuses", masseuseRepository.findAllByOrderByIdAsc());
        model.addAttribute("editingService", serviceRepository.findById(id).orElse(null));
        var reservations = reservationRepository.findAllByOrderByReservationDateAscReservationTimeAsc();
        model.addAttribute("reservationCount", reservations.size());
        model.addAttribute("reservations", reservations);
        model.addAttribute("analytics", DashboardAnalytics.from(reservations, branchRepository.findAllByOrderByIdAsc()));
        List<Review> reviews = reviewRepository.findAll();
        List<Review> approvedReviews = reviewRepository.findByStatusOrderByIdDesc("APROBADA");
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", approvedReviews.size());
        model.addAttribute("averageRating", approvedReviews.stream().mapToInt(review -> review.getRating()).average().orElse(0));
        return "admin-dashboard";
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
        masseuseRepository.deleteById(id);
        return "redirect:/admin/masseuses";
    }

    @PostMapping("/admin/services/save")
    public String saveService(@RequestParam(required = false) Long id,
                              @RequestParam String name, @RequestParam String category,
                              @RequestParam String description, @RequestParam Integer durationMinutes,
                              @RequestParam BigDecimal price, @RequestParam String imageUrl,
                              HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
        if (id == null) {
            serviceRepository.save(new SpaService(name, category, description, durationMinutes, price, imageUrl));
        } else {
            serviceRepository.findById(id).ifPresent(service -> {
                service.update(name, category, description, durationMinutes, price, imageUrl);
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
        serviceRepository.deleteById(id);
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
    public String deleteReview(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/admin/login";
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
                                     Model model) {
        LocalDate reservationDate = LocalDate.parse(fecha);
        boolean validSlot = availableSlotValues().contains(horaSeleccionada);
        boolean occupied = reservationRepository.findByMasseuseIdAndReservationDateAndStatusNot(masajistaId, reservationDate, "CANCELADA")
                .stream().anyMatch(reservation -> reservation.getReservationTime().equals(horaSeleccionada));
        if (reservationDate.isBefore(LocalDate.now()) || !validSlot || occupied) {
            model.addAttribute("error", occupied ? "Ese horario ya está ocupado para la masajista elegida."
                : (!validSlot ? "Selecciona una hora entre las 09:00 y las 18:00." : "No puedes reservar una fecha pasada."));
        } else {
            reservationRepository.save(new Reservation(servicioId, sucursalId, masajistaId, clienteNombre, clienteCedula,
                    clienteTelefono, clienteEmail, reservationDate, horaSeleccionada));
            model.addAttribute("confirmation", "Tu cita fue registrada. Te contactaremos para confirmarla.");
        }
        model.addAttribute("services", serviceRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("branches", branchRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("masseuses", masseuseRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("availableSlots", availableSlots(masajistaId, reservationDate));
        model.addAttribute("selectedBranchId", sucursalId);
        model.addAttribute("selectedMasseuseId", masajistaId);
        model.addAttribute("selectedServiceId", servicioId);
        model.addAttribute("selectedDate", fecha);
        return "agendar";
    }

    @GetMapping("/agendar/disponibilidad")
    public String availability(@RequestParam(required = false) Integer sucursalId,
                               @RequestParam(required = false) Long masajistaId,
                               @RequestParam(required = false) Long servicioId,
                               @RequestParam(required = false) String fecha, Model model) {
        model.addAttribute("services", serviceRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("branches", branchRepository.findByActiveTrueOrderByIdAsc());
        model.addAttribute("masseuses", masseuseRepository.findByActiveTrueOrderByNameAsc());
        LocalDate selectedDate = fecha == null || fecha.isBlank() ? null : LocalDate.parse(fecha);
        model.addAttribute("availableSlots", availableSlots(masajistaId, selectedDate));
        model.addAttribute("selectedBranchId", sucursalId);
        model.addAttribute("selectedMasseuseId", masajistaId);
        model.addAttribute("selectedServiceId", servicioId);
        model.addAttribute("selectedDate", fecha);
        return "agendar";
    }

    private List<String> availableSlots(Long masseuseId, LocalDate date) {
        List<String> slots = availableSlotValues();
        if (masseuseId == null || date == null) return slots;
        List<String> occupied = reservationRepository.findByMasseuseIdAndReservationDateAndStatusNot(masseuseId, date, "CANCELADA")
                .stream().map(reservation -> reservation.getReservationTime()).collect(Collectors.toList());
        slots.removeAll(occupied);
        return slots;
    }

    private List<String> availableSlotValues() {
        List<String> slots = new ArrayList<>();
        for (int hour = 9; hour <= 18; hour++) {
            slots.add(String.format("%02d:00", hour));
        }
        return slots;
    }
}