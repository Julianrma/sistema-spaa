package com.spa.sistema_spa;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationBookingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[\\p{L}\\p{N}-]{6,20}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+\\d][\\d\\s().-]{6,20}$");

    private final SpaServiceRepository serviceRepository;
    private final ReservationRepository reservationRepository;
    private final BranchRepository branchRepository;
    private final MasseuseRepository masseuseRepository;

    public ReservationBookingService(SpaServiceRepository serviceRepository,
                                     ReservationRepository reservationRepository,
                                     BranchRepository branchRepository,
                                     MasseuseRepository masseuseRepository) {
        this.serviceRepository = serviceRepository;
        this.reservationRepository = reservationRepository;
        this.branchRepository = branchRepository;
        this.masseuseRepository = masseuseRepository;
    }

    @Transactional(readOnly = true)
    public List<String> availableSlots(Integer branchId, Long masseuseId, Long serviceId, LocalDate date) {
        if (branchId == null || masseuseId == null || serviceId == null || date == null
                || date.isBefore(LocalDate.now())) {
            return List.of();
        }

        Branch branch = branchRepository.findById(branchId.longValue()).filter(Branch::isActive).orElse(null);
        SpaService service = serviceRepository.findById(serviceId).filter(SpaService::isActive).orElse(null);
        Masseuse masseuse = masseuseRepository.findById(masseuseId).filter(Masseuse::isActive).orElse(null);
        if (branch == null || service == null || masseuse == null) {
            return List.of();
        }

        List<Reservation> existing = reservationRepository
                .findByMasseuseIdAndReservationDateAndStatusNot(masseuseId, date, "CANCELADA");
        return BookingAvailability.availableSlots(branch.getOpeningHours(), date.getDayOfWeek(),
                service.getDurationMinutes(), existing, serviceDurations(existing));
    }

    @Transactional
    public BookingResult book(Long serviceId, Integer branchId, Long masseuseId, LocalDate date,
                              String selectedTime, String customerName, String customerId,
                              String customerPhone, String customerEmail) {
        if (date == null) {
            return BookingResult.failure("La fecha de la reserva no es válida.");
        }
        if (date.isBefore(LocalDate.now())) {
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

        reservationRepository.save(new Reservation(serviceId, branchId, masseuseId,
                customerName.strip(), customerId.strip(), customerPhone.strip(), customerEmail.strip(), date, selectedTime));
        return BookingResult.success("Tu cita fue registrada. Te contactaremos para confirmarla.");
    }

    private Map<Long, Integer> serviceDurations(List<Reservation> reservations) {
        List<Long> serviceIds = reservations.stream().map(Reservation::getServiceId).distinct().toList();
        Map<Long, Integer> durations = new HashMap<>();
        serviceRepository.findAllById(serviceIds)
                .forEach(service -> durations.put(service.getId(), service.getDurationMinutes()));
        return durations;
    }

    private boolean validCustomerDetails(String name, String customerId, String phone, String email) {
        if (name == null || name.strip().length() < 2 || customerId == null || phone == null || email == null) {
            return false;
        }
        return ID_PATTERN.matcher(customerId.strip()).matches()
                && PHONE_PATTERN.matcher(phone.strip()).matches()
                && phone.chars().filter(Character::isDigit).count() >= 7
                && EMAIL_PATTERN.matcher(email.strip()).matches();
    }

    public record BookingResult(boolean successful, String message) {
        private static BookingResult success(String message) {
            return new BookingResult(true, message);
        }

        private static BookingResult failure(String message) {
            return new BookingResult(false, message);
        }
    }
}