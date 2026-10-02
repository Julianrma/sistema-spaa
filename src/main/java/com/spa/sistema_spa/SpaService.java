package com.spa.sistema_spa;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "spa_services")
public class SpaService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String category;
    @Column(columnDefinition = "TEXT")
    private String description;
    private Integer durationMinutes;
    private BigDecimal price;
    private String imageUrl;
    private boolean active = true;
    private boolean featured = false;

    protected SpaService() {
    }

    public SpaService(String name, String category, String description, int durationMinutes, BigDecimal price, String imageUrl) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return active; }
    public boolean isFeatured() { return featured; }

    public void update(String name, String category, String description, Integer durationMinutes,
                       BigDecimal price, String imageUrl) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    public void setActive(boolean active) { this.active = active; }
    public void setFeatured(boolean featured) { this.featured = featured; }
}