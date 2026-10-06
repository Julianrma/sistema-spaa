package com.spa.sistema_spa;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReservationBookingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[\\p{L}\\p{N}-]{6,20}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+\\d][\\d\\s().-]{6,20}$");

    private final SpaServiceRepository serviceRepository;
    private final ReservationRepository reservationRepository;
    private final BranchRepository branchRepository;
    private final MasseuseRepository masseuseRepository;
    private final ReservationNotificationService notificationService;
    private final ReservationLifecycle lifecycle;
    private final SecureRandom secureRandom = new SecureRandom();

    public ReservationBookingService(SpaServiceRepository serviceRepository,
                                     ReservationRepository reservationRepository,
                                     BranchRepository branchRepository,
                                     MasseuseRepository masseuseRepository,
                                     ReservationNotificationService notificationService, ReservationLifecycle lifecycle) {
        this.serviceRepository = serviceRepository;
        this.reservationRepository = reservationRepository;
        this.branchRepository = branchRepository;
        this.masseuseRepository = masseuseRepository;
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
    }

    @Transactional(readOnly = true)
    public List<String> availableSlots(Integer branchId, Long masseuseId, Long serviceId, LocalDate date) {
        return availableSlots(branchId, masseuseId, serviceId, date, null);
    }

    private List<String> availableSlots(Integer branchId, Long masseuseId, Long serviceId, LocalDate date,
                                        Long excludedReservationId) {
        if (branchId == null || masseuseId == null || serviceId == null || date == null
                || date.isBefore(lifecycle.now().toLocalDate())) {
            return List.of();
        }

        Branch branch = branchRepository.findById(branchId.longValue()).filter(Branch::isActive).orElse(null);
        SpaService service = serviceRepository.findById(serviceId).filter(SpaService::isActive).orElse(null);
        Masseuse masseuse = masseuseRepository.findById(masseuseId).filter(Masseuse::isActive).orElse(null);
        if (branch == null || service == null || masseuse == null) {
            return List.of();
        }

        List<Reservation> existing = reservationRepository
                .findByMasseuseIdAndReservationDateAndStatusNot(masseuseId, date, "CANCELADA")
                .stream().filter(r -> !"EXPIRADA".equals(r.getStatus()) && !ReservationPolicy.shouldExpire(r, lifecycle.now()))
                .filter(r -> excludedReservationId == null || !excludedReservationId.equals(r.getId()))
                .toList();
        if (service.getDurationMinutes() == null) return List.of();
        return BookingAvailability.availableSlots(branch.getOpeningHours(), date.getDayOfWeek(),
                service.getDurationMinutes(), existing, serviceDurations(existing)).stream()
                .filter(slot -> date.atTime(ReservationPolicy.parseTime(slot)).isAfter(lifecycle.now())).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResult book(Long serviceId, Integer branchId, Long masseuseId, LocalDate date,
                              String selectedTime, String customerName, String customerId,
                              String customerPhone, String customerEmail) {
        if (date == null) {
            return BookingResult.failure("La fecha de la reserva no es válida.");
        }
        if (date.isBefore(lifecycle.now().toLocalDate())) {
            return BookingResult.failure("No puedes reservar una fecha pasada.");
        }
        if (!validCustomerDetails(customerName, customerId, customerPhone, customerEmail)) {
            return BookingResult.failure("Revisa el nombre, la identificación, el teléfono y el correo ingresados.");
        }

        Masseuse masseuse = masseuseId == null ? null : masseuseRepository.findByIdForUpdate(masseuseId)
                .filter(Masseuse::isActive).orElse(null);
        Branch branch = branchId == null ? null : branchRepository.findById(branchId.longValue())
                .filter(Branch::isActive).orElse(null);
        SpaService service = serviceId == null ? null : serviceRepository.findById(serviceId)
                .filter(SpaService::isActive).orElse(null);
        if (masseuse == null || branch == null || service == null) {
            return BookingResult.failure("La sucursal, el servicio o la masajista ya no están disponibles.");
        }

        List<String> slots = availableSlots(branchId, masseuseId, serviceId, date);
        if (selectedTime == null || !slots.contains(selectedTime)) {
            return BookingResult.failure("Ese turno ya no está disponible o no cabe dentro del horario de la sucursal.");
        }

        String accessCode = generateAccessCode();
        Reservation reservation = new Reservation(serviceId, branchId, masseuseId,
                customerName.strip(), customerId.strip(), customerPhone.strip(), customerEmail.strip(), date, selectedTime);
        reservation.setAccessCodeHash(hashAccessCode(accessCode));
        reservationRepository.save(reservation);
        String serviceName = service.getName();
        String branchName = branch.getName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.sendConfirmation(reservation, accessCode, serviceName, branchName);
            }
        });
        return BookingResult.success("Tu cita fue registrada. Guarda el código privado que aparece aquí para consultarla o cancelarla.", accessCode);
    }

    @Transactional
    public Optional<Reservation> findReservation(String email, String accessCode) {
        if (!validLookupCredentials(email, accessCode)) {
            return Optional.empty();
        }
        var found = reservationRepository.findForUpdateByAccessCode(hashAccessCode(accessCode.strip()), email.strip());
        found.ifPresent(lifecycle::reconcileLocked);
        return found;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResult cancel(String email, String accessCode) {
        if (!validLookupCredentials(email, accessCode)) {
            return BookingResult.failure("No encontramos una reserva con esos datos.");
        }
        Reservation reservation = reservationRepository.findForUpdateByAccessCode(
                hashAccessCode(accessCode.strip()), email.strip()).orElse(null);
        if (reservation == null) {
            return BookingResult.failure("No encontramos una reserva con esos datos.");
        }
        lifecycle.reconcileLocked(reservation);
        if (!lockMasseuse(reservation)) return BookingResult.failure("La masajista ya no existe.");
        if ("CANCELADA".equals(reservation.getStatus())) {
            return BookingResult.success("La reserva ya estaba cancelada.", accessCode);
        }
        if (!ReservationPolicy.canCancel(reservation, lifecycle.now())) {
            return BookingResult.failure("Solo puedes cancelar citas pendientes o confirmadas antes de su hora de inicio.");
        }
        reservation.setStatus("CANCELADA");
        reservationRepository.save(reservation);
        return BookingResult.success("La reserva fue cancelada.", accessCode);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResult changeStatus(Long id, String targetStatus) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id).orElse(null);
        if (reservation == null) return BookingResult.failure("La reserva ya no existe.");
        lifecycle.reconcileLocked(reservation);
        if (!lockMasseuse(reservation)) return BookingResult.failure("La masajista ya no existe.");
        String current = reservation.getStatus();
        if (!validTransition(reservation, targetStatus)) {
            return BookingResult.failure("Transición de estado no permitida.");
        }
        if (current.equals(targetStatus)) return BookingResult.success("La reserva ya tiene ese estado.");
        if ("CANCELADA".equals(current) && (reservation.getReservationTime() == null || !availableSlots(reservation.getBranchId(),
                reservation.getMasseuseId(), reservation.getServiceId(), reservation.getReservationDate(),
                reservation.getId()).contains(reservation.getReservationTime()))) {
            return BookingResult.failure("No se puede reactivar: el turno está ocupado o ya no está disponible.");
        }
        reservation.setStatus(targetStatus);
        reservationRepository.save(reservation);
        return BookingResult.success("Estado actualizado.");
    }

    // Reservation history is permanent, including terminal states.
    public BookingResult delete(Long id) {
        return BookingResult.failure("Las reservas forman parte del historial y no se eliminan.");
    }

    // Existing reservations: lock their row first, then the shared agenda owner.
    // Creation locks only the agenda owner and never locks existing reservation rows.
    private boolean lockMasseuse(Reservation reservation) {
        return reservation.getMasseuseId() == null
                || masseuseRepository.findByIdForUpdate(reservation.getMasseuseId()).isPresent();
    }

    private boolean validTransition(Reservation reservation, String target) {
        return target != null && ReservationPolicy.transitions(reservation, lifecycle.now()).contains(target);
    }

    private String generateAccessCode() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashAccessCode(String accessCode) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(accessCode.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private Map<Long, Integer> serviceDurations(List<Reservation> reservations) {
        List<Long> serviceIds = reservations.stream().map(Reservation::getServiceId).distinct().toList();
        Map<Long, Integer> durations = new HashMap<>();
        serviceRepository.findAllById(serviceIds)
                .forEach(service -> durations.put(service.getId(), service.getDurationMinutes()));
        return durations;
    }

    private boolean validCustomerDetails(String name, String customerId, String phone, String email) {
        if (name == null || name.strip().length() < 2 || name.strip().length() > 255
                || customerId == null || phone == null || email == null || email.strip().length() > 254) {
            return false;
        }
        return ID_PATTERN.matcher(customerId.strip()).matches()
                && PHONE_PATTERN.matcher(phone.strip()).matches()
                && phone.chars().filter(Character::isDigit).count() >= 7
                && EMAIL_PATTERN.matcher(email.strip()).matches();
    }

    private boolean validLookupCredentials(String email, String code) {
        return email != null && !email.isBlank() && email.length() <= 254
                && code != null && !code.isBlank() && code.length() <= 128;
    }

    public record BookingResult(boolean successful, String message, String accessCode) {
        private static BookingResult success(String message) {
            return success(message, null);
        }

        private static BookingResult success(String message, String accessCode) {
            return new BookingResult(true, message, accessCode);
        }

        private static BookingResult failure(String message) {
            return new BookingResult(false, message, null);
        }
    }
}
