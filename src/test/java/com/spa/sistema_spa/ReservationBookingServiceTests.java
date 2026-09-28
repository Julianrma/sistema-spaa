package com.spa.sistema_spa;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ReservationBookingServiceTests {
    private final SpaServiceRepository services = mockOf(SpaServiceRepository.class);
    private final ReservationRepository reservations = mockOf(ReservationRepository.class);
    private final BranchRepository branches = mockOf(BranchRepository.class);
    private final MasseuseRepository masseuses = mockOf(MasseuseRepository.class);
    private final ReservationNotificationService notifications = mockOf(ReservationNotificationService.class);
    private final ReservationBookingService booking = new ReservationBookingService(
            services, reservations, branches, masseuses, notifications);
    private static final LocalDate DATE = LocalDate.of(2099, 1, 5);
    private Reservation original;

    private static <T> T mockOf(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS));
    }

    @BeforeEach
    void setup() {
        original = reservation(1L, 10L, "09:00", "CANCELADA");
        when(reservations.findByIdForUpdate(1L)).thenReturn(Optional.of(original));
        Masseuse masseuse = new Masseuse("Nombre", "Especialidad", "0999999999", "m@example.com");
        when(masseuses.findByIdForUpdate(3L)).thenReturn(Optional.of(masseuse));
        when(masseuses.findById(3L)).thenReturn(Optional.of(masseuse));
        when(branches.findById(1L)).thenReturn(Optional.of(
                new Branch("Sede", "Sector", "Dirección", "Lun - Dom: 08h00 - 20h00", 0, 0)));
        when(services.findById(10L)).thenReturn(Optional.of(service(10L, 60)));
        when(reservations.findByMasseuseIdAndReservationDateAndStatusNot(3L, DATE, "CANCELADA"))
                .thenReturn(List.of());
        when(services.findAllById(any())).thenReturn(List.of(service(10L, 60), service(20L, 120)));
    }

    @Test
    void reactivatesWithoutConflictAfterLockingAgenda() {
        assertTrue(booking.changeStatus(1L, "CONFIRMADA").successful());
        assertEquals("CONFIRMADA", original.getStatus());
        var order = inOrder(reservations, masseuses);
        order.verify(reservations).findByIdForUpdate(1L);
        order.verify(masseuses).findByIdForUpdate(3L);
        order.verify(reservations).findByMasseuseIdAndReservationDateAndStatusNot(3L, DATE, "CANCELADA");
        order.verify(reservations).save(original);
        verifyNoInteractions(notifications);
    }

    @Test
    void rejectsReactivationWhenAnotherCustomerHasTakenTheSlot() {
        occupied(reservation(2L, 10L, "09:00", "CONFIRMADA"));
        assertRejected("CONFIRMADA");
    }

    @Test
    void rejectsPartialOverlapForSameMasseuse() {
        occupied(reservation(2L, 10L, "09:30", "PENDIENTE"));
        assertRejected("CONFIRMADA");
    }

    @Test
    void usesFullDurationOfDifferentExistingService() {
        occupied(reservation(2L, 20L, "08:00", "CONFIRMADA"));
        assertRejected("CONFIRMADA");
    }

    @Test
    void usesFullDurationOfReactivatedService() {
        when(services.findById(10L)).thenReturn(Optional.of(service(10L, 120)));
        occupied(reservation(2L, 20L, "10:30", "CONFIRMADA"));
        assertRejected("CONFIRMADA");
    }

    @Test
    void allowsConsecutiveReservations() {
        occupied(reservation(2L, 10L, "08:00", "CONFIRMADA"),
                reservation(3L, 10L, "10:00", "CONFIRMADA"));
        assertTrue(booking.changeStatus(1L, "CONFIRMADA").successful());
    }

    @Test
    void ignoresCancelledReservations() {
        occupied(reservation(2L, 10L, "09:00", "CANCELADA"));
        assertTrue(booking.changeStatus(1L, "CONFIRMADA").successful());
    }

    @Test
    void excludesOwnIdEvenWhenQueryContainsIt() {
        occupied(reservation(1L, 10L, "09:00", "CONFIRMADA"));
        assertTrue(booking.changeStatus(1L, "CONFIRMADA").successful());
    }

    @Test
    void reactivationToPendingAlsoChecksConflicts() {
        occupied(reservation(2L, 10L, "09:00", "CONFIRMADA"));
        assertRejected("PENDIENTE");
    }

    @Test
    void rejectsInvalidAndUnknownTransitions() {
        for (String target : new String[] {"COMPLETADA", "UNKNOWN", "", null}) {
            assertRejected(target);
        }
        original.setStatus("COMPLETADA");
        assertFalse(booking.changeStatus(1L, "PENDIENTE").successful());
        assertFalse(booking.changeStatus(1L, "CANCELADA").successful());
        original.setStatus("UNKNOWN");
        assertFalse(booking.changeStatus(1L, "CONFIRMADA").successful());
        original.setStatus(null);
        assertFalse(booking.changeStatus(1L, "CONFIRMADA").successful());
        verify(reservations, never()).save(any());
    }

    @Test
    void preservesAllowedTransitionsAndIdempotentStates() {
        for (String current : List.of("PENDIENTE", "CONFIRMADA")) {
            for (String target : List.of("PENDIENTE", "CONFIRMADA", "COMPLETADA", "CANCELADA")) {
                original.setStatus(current);
                assertTrue(booking.changeStatus(1L, target).successful());
                assertEquals(target, original.getStatus());
            }
        }
        original.setStatus("COMPLETADA");
        assertTrue(booking.changeStatus(1L, "COMPLETADA").successful());
    }

    @Test
    void cancellationRequiresMatchingCodeAndEmailAndIsIdempotent() throws Exception {
        original.setStatus("CONFIRMADA");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("private-code".getBytes(StandardCharsets.UTF_8)));
        when(reservations.findForUpdateByAccessCode(hash, "client@example.com"))
                .thenReturn(Optional.of(original));
        assertTrue(booking.cancel("client@example.com", "private-code").successful());
        assertEquals("CANCELADA", original.getStatus());
        assertTrue(booking.cancel("client@example.com", "private-code").successful());
        verify(reservations, times(1)).save(original);
        assertFalse(booking.cancel("other@example.com", "private-code").successful());
        assertFalse(booking.cancel("client@example.com", "wrong-code").successful());
        assertFalse(booking.cancel(null, null).successful());
        var order = inOrder(reservations, masseuses);
        order.verify(reservations).findForUpdateByAccessCode(hash, "client@example.com");
        order.verify(masseuses).findByIdForUpdate(3L);
        order.verify(reservations).save(original);
    }

    @Test
    void cancellationUsesLockedCurrentStateInsteadOfStaleRead() {
        original.setStatus("COMPLETADA");
        when(reservations.findForUpdateByAccessCode(anyString(), anyString())).thenReturn(Optional.of(original));
        assertFalse(booking.cancel("client@example.com", "code").successful());
        verify(reservations, never()).findByAccessCodeHashAndCustomerEmailIgnoreCase(any(), any());
        verify(reservations, never()).save(any());
        assertEquals("COMPLETADA", original.getStatus());
    }

    @Test
    void rejectsCancellationOfUnknownOrNullState() {
        when(reservations.findForUpdateByAccessCode(anyString(), anyString())).thenReturn(Optional.of(original));
        for (String state : new String[] {"UNKNOWN", null}) {
            original.setStatus(state);
            assertFalse(booking.cancel("client@example.com", "code").successful());
            assertEquals(state, original.getStatus());
        }
        verify(reservations, never()).save(any());
    }

    @Test
    void rejectsReactivationOfMalformedReservation() {
        ReflectionTestUtils.setField(original, "reservationTime", null);
        assertRejected("CONFIRMADA");
    }

    @Test
    void deletionLocksRowAndAgendaAndIsSafeWhenAlreadyDeleted() {
        assertTrue(booking.delete(1L).successful());
        var order = inOrder(reservations, masseuses);
        order.verify(reservations).findByIdForUpdate(1L);
        order.verify(masseuses).findByIdForUpdate(3L);
        order.verify(reservations).delete(original);
        when(reservations.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertTrue(booking.delete(1L).successful());
        assertFalse(booking.changeStatus(1L, "CONFIRMADA").successful());
        verify(reservations, times(1)).delete(original);
        verify(reservations, never()).save(any());
    }

    @Test
    void legacyReservationWithoutMasseuseCannotBeReactivated() {
        ReflectionTestUtils.setField(original, "masseuseId", null);
        assertRejected("CONFIRMADA");
        assertTrue(booking.delete(1L).successful());
    }

    @Test
    void creationOnlyNotifiesAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(booking.book(10L, 1, 3L, DATE, "09:00", "Cliente", "12345678",
                    "0999999999", "client@example.com").successful());
            verifyNoInteractions(notifications);
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.getFirst().afterCommit();
            verify(notifications).sendConfirmation(any(), anyString(), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rollbackDoesNotSendConfirmation() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(booking.book(10L, 1, 3L, DATE, "09:00", "Cliente", "12345678",
                    "0999999999", "client@example.com").successful());
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(
                    org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(notifications);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void occupied(Reservation... existing) {
        when(reservations.findByMasseuseIdAndReservationDateAndStatusNot(3L, DATE, "CANCELADA"))
                .thenReturn(List.of(existing));
    }

    private void assertRejected(String target) {
        assertFalse(booking.changeStatus(1L, target).successful());
        assertEquals("CANCELADA", original.getStatus());
        verify(reservations, never()).save(any());
    }

    private Reservation reservation(Long id, Long service, String time, String state) {
        Reservation reservation = new Reservation(service, 1, 3L, "Cliente " + id, "12345678",
                "0999999999", "client@example.com", DATE, time);
        ReflectionTestUtils.setField(reservation, "id", id);
        reservation.setStatus(state);
        return reservation;
    }

    private SpaService service(Long id, int duration) {
        SpaService service = new SpaService("Servicio", "Categoría", "Descripción", duration,
                BigDecimal.TEN, "https://example.com/image.jpg");
        ReflectionTestUtils.setField(service, "id", id);
        return service;
    }
}
