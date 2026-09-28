package com.spa.sistema_spa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerName;
    private Integer branchId;
    private Integer rating;
    private String comment;
    private String status = "PENDIENTE";

    protected Review() {
    }

    public Review(String customerName, Integer branchId, Integer rating, String comment) {
        this.customerName = customerName;
        this.branchId = branchId;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public Integer getBranchId() { return branchId; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public String getStatus() { return status; }
    public void approve() { this.status = "APROBADA"; }
}