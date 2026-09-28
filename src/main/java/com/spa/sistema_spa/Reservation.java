package com.spa.sistema_spa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDate;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "service_id", nullable = false)
    private Long serviceId;
    @Column(name = "branch_id", nullable = false)
    private Integer branchId;
    @Column(name = "masseuse_id")
    private Long masseuseId;
    @Column(name = "customer_name", nullable = false)
    private String customerName;
    @Column(name = "customer_id_number", nullable = false)
    private String customerIdNumber;
    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;
    @Column(name = "customer_email", nullable = false)
    private String customerEmail;
    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;
    @Column(name = "reservation_time", nullable = false)
    private String reservationTime;
    @Column(nullable = false)
    private String status = "PENDIENTE";

    protected Reservation() {
    }

    public Reservation(Long serviceId, Integer branchId, Long masseuseId, String customerName, String customerIdNumber,
                       String customerPhone, String customerEmail, LocalDate reservationDate, String reservationTime) {
        this.serviceId = serviceId;
        this.branchId = branchId;
        this.masseuseId = masseuseId;
        this.customerName = customerName;
        this.customerIdNumber = customerIdNumber;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.reservationDate = reservationDate;
        this.reservationTime = reservationTime;
    }

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public Long getServiceId() { return serviceId; }
    public Integer getBranchId() { return branchId; }
    public Long getMasseuseId() { return masseuseId; }
    public String getCustomerIdNumber() { return customerIdNumber; }
    public String getCustomerPhone() { return customerPhone; }
    public String getCustomerEmail() { return customerEmail; }
    public LocalDate getReservationDate() { return reservationDate; }
    public String getReservationTime() { return reservationTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}