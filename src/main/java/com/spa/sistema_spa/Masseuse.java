package com.spa.sistema_spa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "masseuses")
public class Masseuse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String specialty;
    private String phone;
    private String email;
    private boolean active = true;

    protected Masseuse() {
    }

    public Masseuse(String name, String specialty, String phone, String email) {
        this.name = name;
        this.specialty = specialty;
        this.phone = phone;
        this.email = email;
    }

    public void update(String name, String specialty, String phone, String email) {
        this.name = name;
        this.specialty = specialty;
        this.phone = phone;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
