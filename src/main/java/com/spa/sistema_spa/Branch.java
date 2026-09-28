package com.spa.sistema_spa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String sector;
    private String address;
    private String openingHours;
    private Double latitude;
    private Double longitude;
    private boolean active = true;

    protected Branch() {
    }

    public Branch(String name, String sector, String address, String openingHours, double latitude, double longitude) {
        this.name = name;
        this.sector = sector;
        this.address = address;
        this.openingHours = openingHours;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void update(String name, String sector, String address, String openingHours, Double latitude, Double longitude) {
        this.name = name;
        this.sector = sector;
        this.address = address;
        this.openingHours = openingHours;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setActive(boolean active) { this.active = active; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSector() { return sector; }
    public String getAddress() { return address; }
    public String getOpeningHours() { return openingHours; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public boolean isActive() { return active; }
}