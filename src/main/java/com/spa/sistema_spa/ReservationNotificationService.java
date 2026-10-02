package com.spa.sistema_spa;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ReservationNotificationService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public ReservationNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                          @Value("${spa.mail.from:}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
    }

    public boolean sendConfirmation(Reservation reservation, String accessCode,
                                   String serviceName, String branchName) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || fromAddress.isBlank()) {
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(reservation.getCustomerEmail());
        message.setSubject("Confirmación de reserva - SpaMoonBeauty");
        message.setText("Hola " + reservation.getCustomerName() + ",\n\n"
                + "Registramos tu reserva en estado PENDIENTE.\n"
                + "Servicio: " + serviceName + "\n"
                + "Sucursal: " + branchName + "\n"
                + "Fecha: " + reservation.getReservationDate() + "\n"
                + "Hora: " + reservation.getReservationTime() + "\n\n"
                + "Código privado para consultar o cancelar tu reserva: " + accessCode + "\n"
                + "Guárdalo; no lo compartas con otras personas.\n\nSpaMoonBeauty");
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            return false;
        }
    }
}